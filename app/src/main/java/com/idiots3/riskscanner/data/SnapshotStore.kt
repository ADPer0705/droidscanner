package com.idiots3.riskscanner.data

import android.content.Context
import com.idiots3.riskscanner.engine.SnapshotEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the last scan snapshot (sensitive granted permissions, enabled special access,
 * and signing certificate SHA-256) so the next scan or background check can compute an
 * accurate diff. Stored locally; never transmitted.
 */
class SnapshotStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun load(): Map<String, SnapshotEntry> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return@withContext emptyMap()
        try {
            val root = JSONObject(raw)
            val result = HashMap<String, SnapshotEntry>(root.length())
            val keys = root.keys()
            while (keys.hasNext()) {
                val packageName = keys.next()
                val entry = root.getJSONObject(packageName)
                val perms = readSet(entry.optJSONArray("p"))
                val special = readSet(entry.optJSONArray("sa"))
                result[packageName] = SnapshotEntry(
                    versionCode = entry.optLong("v"),
                    score = entry.optInt("s"),
                    level = entry.optString("l"),
                    sensitiveGranted = perms,
                    enabledSpecialAccess = special,
                    signingCertSha256 = entry.optString("c", "").takeIf { it.isNotBlank() },
                    isSystemApp = entry.optBoolean("sys", false)
                )
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    suspend fun save(entries: Map<String, SnapshotEntry>) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            entries.forEach { (packageName, entry) ->
                val json = JSONObject()
                json.put("v", entry.versionCode)
                json.put("s", entry.score)
                json.put("l", entry.level)
                json.put("p", JSONArray(entry.sensitiveGranted.sorted()))
                if (entry.enabledSpecialAccess.isNotEmpty()) {
                    json.put("sa", JSONArray(entry.enabledSpecialAccess.sorted()))
                }
                entry.signingCertSha256?.let { json.put("c", it) }
                if (entry.isSystemApp) json.put("sys", true)
                root.put(packageName, json)
            }
            prefs.edit().putString(KEY_ENTRIES, root.toString()).apply()
        } catch (_: Throwable) {
        }
    }

    fun lastScanTime(): Long = prefs.getLong(KEY_LAST_SCAN, 0L)

    fun setLastScanTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_SCAN, time).apply()
    }

    private fun readSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "risk_snapshot"
        const val KEY_ENTRIES = "entries"
        const val KEY_LAST_SCAN = "last_scan"
    }
}
