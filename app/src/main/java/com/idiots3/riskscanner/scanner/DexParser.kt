package com.idiots3.riskscanner.scanner

import com.idiots3.riskscanner.model.DetectedTracker
import com.idiots3.riskscanner.model.HighRiskApiHit
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class TrackerSignature(
    val name: String,
    val category: String,
    val prefix: String
)

data class DexScanCatalog(
    val trackers: List<TrackerSignature> = emptyList(),
    val financeTargets: Set<String> = emptySet()
)

data class DexSingleFileResult(
    val trackers: Set<DetectedTracker> = emptySet(),
    val highRiskApis: Set<HighRiskApiHit> = emptySet(),
    val financeTargets: Set<String> = emptySet(),
    val cleartextHttpCount: Int = 0,
    val rawIpEndpoints: Set<String> = emptySet(),
    val parseAnomaly: Boolean = false
)

/**
 * Pure-JVM, zero-full-allocation streaming mini-DEX parser.
 *
 * Reads DEX header offsets, type descriptors (for ad/analytics tracker attribution),
 * method references (for sensitive execution/SMS/wipe/overlay APIs), and filtered
 * string literals (for banking target lists and cleartext/IP endpoints) directly from
 * a read-only [ByteBuffer] or memory-mapped file.
 *
 * Any malformed or truncated offset is handled safely and reported as [DexSingleFileResult.parseAnomaly]
 * rather than crashing.
 */
object DexParser {

    private const val HEADER_MIN_BYTES = 0x70
    private const val MAX_STRING_BYTES = 320
    private const val MAX_STRINGS_SCANNED = 120_000
    private const val MAX_TYPES_SCANNED = 65_536
    private const val MAX_METHODS_SCANNED = 120_000
    private const val MAX_RAW_IPS = 6

    private val IGNORED_HTTP_PREFIXES = listOf(
        "http://schemas.",
        "http://www.w3.org",
        "http://ns.adobe.com",
        "http://xml.org",
        "http://xmlpull.org",
        "http://apache.org",
        "http://www.apache.org",
        "http://localhost",
        "http://127.0.0.1",
        "http://example.com",
        "http://www.example.com",
        "http://purl.org"
    )

    private val RAW_IP_URL_REGEX = Regex("""https?://(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})(?::\d+)?(?:/[^\s"']*)?""")

    private data class WatchedApi(
        val id: String,
        val classDescriptor: String,
        val methodNames: Set<String>, // empty means any method on that class
        val name: String,
        val category: String,
        val plainExplanation: String
    )

