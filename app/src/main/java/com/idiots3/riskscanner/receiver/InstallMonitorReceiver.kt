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
import com.idiots3.riskscanner.MainActivity
import com.idiots3.riskscanner.model.AppInfo

/**
 * Live install/update monitoring while the app is open.
 *
 * Android 8+ forbids manifest-declared receivers for package change broadcasts, so this
 * receiver is registered at runtime. Background changes while DroidScanner is closed are
 * caught by [com.idiots3.riskscanner.worker.BackgroundSecurityWorker] and on resume via
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

/**
 * Deep-linked local notifications for high-risk installs, special-access or signer changes,
 * invalidated trusted apps, and device-posture regressions.
 */
object InstallAlertNotifier {

    const val EXTRA_OPEN_PACKAGE = "extra_open_package"
    const val EXTRA_OPEN_TAB = "extra_open_tab"
    const val TAB_DEVICE = "device"
    const val TAB_HISTORY = "history"

    private const val CHANNEL_RISK = "risk_alerts"
    private const val CHANNEL_POSTURE = "posture_alerts"

    fun notifyHighRisk(context: Context, app: AppInfo, detailPrefix: String? = null) {
        val body = buildString {
            if (!detailPrefix.isNullOrBlank()) {
                append(detailPrefix)
                append(" ")
            }
            append("${app.appName} scored ${app.riskScore}/100 (${app.riskLevel.label} risk). Tap to see why.")
        }
        notifySecurityEvent(
            context = context,
            notificationId = app.packageName.hashCode(),
            title = "${app.riskLevel.label} risk app: ${app.appName}",
            body = body,
            targetPackage = app.packageName,
            openTab = null,
            highPriority = true
        )
    }

    fun notifySecurityEvent(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        targetPackage: String? = null,
        openTab: String? = null,
        highPriority: Boolean = true
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannels(manager)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!targetPackage.isNullOrBlank()) putExtra(EXTRA_OPEN_PACKAGE, targetPackage)
            if (!openTab.isNullOrBlank()) putExtra(EXTRA_OPEN_TAB, openTab)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (highPriority) CHANNEL_RISK else CHANNEL_POSTURE
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(notificationId, notification)
        } catch (_: Throwable) {
        }
    }

    private fun ensureChannels(manager: NotificationManager) {
        val riskChannel = NotificationChannel(
            CHANNEL_RISK,
            "App security alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for new High/Critical apps, special-access grants, or signing changes"
        }
        val postureChannel = NotificationChannel(
            CHANNEL_POSTURE,
            "Device configuration alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when a key device security setting weakens (e.g. new CA cert or debugging)"
        }
        manager.createNotificationChannel(riskChannel)
        manager.createNotificationChannel(postureChannel)
    }
}
