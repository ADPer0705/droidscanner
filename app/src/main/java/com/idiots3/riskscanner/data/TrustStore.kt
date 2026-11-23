package com.idiots3.riskscanner.data

import android.content.Context
import com.idiots3.riskscanner.model.DismissedPostureRecord
import com.idiots3.riskscanner.model.TrustState
import com.idiots3.riskscanner.model.TrustedAppRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists user-reviewed apps and acknowledged device-posture findings locally in internal storage.
 * Excluded from Android cloud backup & device transfer via backup_rules / data_extraction_rules.
 */
class TrustStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    suspend fun load(): TrustState = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext TrustState()
        try {
            val root = JSONObject(file.readText())
            val appsMap = linkedMapOf<String, TrustedAppRecord>()
            val appsObj = root.optJSONObject("apps")
            if (appsObj != null) {
                val keys = appsObj.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    val obj = appsObj.optJSONObject(pkg) ?: continue
                    appsMap[pkg] = TrustedAppRecord(
                        packageName = pkg,
                        appName = obj.optString("name", pkg),
                        certSha256 = obj.optString("cert", "").takeIf { it.isNotBlank() },
                        grantedSensitivePerms = readStringSet(obj.optJSONArray("perms")),
                        enabledSpecialAccess = readStringSet(obj.optJSONArray("special")),
                        trustedAt = obj.optLong("at", 0L),
                        note = obj.optString("note", "").takeIf { it.isNotBlank() }
                    )
                }
            }

            val postureMap = linkedMapOf<String, DismissedPostureRecord>()
            val postureObj = root.optJSONObject("posture")
            if (postureObj != null) {
                val keys = postureObj.keys()
                while (keys.hasNext()) {
                    val checkId = keys.next()
                    val obj = postureObj.optJSONObject(checkId) ?: continue
                    postureMap[checkId] = DismissedPostureRecord(
                        checkId = checkId,
                        title = obj.optString("title", checkId),
                        detailFingerprint = obj.optString("fp", ""),
                        dismissedAt = obj.optLong("at", 0L)
                    )
                }
            }
            TrustState(trustedApps = appsMap, dismissedPosture = postureMap)
        } catch (_: Throwable) {
            TrustState()
        }
    }

    suspend fun save(state: TrustState) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            val appsObj = JSONObject()
            state.trustedApps.forEach { (pkg, record) ->
                val obj = JSONObject()
                obj.put("name", record.appName)
                record.certSha256?.let { obj.put("cert", it) }
                obj.put("perms", JSONArray(record.grantedSensitivePerms.sorted()))
                obj.put("special", JSONArray(record.enabledSpecialAccess.sorted()))
                obj.put("at", record.trustedAt)
                record.note?.let { obj.put("note", it) }
                appsObj.put(pkg, obj)
            }
            root.put("apps", appsObj)

            val postureObj = JSONObject()
            state.dismissedPosture.forEach { (id, record) ->
                val obj = JSONObject()
                obj.put("title", record.title)
                obj.put("fp", record.detailFingerprint)
                obj.put("at", record.dismissedAt)
                postureObj.put(id, obj)
            }
            root.put("posture", postureObj)

            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        } catch (_: Throwable) {
        }
    }

    private fun readStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                val item = array.optString(i)
                if (item.isNotBlank()) add(item)
            }
        }
    }

    companion object {
        const val FILE_NAME = "trusted_items.json"
    }
}