    private val WATCHED_APIS = listOf(
        WatchedApi(
            id = "dynamic_dex_load",
            classDescriptor = "Ldalvik/system/DexClassLoader;",
            methodNames = emptySet(),
            name = "Dynamic code loading (DexClassLoader)",
            category = "Code Execution",
            plainExplanation = "Can load and run extra executable code at runtime outside the installed APK."
        ),
        WatchedApi(
            id = "in_memory_dex_load",
            classDescriptor = "Ldalvik/system/InMemoryDexClassLoader;",
            methodNames = emptySet(),
            name = "In-memory code loading",
            category = "Code Execution",
            plainExplanation = "Can decrypt and execute hidden code directly in memory."
        ),
        WatchedApi(
            id = "runtime_exec",
            classDescriptor = "Ljava/lang/Runtime;",
            methodNames = setOf("exec"),
            name = "Shell command execution (Runtime.exec)",
            category = "Code Execution",
            plainExplanation = "Can run low-level system shell commands."
        ),
        WatchedApi(
            id = "process_builder",
            classDescriptor = "Ljava/lang/ProcessBuilder;",
            methodNames = setOf("start"),
            name = "Subprocess execution (ProcessBuilder)",
            category = "Code Execution",
            plainExplanation = "Can spawn background native shell commands."
        ),
        WatchedApi(
            id = "sms_send",
            classDescriptor = "Landroid/telephony/SmsManager;",
            methodNames = setOf("sendTextMessage", "sendMultipartTextMessage", "sendDataMessage"),
            name = "Programmatic SMS sending",
            category = "SMS & Billing",
            plainExplanation = "Contains code capable of sending SMS messages in the background."
        ),
        WatchedApi(
            id = "device_wipe_lock",
            classDescriptor = "Landroid/app/admin/DevicePolicyManager;",
            methodNames = setOf("wipeData", "lockNow", "resetPassword", "resetPasswordWithToken"),
            name = "Device lock or wipe API",
            category = "Device Control",
            plainExplanation = "References Device Administrator lock or factory-wipe APIs."
        ),
        WatchedApi(
            id = "media_projection",
            classDescriptor = "Landroid/media/projection/MediaProjectionManager;",
            methodNames = setOf("createScreenCaptureIntent"),
            name = "Screen capture (MediaProjection)",
            category = "Screen Capture",
            plainExplanation = "Contains code that can request recording of your screen."
        ),
        WatchedApi(
            id = "a11y_automation",
            classDescriptor = "Landroid/accessibilityservice/AccessibilityService;",
            methodNames = setOf("performGlobalAction", "dispatchGesture"),
            name = "Automated UI interaction",
            category = "Accessibility",
            plainExplanation = "Contains code that can tap, swipe, or press navigation buttons programmatically."
        ),
        WatchedApi(
            id = "package_installer",
            classDescriptor = "Landroid/content/pm/PackageInstaller;",
            methodNames = setOf("createSession", "openSession"),
            name = "Programmatic APK installer",
            category = "Dropper Capability",
            plainExplanation = "Contains code to stage and launch installation of additional APKs."
        ),
        WatchedApi(
            id = "webview_js_bridge",
            classDescriptor = "Landroid/webkit/WebView;",
            methodNames = setOf("addJavascriptInterface"),
            name = "WebView JavaScript bridge",
            category = "Web Bridge",
            plainExplanation = "Exposes native app functions to web pages rendered inside the app."
        )
    )

    private val WATCHED_BY_CLASS: Map<String, List<WatchedApi>> = WATCHED_APIS.groupBy { it.classDescriptor }

