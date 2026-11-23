package com.idiots3.riskscanner.report

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.idiots3.riskscanner.engine.ScoringConfig
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.DevicePosture
import com.idiots3.riskscanner.model.DeviceSummary
import com.idiots3.riskscanner.model.RecommendationSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates the full-device scan report as a PDF **on-device** and writes it to the app's
 * cache directory. Sharing is explicit and user-driven through the system share sheet.
 */
object PdfExporter {

    private const val PAGE_WIDTH = 595 // A4 at 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE = 14.5f

    suspend fun export(
        context: Context,
        summary: DeviceSummary,
        apps: List<AppInfo>,
        posture: DevicePosture? = null,
        recommendations: RecommendationSummary? = null
    ): File? = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val file = File(dir, "droidscanner-report-$stamp.pdf")

            val body = Paint().apply { color = Color.BLACK; textSize = 9.5f; isAntiAlias = true }
            val title = Paint(body).apply { textSize = 17f; isFakeBoldText = true }
            val heading = Paint(body).apply { textSize = 12f; isFakeBoldText = true }
            val faint = Paint(body).apply { color = Color.parseColor("#555555"); textSize = 8.5f }

            var pageNumber = 1
            var page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            var canvas: Canvas = page.canvas
            var y = MARGIN + 10f

            fun newPage() {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                canvas = page.canvas
                y = MARGIN + 10f
            }

            fun drawWrapped(text: String, paint: Paint = body, indent: Float = 0f) {
                val maxWidth = (PAGE_WIDTH - MARGIN * 2f - indent).coerceAtLeast(100f)
                var remaining = text.replace('\n', ' ').trim()
                if (remaining.isEmpty()) {
                    if (y + LINE > PAGE_HEIGHT - MARGIN) newPage()
                    y += LINE
                    return
                }
                while (remaining.isNotEmpty()) {
                    val measured = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                    val slice = if (measured >= remaining.length) {
                        remaining
                    } else {
                        val lastSpace = remaining.lastIndexOf(' ', measured)
                        if (lastSpace > 0) remaining.substring(0, lastSpace) else remaining.substring(0, measured)
                    }
                    if (y + LINE > PAGE_HEIGHT - MARGIN) newPage()
                    canvas.drawText(slice, MARGIN + indent, y, paint)
                    y += LINE
                    remaining = remaining.substring(slice.length).trimStart()
                }
            }

            fun gap(amount: Float = 6f) {
                y += amount
            }

            drawWrapped("DroidScanner — On-Device Security Report", title)
            gap(2f)
            drawWrapped(
                "Generated ${SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date())} · " +
                    "100% on-device analysis · scoring engine v${ScoringConfig.VERSION}",
                faint
            )
            gap(10f)

            drawWrapped("Device summary", heading)
            if (recommendations != null) {
                val parts = recommendations.scoreBreakdown.entries.joinToString(" · ") { "${it.key} ${it.value}/100" }
                drawWrapped("Overall Device Security Score: ${recommendations.deviceScore}/100 ($parts)")
            }
            drawWrapped("Apps audited: ${summary.totalApps}    High or Critical: ${summary.highOrCritical} (Critical ${summary.criticalApps}, High ${summary.highApps})")
            drawWrapped("Medium: ${summary.mediumApps}    Low: ${summary.lowApps}    Sideloaded apps: ${summary.sideloadedApps}    Trusted: ${summary.trustedApps}")
            drawWrapped("Apps with verified special access: ${summary.specialAccessApps}    Impersonation alerts: ${summary.impersonationAlerts}")
            drawWrapped("Sensitive permissions granted in total: ${summary.totalPermissionsGranted}")
            drawWrapped("On-device purpose check: ${summary.aiAnalyzedApps} apps analysed, ${summary.aiUnexplainedApps} with unexplained access")
            gap(4f)
            drawWrapped("Bands: Low 0-25 · Medium 26-50 · High 51-75 · Critical 76-100", faint)
            gap(10f)

            if (posture != null) {
                drawWrapped("Device protection posture (${posture.protectionScore}/100)", heading)
                posture.checks.forEach { check ->
                    drawWrapped("• [${check.status.label}] ${check.title}: ${check.detail}", indent = 6f)
                }
                gap(10f)
            }

            if (recommendations != null && recommendations.recommendations.isNotEmpty()) {
                drawWrapped("Top priority recommendations", heading)
                recommendations.recommendations.take(8).forEachIndexed { idx, rec ->
                    drawWrapped("${idx + 1}. [${rec.priority.label}] ${rec.title} — ${rec.description}", indent = 6f)
                }
                gap(10f)
            }

            drawWrapped("Apps to review first", heading)
            gap(2f)

            val ranked = apps.sortedByDescending { it.riskScore }.take(25)
            if (ranked.isEmpty()) {
                drawWrapped("No applications were scanned yet.")
            }
            ranked.forEachIndexed { index, app ->
                gap(4f)
                drawWrapped(
                    "${index + 1}. ${app.appName} — ${app.riskScore}/100 (${app.riskLevel.label})" +
                        if (app.isSideloaded) " · sideloaded" else "",
                    heading
                )
                drawWrapped(
                    "${app.packageName} · v${app.versionName} · ${app.installerSource} · " +
                        "${app.grantedSensitive.size} sensitive permissions granted · " +
                        "${app.specialAccess.enabledLabels.size} active special access",
                    faint,
                    indent = 10f
                )
                app.riskReasons.take(3).forEach { reason ->
                    gap(1f)
                    drawWrapped("• ${reason.title}: ${reason.description}", body, indent = 10f)
                }
            }

            gap(12f)
            drawWrapped("What to do now", heading)
            drawWrapped("1. Open an app's detail screen and use \"App Settings\" to revoke permissions you do not expect.")
            drawWrapped("2. Critical apps that you do not recognise should be uninstalled from the detail screen.")
            drawWrapped("3. Check Device & Access > Special Access Hub for active Accessibility, Notification access and Device Admin holders.")

            gap(10f)
            drawWrapped("Generated entirely on this device by DroidScanner. No scan data was transmitted anywhere.", faint)

            document.finishPage(page)
            FileOutputStream(file).use { output -> document.writeTo(output) }
            file
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { document.close() }
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
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share security report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Throwable) {
        }
    }
}
