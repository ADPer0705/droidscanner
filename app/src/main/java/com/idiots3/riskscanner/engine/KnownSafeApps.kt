package com.idiots3.riskscanner.engine

import android.content.Context
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.InstallProvenance
import org.json.JSONArray
import org.json.JSONObject

data class KnownSafeEntry(
    val packageName: String,
    val displayName: String,
    val role: String,
    val expectedSpecialAccess: Set<String>,
    val allowOfficialStore: Boolean = true,
    val allowAlternativeStore: Boolean = false,
    val pinnedCertSha256: String? = null
)

/**
 * Verifies well-known legitimate high-privilege apps (screen readers, password managers,
 * smartwatch companions, open-source stores) using package name, trusted store provenance,
 * clean production signing, and optional certificate pinning.
 *
 * Sideloaded, debug-signed, debuggable, or weak-key builds NEVER qualify as verified safe.
 */
object KnownSafeApps {

    private const val ASSET_NAME = "known_safe_apps.json"

    @Volatile
    private var cached: Map<String, KnownSafeEntry>? = null

    fun load(context: Context): Map<String, KnownSafeEntry> {
        cached?.let { return it }
        val parsed = runCatching {
            val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            parseJson(raw)
        }.getOrDefault(emptyMap())
        cached = parsed
        return parsed
    }

    fun parseJson(json: String): Map<String, KnownSafeEntry> {
        val root = JSONObject(json)
        val array = root.optJSONArray("apps") ?: JSONArray()
        val result = linkedMapOf<String, KnownSafeEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val pkg = obj.optString("packageName").trim()
            if (pkg.isBlank()) continue
            val expectedArr = obj.optJSONArray("expectedSpecialAccess") ?: JSONArray()
            val expected = buildSet {
                for (j in 0 until expectedArr.length()) {
                    val v = expectedArr.optString(j).trim()
                    if (v.isNotBlank()) add(v)
                }
            }
            result[pkg] = KnownSafeEntry(
                packageName = pkg,
                displayName = obj.optString("displayName", pkg),
                role = obj.optString("role", "Verified tool"),
                expectedSpecialAccess = expected,
                allowOfficialStore = obj.optBoolean("allowOfficialStore", true),
                allowAlternativeStore = obj.optBoolean("allowAlternativeStore", false),
                pinnedCertSha256 = obj.optString("pinnedCertSha256", "").takeIf { it.isNotBlank() }
            )
        }
        return result
    }

    fun match(app: AppInfo, catalog: Map<String, KnownSafeEntry>): KnownSafeEntry? {
        val entry = catalog[app.packageName] ?: return null
        // Tampered, debug-signed, debuggable, or weak-key builds never qualify.
        if (app.isDebugSigned || app.isDebuggable || app.isWeakSigningKey) return null

        if (!entry.pinnedCertSha256.isNullOrBlank()) {
            if (!entry.pinnedCertSha256.equals(app.signingCertSha256, ignoreCase = true)) {
                return null
            }
        }

        val provenanceTrusted = when (app.installProvenance) {
            InstallProvenance.PREINSTALLED_SYSTEM -> true
            InstallProvenance.OFFICIAL_STORE -> entry.allowOfficialStore
            InstallProvenance.ALTERNATIVE_STORE -> entry.allowAlternativeStore
            InstallProvenance.DEVICE_MIGRATION -> entry.allowOfficialStore
            InstallProvenance.BROWSER_OR_APP_SIDELOAD,
            InstallProvenance.PACKAGE_INSTALLER,
            InstallProvenance.ADB_OR_SHELL,
            InstallProvenance.UNKNOWN -> false
        }
        return if (provenanceTrusted) entry else null
    }
}
