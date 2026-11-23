package com.idiots3.riskscanner.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.idiots3.riskscanner.engine.ScoringConfig
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.DevicePosture
import com.idiots3.riskscanner.model.DeviceSummary
import com.idiots3.riskscanner.model.RecommendationSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a structured JSON security audit report locally for troubleshooting or archiving.
 * Nothing is transmitted unless the user explicitly shares the exported file.
 */
object JsonReportExporter {

    suspend fun export(
        context: Context,
        summary: DeviceSummary,
        apps: List<AppInfo>,
        posture: DevicePosture? = null,
        recommendations: RecommendationSummary? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val file = File(dir, "droidscanner-audit-$stamp.json")

            val root = JSONObject()
            root.put("generator", "DroidScanner")
            root.put("engineVersion", ScoringConfig.VERSION)
            root.put("generatedAt", System.currentTimeMillis())

            val summaryObj = JSONObject().apply {
                put("deviceScore", recommendations?.deviceScore)
                put("totalApps", summary.totalApps)
                put("criticalApps", summary.criticalApps)
                put("highApps", summary.highApps)
                put("mediumApps", summary.mediumApps)
                put("lowApps", summary.lowApps)
                put("sideloadedApps", summary.sideloadedApps)
                put("specialAccessApps", summary.specialAccessApps)
                put("trustedApps", summary.trustedApps)
                put("impersonationAlerts", summary.impersonationAlerts)
            }
            root.put("summary", summaryObj)

            if (posture != null) {
                val checksArr = JSONArray()
                posture.checks.forEach { check ->
                    checksArr.put(
                        JSONObject().apply {
                            put("id", check.id)
                            put("title", check.title)
                            put("status", check.status.name)
                            put("dismissed", check.isDismissed)
                            put("detail", check.detail)
                            put("recommendation", check.recommendation)
                            if (check.details.isNotEmpty()) put("details", JSONArray(check.details))
                        }
                    )
                }
                root.put(
                    "devicePosture",
                    JSONObject().apply {
                        put("score", posture.protectionScore)
                        put("checks", checksArr)
                    }
                )
            }

            val appsArr = JSONArray()
            apps.sortedByDescending { it.riskScore }.forEach { app ->
                appsArr.put(
                    JSONObject().apply {
                        put("appName", app.appName)
                        put("packageName", app.packageName)
                        put("versionName", app.versionName)
                        put("versionCode", app.versionCode)
                        put("installerSource", app.installerSource)
                        put("provenance", app.installProvenance.name)
                        put("targetSdk", app.targetSdk)
                        put("riskScore", app.riskScore)
                        put("riskLevel", app.riskLevel.label)
                        put("isSystemApp", app.isSystemApp)
                        put("isUserTrusted", app.isUserTrusted)
                        app.impersonationTarget?.let { put("impersonationTarget", it) }
                        app.signingCertSha256?.let { put("signingCertSha256", it) }
                        put("debuggable", app.isDebuggable)
                        put("debugSigned", app.isDebugSigned)
                        put("enabledSpecialAccess", JSONArray(app.specialAccess.enabledLabels))
                        put("grantedSensitivePermissions", JSONArray(app.grantedSensitive.map { it.shortName }))
                        put("reasons", JSONArray(app.riskReasons.map { "${it.title}: ${it.description}" }))
                    }
                )
            }
            root.put("apps", appsArr)

            file.writeText(root.toString(2))
            file
        } catch (_: Throwable) {
            null
        }
    }

    fun share(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share JSON audit").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Throwable) {
        }
    }
}
