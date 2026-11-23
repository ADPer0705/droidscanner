package com.idiots3.riskscanner.scanner

import android.app.ActivityManager
import android.content.Context
import com.idiots3.riskscanner.model.DetectedTracker
import com.idiots3.riskscanner.model.DexAnalysisResult
import com.idiots3.riskscanner.model.HighRiskApiHit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.zip.ZipFile

/**
 * Streams `classes*.dex` entries from an APK through a temporary mapped file in [Context.getCacheDir]
 * so 2 GB consumer phones can inspect trackers, sensitive APIs, and finance target lists without
 * loading whole APKs or DEX arrays into Java heap.
 */
class DexInspector(private val context: Context) {

    fun inspectApk(apkFile: File): DexAnalysisResult {
        if (!apkFile.exists() || !apkFile.isFile || !apkFile.canRead()) {
            return DexAnalysisResult()
        }
        val lowRam = isLowRamDevice()
        val maxDexFiles = if (lowRam) 4 else 8
        val maxDexBytes = if (lowRam) 12L * 1024L * 1024L else 22L * 1024L * 1024L

        val catalog = loadCatalog(context)
        val trackers = linkedSetOf<DetectedTracker>()
        val apis = linkedSetOf<HighRiskApiHit>()
        val financeMatches = linkedSetOf<String>()
        val rawIps = linkedSetOf<String>()
        var cleartextCount = 0
        var parseAnomaly = false
        var scannedDexCount = 0
        var skippedLarge = 0

        try {
            ZipFile(apkFile).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { entry ->
                        !entry.isDirectory &&
                            !entry.name.contains('/') &&
                            entry.name.startsWith("classes") &&
                            entry.name.endsWith(".dex")
                    }
                    .sortedBy { it.name }
                    .toList()

                for (entry in dexEntries) {
                    if (scannedDexCount >= maxDexFiles) break
                    if (entry.size > maxDexBytes) {
                        skippedLarge++
                        continue
                    }

                    val tmp = File(context.cacheDir, "dex_scan_${System.nanoTime()}.dex")
                    try {
                        var copied = 0L
                        var exceeded = false
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(tmp).use { out ->
                                val buf = ByteArray(32 * 1024)
                                var n = input.read(buf)
                                while (n > 0) {
                                    copied += n
                                    if (copied > maxDexBytes) {
                                        exceeded = true
                                        break
                                    }
                                    out.write(buf, 0, n)
                                    n = input.read(buf)
                                }
                            }
                        }
                        if (exceeded || copied == 0L) {
                            if (exceeded) skippedLarge++
                            continue
                        }

                        RandomAccessFile(tmp, "r").use { raf ->
                            val channel = raf.channel
                            val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, copied)
                            val single = DexParser.scanBuffer(mapped, catalog)
                            trackers += single.trackers
                            apis += single.highRiskApis
                            financeMatches += single.financeTargets
                            rawIps += single.rawIpEndpoints
                            cleartextCount += single.cleartextHttpCount
                            if (single.parseAnomaly) parseAnomaly = true
                        }
                        scannedDexCount++
                    } catch (_: Throwable) {
                        parseAnomaly = true
                    } finally {
                        runCatching { tmp.delete() }
                    }
                }
            }
        } catch (_: Throwable) {
            parseAnomaly = true
        }

        return DexAnalysisResult(
            dexFileCount = scannedDexCount,
            detectedTrackers = trackers.sortedWith(compareBy({ it.category }, { it.name })),
            highRiskApis = apis.sortedBy { it.category },
            financeTargetMatches = financeMatches.sorted().take(20),
            cleartextHttpCount = cleartextCount,
            rawIpEndpoints = rawIps.take(6),
            parseAnomaly = parseAnomaly,
            skippedLargeDexCount = skippedLarge
        )
    }

    private fun isLowRamDevice(): Boolean = runCatching {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }.getOrDefault(false)

    companion object {
        private const val TRACKERS_ASSET = "tracker_signatures.json"
        private const val FINANCE_ASSET = "finance_targets.json"

        @Volatile
        private var cachedCatalog: DexScanCatalog? = null

        @Volatile
        private var cachedPackers: Map<String, String>? = null

        fun loadCatalog(context: Context): DexScanCatalog {
            cachedCatalog?.let { return it }
            val trackers = loadTrackersAndPackers(context).first
            val finance = runCatching {
                val raw = context.assets.open(FINANCE_ASSET).bufferedReader().use { it.readText() }
                val arr = JSONObject(raw).optJSONArray("packages") ?: JSONArray()
                buildSet {
                    for (i in 0 until arr.length()) {
                        val pkg = arr.optString(i).trim()
                        if (pkg.isNotBlank()) add(pkg)
                    }
                }
            }.getOrDefault(emptySet())
            val catalog = DexScanCatalog(trackers = trackers, financeTargets = finance)
            cachedCatalog = catalog
            return catalog
        }

        fun loadPackerMap(context: Context): Map<String, String> {
            cachedPackers?.let { return it }
            return loadTrackersAndPackers(context).second
        }

        private fun loadTrackersAndPackers(context: Context): Pair<List<TrackerSignature>, Map<String, String>> {
            return runCatching {
                val raw = context.assets.open(TRACKERS_ASSET).bufferedReader().use { it.readText() }
                val root = JSONObject(raw)
                val tArr = root.optJSONArray("trackers") ?: JSONArray()
                val trackers = buildList {
                    for (i in 0 until tArr.length()) {
                        val obj = tArr.optJSONObject(i) ?: continue
                        val name = obj.optString("name").trim()
                        val cat = obj.optString("category", "Analytics").trim()
                        val prefix = obj.optString("prefix").trim()
                        if (name.isNotBlank() && prefix.isNotBlank()) {
                            add(TrackerSignature(name, cat, prefix))
                        }
                    }
                }
                val pArr = root.optJSONArray("packers") ?: JSONArray()
                val packers = linkedMapOf<String, String>()
                for (i in 0 until pArr.length()) {
                    val obj = pArr.optJSONObject(i) ?: continue
                    val lib = obj.optString("lib").trim()
                    val name = obj.optString("name").trim()
                    if (lib.isNotBlank() && name.isNotBlank()) {
                        packers[lib] = name
                    }
                }
                cachedPackers = packers
                trackers to packers
            }.getOrDefault(emptyList<TrackerSignature>() to emptyMap())
        }
    }
}
