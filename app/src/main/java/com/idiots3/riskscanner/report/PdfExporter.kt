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
import com.idiots3.riskscanner.model.DeviceSummary
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
    private const val MAX_CHARS = 118

    suspend fun export(
        context: Context,
        summary: DeviceSummary,
        apps: List<AppInfo>
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val file = File(dir, "riskscanner-report-$stamp.pdf")

            val document = PdfDocument()
            val body = Paint().apply { color = Color.BLACK; textSize = 9.5f; isAntiAlias = true }
            val title = Paint(body).apply { textSize = 17f; isFakeBoldText = true }
            val heading = Paint(body).apply { textSize = 12f; isFakeBoldText = true }
            val faint = Paint(body).apply { color = Color.parseColor("#666666"); textSize = 8.5f }

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

            fun draw(text: String, paint: Paint = body, indent: Float = 0f) {
                if (y + LINE > PAGE_HEIGHT - MARGIN) newPage()
                canvas.drawText(text.take(MAX_CHARS), MARGIN + indent, y, paint)
                y += LINE
            }

            fun gap(amount: Float = 6f) {
                y += amount
            }

            draw("RiskScanner — On-Device App Risk Report", title)
            gap(2f)
            draw(
                "Generated ${SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date())} · " +
                    "100% on-device analysis · scoring engine v${ScoringConfig.VERSION}",
                faint
            )
            gap(10f)

            draw("Device summary", heading)
            draw("Apps audited: ${summary.totalApps}    High or Critical: ${summary.highOrCritical} (Critical ${summary.criticalApps}, High ${summary.highApps})")
            draw("Medium: ${summary.mediumApps}    Low: ${summary.lowApps}    Sideloaded apps: ${summary.sideloadedApps}")
            draw("Apps with special access enabled: ${summary.specialAccessApps}    Apps with no launcher icon: ${summary.hiddenApps}")
            draw("Sensitive permissions granted in total: ${summary.totalPermissionsGranted}")
            draw("On-device purpose check: ${summary.aiAnalyzedApps} apps analysed, ${summary.aiUnexplainedApps} with unexplained access")
            gap(4f)
            draw("Bands: Low 0-25 · Medium 26-50 · High 51-75 · Critical 76-100", faint)
            gap(10f)

            draw("Apps to review first", heading)
            gap(2f)

            val ranked = apps.sortedByDescending { it.riskScore }.take(25)
            if (ranked.isEmpty()) {
                draw("No applications were scanned yet.")
            }
            ranked.forEachIndexed { index, app ->
                gap(4f)
                draw(
                    "${index + 1}. ${app.appName} — ${app.riskScore}/100 (${app.riskLevel.label})" +
                        if (app.isSideloaded) " · sideloaded" else "",
                    heading
                )
                draw(
                    "   ${app.packageName} · v${app.versionName} · ${app.installerSource} · " +
                        "${app.grantedSensitive.size} sensitive permissions granted · " +
                        "${app.specialAccess.enabledLabels.size} special access",
                    faint
                )
                app.riskReasons.take(3).forEach { reason ->
                    gap(1f)
                    draw("   • ${reason.title}: ${reason.description}", body, indent = 6f)
                }
            }

            gap(12f)
            draw("What to do now (Q3)", heading)
            draw("1. Open an app's detail screen and use \"App Settings\" to revoke permissions you do not expect.")
            draw("2. Critical apps that you do not recognise should be uninstalled from the detail screen.")
            draw("3. Check Settings > Accessibility, Notification access and Device admin for apps you did not enable.")

            gap(10f)
            draw("Generated entirely on this device by RiskScanner. No scan data was transmitted anywhere.", faint)

            document.finishPage(page)
            FileOutputStream(file).use { output -> document.writeTo(output) }
            document.close()
            file
        } catch (t: Throwable) {
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
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share risk report"))
        } catch (_: Throwable) {
        }
    }
}
