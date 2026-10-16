package com.idiots3.riskscanner.data

import android.content.Context
import com.idiots3.riskscanner.engine.SnapshotEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Persists the last scan snapshot (only sensitive, granted permissions) so the next
 * scan can show a permission diff. Stored locally; never transmitted.
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
                val perms = entry.optJSONArray("p")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }.toSet()
                }.orEmpty()
                result[packageName] = SnapshotEntry(
                    versionCode = entry.optLong("v"),
                    score = entry.optInt("s"),
                    level = entry.optString("l"),
                    sensitiveGranted = perms
                )
            }
            result
        } catch (t: Throwable) {
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
                json.put("p", org.json.JSONArray(entry.sensitiveGranted.sorted()))
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

    private companion object {
        const val PREFS_NAME = "risk_snapshot"
        const val KEY_ENTRIES = "entries"
        const val KEY_LAST_SCAN = "last_scan"
    }
}
