package com.idiots3.riskscanner.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.idiots3.riskscanner.scanner.AppScanner
import com.idiots3.riskscanner.model.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InstallMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        val action = intent.action ?: return

        if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) {
            val scanner = AppScanner(context)
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val apps = scanner.scanAllApps(includeSystemApps = true)
                    val app = apps.find { it.packageName == packageName }
                    if (app != null && (app.riskLevel == RiskLevel.HIGH || app.riskLevel == RiskLevel.CRITICAL)) {
                        showNotification(context, app.appName, app.riskScore, app.riskLevel, packageName)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showNotification(context: Context, appName: String, score: Int, level: RiskLevel, packageName: String) {
        val channelId = "risk_alerts"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Risk Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts for high-risk app installations"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("âš ï¸ ${level.label} Risk App Installed")
            .setContentText("$appName scored $score/100. Tap to review.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$appName scored $score/100 (${level.label}). This app may have dangerous permissions or access. Tap to open ShinoDroid Risk Auditor for details."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(packageName.hashCode(), notification)
    }
}

