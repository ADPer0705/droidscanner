package com.idiots3.riskscanner.engine

import android.content.Context
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.InstallProvenance
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

data class ImpersonationTarget(
    val label: String,
    val aliases: List<String>,
    val officialPackages: Set<String>,
    val mustBeSystem: Boolean
)

data class ImpersonationHit(
    val targetLabel: String,
    val reason: String
)

/**
 * Pure-JVM detector for apps impersonating Android system components or popular
 * banking, messaging, and payment apps through spoofed labels, Unicode confusables,
 * or lookalike sideloaded package names.
 */
object ImpersonationDetector {

    private const val ASSET_NAME = "impersonation_targets.json"

    @Volatile
    private var cached: List<ImpersonationTarget>? = null

    fun load(context: Context): List<ImpersonationTarget> {
        cached?.let { return it }
        val parsed = runCatching {
            val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            parseJson(raw)
        }.getOrDefault(emptyList())
        cached = parsed
        return parsed
    }

    fun parseJson(json: String): List<ImpersonationTarget> {
        val root = JSONObject(json)
        val array = root.optJSONArray("targets") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val label = obj.optString("label").trim()
                if (label.isBlank()) continue
                val aliasesArr = obj.optJSONArray("aliases") ?: JSONArray()
                val aliases = (0 until aliasesArr.length())
                    .mapNotNull { aliasesArr.optString(it).trim().takeIf { s -> s.isNotBlank() } }
                val pkgsArr = obj.optJSONArray("officialPackages") ?: JSONArray()
                val pkgs = (0 until pkgsArr.length())
                    .mapNotNull { pkgsArr.optString(it).trim().takeIf { s -> s.isNotBlank() } }
                    .toSet()
                add(
                    ImpersonationTarget(
                        label = label,
                        aliases = aliases,
                        officialPackages = pkgs,
                        mustBeSystem = obj.optBoolean("mustBeSystem", false)
                    )
                )
            }
        }
    }

    fun detect(app: AppInfo, targets: List<ImpersonationTarget>): ImpersonationHit? {
        if (app.isSystemApp || app.isSelfApp) return null
        val normalizedLabel = normalizeLabel(app.appName)
        if (normalizedLabel.length < 4) return null

        val pkgLower = app.packageName.lowercase()

        for (target in targets) {
            // Skip if this is genuinely one of the official packages.
            if (target.officialPackages.any { official ->
                    pkgLower == official.lowercase() || pkgLower.startsWith("${official.lowercase()}.")
                }
            ) {
                continue
            }

            // 1. Exact label or alias impersonation (normalizing zero-width & Unicode confusables).
            val candidateNames = buildList {
                add(normalizeLabel(target.label))
                target.aliases.forEach { add(normalizeLabel(it)) }
            }
            if (candidateNames.any { it == normalizedLabel }) {
                // Non-system apps from official stores can occasionally share generic names like "Settings"
                // only rarely; require either mustBeSystem, sideloaded, or high-trust brand match.
                val suspicious = target.mustBeSystem ||
                    app.isSideloaded ||
                    app.installProvenance == InstallProvenance.UNKNOWN ||
                    target.label.length >= 6
                if (suspicious) {
                    val scope = if (target.mustBeSystem) "a system component" else "a popular app"
                    return ImpersonationHit(
                        targetLabel = target.label,
                        reason = "Uses the name \"${app.appName}\" matching $scope (${target.label}), " +
                            "but its package (${app.packageName}) is not official."
                    )
                }
            }

            // 2. Sideloaded package-name typosquatting (edit distance 1..2 against official package).
            if (app.isSideloaded) {
                for (official in target.officialPackages) {
                    val offLower = official.lowercase()
                    if (offLower.length >= 10 &&
                        kotlin.math.abs(pkgLower.length - offLower.length) <= 2
                    ) {
                        val dist = levenshtein(pkgLower, offLower, maxDist = 2)
                        if (dist in 1..2) {
                            return ImpersonationHit(
                                targetLabel = target.label,
                                reason = "Sideloaded package \"${app.packageName}\" closely mimics official package \"$official\"."
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Strips zero-width characters, diacritics, and common Cyrillic/Greek/digit homoglyphs
     * used by banking trojans to spoof trusted labels like "Gооgle Plаy Services".
     */
    fun normalizeLabel(raw: String): String {
        val stripped = raw
            .replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\uFEFF]"), "")
            .let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
            .lowercase()

        val sb = StringBuilder(stripped.length)
        for (ch in stripped) {
            val mapped = HOMOGLYPHS[ch] ?: ch
            if (mapped.isLetterOrDigit()) {
                sb.append(mapped)
            } else if (mapped.isWhitespace()) {
                if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
            }
        }
        return sb.toString().trim()
    }

    private fun levenshtein(a: String, b: String, maxDist: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > maxDist) return maxDist + 1
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                val v = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                curr[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > maxDist) return maxDist + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    private val HOMOGLYPHS: Map<Char, Char> = mapOf(
        // Cyrillic lookalikes
        '\u0430' to 'a', // а
        '\u0435' to 'e', // е
        '\u043E' to 'o', // о
        '\u0440' to 'p', // р
        '\u0441' to 'c', // с
        '\u0443' to 'y', // у
        '\u0445' to 'x', // х
        '\u0456' to 'i', // і
        '\u0455' to 's', // ѕ
        // Greek lookalikes
        '\u03B1' to 'a', // α
        '\u03BF' to 'o', // ο
        '\u03C1' to 'p', // ρ
        '\u03B5' to 'e', // ε
        // Common digit substitutions
        '0' to 'o',
        '1' to 'l'
    )
}