    fun scanBuffer(rawBuffer: ByteBuffer, catalog: DexScanCatalog): DexSingleFileResult {
        val buf = rawBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val limit = buf.limit()
        if (limit < HEADER_MIN_BYTES) {
            return DexSingleFileResult(parseAnomaly = true)
        }

        // Validate DEX magic: "dex\n0XX\0"
        if (buf.get(0) != 0x64.toByte() ||
            buf.get(1) != 0x65.toByte() ||
            buf.get(2) != 0x78.toByte() ||
            buf.get(3) != 0x0A.toByte() ||
            buf.get(7) != 0x00.toByte()
        ) {
            return DexSingleFileResult(parseAnomaly = true)
        }

        val strCount = buf.getInt(0x38)
        val strOff = buf.getInt(0x3C)
        val typeCount = buf.getInt(0x40)
        val typeOff = buf.getInt(0x44)
        val methodCount = buf.getInt(0x58)
        val methodOff = buf.getInt(0x5C)

        if (strCount < 0 || typeCount < 0 || methodCount < 0 ||
            !tableInBounds(strOff, strCount, 4, limit) ||
            !tableInBounds(typeOff, typeCount, 4, limit) ||
            !tableInBounds(methodOff, methodCount, 8, limit)
        ) {
            return DexSingleFileResult(parseAnomaly = true)
        }

        val trackersFound = linkedSetOf<DetectedTracker>()
        val apisFound = linkedSetOf<HighRiskApiHit>()
        val financeMatches = linkedSetOf<String>()
        val rawIps = linkedSetOf<String>()
        var cleartextHttpCount = 0
        var anomaly = false

        // 1. Scan type descriptors for embedded tracker SDKs and index watched API class IDs.
        val watchedClassTypeIds = HashMap<Int, List<WatchedApi>>()
        val typesLimit = minOf(typeCount, MAX_TYPES_SCANNED)
        for (i in 0 until typesLimit) {
            val strIdx = buf.getInt(typeOff + i * 4)
            if (strIdx !in 0 until strCount) {
                anomaly = true
                continue
            }
            val desc = readString(buf, strOff, strIdx, limit) ?: continue
            if (!desc.startsWith('L')) continue

            WATCHED_BY_CLASS[desc]?.let { watchedClassTypeIds[i] = it }

            for (sig in catalog.trackers) {
                if (desc.startsWith(sig.prefix)) {
                    trackersFound += DetectedTracker(sig.name, sig.category)
                    break
                }
            }
        }

        // 2. Scan method_ids ONLY where class_idx matches a watched framework class (very fast).
        if (watchedClassTypeIds.isNotEmpty()) {
            val methodsLimit = minOf(methodCount, MAX_METHODS_SCANNED)
            for (i in 0 until methodsLimit) {
                val entryOff = methodOff + i * 8
                val classIdx = buf.getShort(entryOff).toInt() and 0xFFFF
                val candidates = watchedClassTypeIds[classIdx] ?: continue
                val nameStrIdx = buf.getInt(entryOff + 4)
                if (nameStrIdx !in 0 until strCount) continue
                val methodName = readString(buf, strOff, nameStrIdx, limit) ?: continue
                for (api in candidates) {
                    if (api.methodNames.isEmpty() || api.methodNames.contains(methodName)) {
                        apisFound += HighRiskApiHit(
                            id = api.id,
                            name = api.name,
                            category = api.category,
                            plainExplanation = api.plainExplanation
                        )
                    }
                }
            }
        }

        // 3. Single streaming pass over string_ids for finance target lists & cleartext/IP URLs.
        val stringsLimit = minOf(strCount, MAX_STRINGS_SCANNED)
        for (i in 0 until stringsLimit) {
            val s = readString(buf, strOff, i, limit) ?: continue
            if (s.length < 7 || s.length > MAX_STRING_BYTES) continue

            if (catalog.financeTargets.contains(s)) {
                financeMatches += s
            }

            if (s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)) {
                val lower = s.lowercase()
                if (lower.startsWith("http://") && IGNORED_HTTP_PREFIXES.none { lower.startsWith(it) }) {
                    cleartextHttpCount++
                }
                if (rawIps.size < MAX_RAW_IPS) {
                    val m = RAW_IP_URL_REGEX.find(s)
                    if (m != null) {
                        val ip = m.groupValues[1]
                        if (isPublicIpv4(ip)) {
                            rawIps += ip
                        }
                    }
                }
            }
        }

        return DexSingleFileResult(
            trackers = trackersFound,
            highRiskApis = apisFound,
            financeTargets = financeMatches,
            cleartextHttpCount = cleartextHttpCount,
            rawIpEndpoints = rawIps,
            parseAnomaly = anomaly
        )
    }

    private fun tableInBounds(offset: Int, count: Int, entrySize: Int, limit: Int): Boolean {
        if (count == 0) return true
        if (offset < HEADER_MIN_BYTES) return false
        val end = offset.toLong() + count.toLong() * entrySize.toLong()
        return end <= limit.toLong()
    }

    private fun readString(buf: ByteBuffer, strOff: Int, index: Int, limit: Int): String? {
        val dataOff = buf.getInt(strOff + index * 4)
        if (dataOff < HEADER_MIN_BYTES || dataOff >= limit) return null
        var pos = dataOff
        // Skip ULEB128 utf16_size (at most 5 bytes)
        var ulebBytes = 0
        while (pos < limit && ulebBytes < 5) {
            val b = buf.get(pos).toInt() and 0xFF
            pos++
            ulebBytes++
            if ((b and 0x80) == 0) break
        }
        val start = pos
        val maxEnd = minOf(limit, start + MAX_STRING_BYTES)
        while (pos < maxEnd && buf.get(pos) != 0.toByte()) {
            pos++
        }
        val len = pos - start
        if (len <= 0) return ""
        val bytes = ByteArray(len)
        for (k in 0 until len) {
            bytes[k] = buf.get(start + k)
        }
        return String(bytes, Charsets.UTF_8)
    }

    fun isPublicIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val a = octets[0]
        val b = octets[1]
        return when {
            a == 0 || a == 10 || a == 127 -> false
            a == 192 && b == 168 -> false
            a == 172 && b in 16..31 -> false
            a == 169 && b == 254 -> false
            a >= 224 -> false
            else -> true
        }
    }
}
