package com.idiots3.riskscanner.data

import android.content.Context
import com.idiots3.riskscanner.model.ChangeType
import com.idiots3.riskscanner.model.DevicePosture
import com.idiots3.riskscanner.model.PermissionChange
import com.idiots3.riskscanner.model.PostureStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ScoreHistoryPoint(
    val timestamp: Long,
    val deviceScore: Int,
    val appScore: Int,
    val postureScore: Int
)

data class AuditHistory(
    val events: List<PermissionChange> = emptyList(),
    val scoreHistory: List<ScoreHistoryPoint> = emptyList(),
    val lastPostureStates: Map<String, String> = emptyMap()
)

/**
 * Rolling local security audit timeline & score trend history so changes do not vanish
 * after refreshing the app, and background scans can detect device-posture regressions.
 */
class AuditHistoryStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    suspend fun load(): AuditHistory = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext AuditHistory()
        try {
            val root = JSONObject(file.readText())
            val events = mutableListOf<PermissionChange>()
            val evArray = root.optJSONArray("events")
            if (evArray != null) {
                for (i in 0 until evArray.length()) {
                    val obj = evArray.optJSONObject(i) ?: continue
                    val typeName = obj.optString("type", "")
                    val type = ChangeType.entries.firstOrNull { it.name == typeName } ?: continue
                    events += PermissionChange(
                        packageName = obj.optString("pkg", ""),
                        appName = obj.optString("name", ""),
                        type = type,
                        addedPermissions = readStringList(obj.optJSONArray("added")),
                        removedPermissions = readStringList(obj.optJSONArray("removed")),
                        addedSpecialAccess = readStringList(obj.optJSONArray("special")),
                        detail = obj.optString("detail", "").takeIf { it.isNotBlank() },
                        oldScore = obj.optInt("oldScore", 0),
                        newScore = obj.optInt("newScore", 0),
                        timestamp = obj.optLong("ts", 0L)
                    )
                }
            }

            val scores = mutableListOf<ScoreHistoryPoint>()
            val scoreArray = root.optJSONArray("scores")
            if (scoreArray != null) {
                for (i in 0 until scoreArray.length()) {
                    val obj = scoreArray.optJSONObject(i) ?: continue
                    scores += ScoreHistoryPoint(
                        timestamp = obj.optLong("ts", 0L),
                        deviceScore = obj.optInt("device", 100),
                        appScore = obj.optInt("app", 100),
                        postureScore = obj.optInt("posture", 100)
                    )
                }
            }

            val postureStates = linkedMapOf<String, String>()
            val postureObj = root.optJSONObject("postureStates")
            if (postureObj != null) {
                val keys = postureObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    postureStates[key] = postureObj.optString(key, "")
                }
            }

            AuditHistory(events = events, scoreHistory = scores, lastPostureStates = postureStates)
        } catch (_: Throwable) {
            AuditHistory()
        }
    }

    suspend fun recordScan(
        newEvents: List<PermissionChange>,
        posture: DevicePosture?,
        deviceScore: Int?,
        appScore: Int?,
        postureScore: Int?,
        now: Long
    ): AuditHistory = withContext(Dispatchers.IO) {
        val current = load()

        // Merge new events ahead of existing timeline, deduplicating rapid identical refreshes.
        val mergedEvents = (newEvents + current.events)
            .distinctBy { "${it.type.name}:${it.packageName}:${it.timestamp / 60_000L}:${it.detail.orEmpty()}" }
            .sortedByDescending { it.timestamp }
            .take(MAX_EVENTS)

        val updatedScores = current.scoreHistory.toMutableList()
        if (deviceScore != null && appScore != null && postureScore != null) {
            val last = updatedScores.lastOrNull()
            val enoughTimePassed = last == null || (now - last.timestamp) >= MIN_SCORE_INTERVAL_MS
            val scoreChanged = last == null || last.deviceScore != deviceScore
            if (enoughTimePassed || scoreChanged) {
                updatedScores += ScoreHistoryPoint(now, deviceScore, appScore, postureScore)
            }
        }
        val trimmedScores = updatedScores.takeLast(MAX_SCORE_POINTS)

        val postureMap = posture?.checks?.associate { it.id to it.status.name } ?: current.lastPostureStates

        val updated = AuditHistory(
            events = mergedEvents,
            scoreHistory = trimmedScores,
            lastPostureStates = postureMap
        )
        save(updated)
        updated
    }

    suspend fun clearEvents() = withContext(Dispatchers.IO) {
        val current = load()
        save(current.copy(events = emptyList()))
    }

    private fun save(history: AuditHistory) {
        try {
            val root = JSONObject()
            val evArray = JSONArray()
            history.events.forEach { ev ->
                val obj = JSONObject()
                obj.put("pkg", ev.packageName)
                obj.put("name", ev.appName)
                obj.put("type", ev.type.name)
                if (ev.addedPermissions.isNotEmpty()) obj.put("added", JSONArray(ev.addedPermissions))
                if (ev.removedPermissions.isNotEmpty()) obj.put("removed", JSONArray(ev.removedPermissions))
                if (ev.addedSpecialAccess.isNotEmpty()) obj.put("special", JSONArray(ev.addedSpecialAccess))
                ev.detail?.let { obj.put("detail", it) }
                obj.put("oldScore", ev.oldScore)
                obj.put("newScore", ev.newScore)
                obj.put("ts", ev.timestamp)
                evArray.put(obj)
            }
            root.put("events", evArray)

            val scoreArray = JSONArray()
            history.scoreHistory.forEach { pt ->
                val obj = JSONObject()
                obj.put("ts", pt.timestamp)
                obj.put("device", pt.deviceScore)
                obj.put("app", pt.appScore)
                obj.put("posture", pt.postureScore)
                scoreArray.put(obj)
            }
            root.put("scores", scoreArray)

            val postureObj = JSONObject()
            history.lastPostureStates.forEach { (k, v) -> postureObj.put(k, v) }
            root.put("postureStates", postureObj)

            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        } catch (_: Throwable) {
        }
    }

    private fun readStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optString(i)
                if (item.isNotBlank()) add(item)
            }
        }
    }

    companion object {
        const val FILE_NAME = "audit_history.json"
        private const val MAX_EVENTS = 120
        private const val MAX_SCORE_POINTS = 30
        private const val MIN_SCORE_INTERVAL_MS = 6L * 3600L * 1000L

        /**
         * Pure-JVM helper detecting meaningful posture regressions (e.g., PROTECTED -> AT_RISK/REVIEW)
         * between two scans.
         */
        fun detectPostureRegressions(
            previousStates: Map<String, String>,
            currentPosture: DevicePosture,
            now: Long
        ): List<PermissionChange> {
            if (previousStates.isEmpty()) return emptyList()
            val regressions = mutableListOf<PermissionChange>()
            for (check in currentPosture.checks) {
                if (check.isDismissed) continue
                val prevName = previousStates[check.id] ?: continue
                val prev = PostureStatus.entries.firstOrNull { it.name == prevName } ?: continue
                val worsened = when (prev) {
                    PostureStatus.PROTECTED -> check.status == PostureStatus.AT_RISK || check.status == PostureStatus.REVIEW
                    PostureStatus.REVIEW -> check.status == PostureStatus.AT_RISK
                    else -> false
                }
                if (worsened) {
                    regressions += PermissionChange(
                        packageName = "device_posture:${check.id}",
                        appName = check.title,
                        type = ChangeType.POSTURE_REGRESSED,
                        detail = "${prev.label} → ${check.status.label}: ${check.detail}",
                        newScore = if (check.status == PostureStatus.AT_RISK) 80 else 55,
                        timestamp = now
                    )
                }
            }
            return regressions
        }
    }
}
