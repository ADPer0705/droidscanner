package com.idiots3.riskscanner.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.idiots3.riskscanner.model.AppInfo

/**
 * Live install/update monitoring while the app is open.
 *
 * Android 8+ forbids manifest-declared receivers for package change broadcasts, so this
 * receiver is registered at runtime. Background changes are caught on resume/scan via
 * the persisted snapshot diff.
 */
class InstallMonitorReceiver(
    private val onPackageChanged: (String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_REMOVED &&
            intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        ) {
            // Skip the intermediate removal broadcast emitted during an in-place app update.
            return
        }
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName.isNotBlank()) onPackageChanged(packageName)
    }

    companion object {
        fun register(context: Context, onChanged: (String) -> Unit): InstallMonitorReceiver {
            val receiver = InstallMonitorReceiver(onChanged)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                context, receiver, filter, ContextCompat.RECEIVER_EXPORTED
            )
            return receiver
        }
    }
}

/** Local notification for apps that score High or Critical on install or permission escalation. */
object InstallAlertNotifier {

    private const val CHANNEL_ID = "risk_alerts"

    fun notifyHighRisk(context: Context, app: AppInfo, detailPrefix: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Risk alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when an app is installed or updated and scores High or Critical risk"
        }
        manager.createNotificationChannel(channel)

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName) ?: return
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = buildString {
            if (!detailPrefix.isNullOrBlank()) {
                append(detailPrefix)
                append(" ")
            }
            append("${app.appName} scored ${app.riskScore}/100 (${app.riskLevel.label} risk). Tap to see why.")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("${app.riskLevel.label} risk app: ${app.appName}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(app.packageName.hashCode(), notification)
        } catch (_: Throwable) {
        }
    }
}
