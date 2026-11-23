package com.idiots3.riskscanner.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.idiots3.riskscanner.data.AuditHistoryStore
import com.idiots3.riskscanner.data.ScannerPreferences
import com.idiots3.riskscanner.data.SnapshotStore
import com.idiots3.riskscanner.data.TrustStore
import com.idiots3.riskscanner.engine.PermissionDiff
import com.idiots3.riskscanner.engine.RecommendationEngine
import com.idiots3.riskscanner.model.ChangeType
import com.idiots3.riskscanner.model.PermissionChange
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.TrustCheckResult
import com.idiots3.riskscanner.model.TrustValidator
import com.idiots3.riskscanner.receiver.InstallAlertNotifier
import com.idiots3.riskscanner.scanner.AppScanner
import com.idiots3.riskscanner.scanner.DevicePostureScanner

/**
 * Battery-friendly, low-RAM periodic security check executed by WorkManager while
 * DroidScanner is closed. Runs metadata-only checks (no MediaPipe ML model, no DEX parsing)
 * and alerts ONLY on real escalations, signing changes, invalidated trust, or posture regressions.
 */
class BackgroundSecurityWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val now = System.currentTimeMillis()
            val appScanner = AppScanner(ctx)
            val postureScanner = DevicePostureScanner(ctx)
            val snapshotStore = SnapshotStore(ctx)
            val trustStore = TrustStore(ctx)
            val historyStore = AuditHistoryStore(ctx)
            val prefs = ScannerPreferences(ctx)

            val scannedApps = appScanner.scanAllApps(includeSystemApps = false)
            val trustState = trustStore.load()
            val updatedTrustedApps = trustState.trustedApps.toMutableMap()
            val trustBrokenEvents = mutableListOf<PermissionChange>()

            val evaluatedApps = scannedApps.map { app ->
                val record = trustState.trustedApps[app.packageName]
                if (record == null) {
                    app
                } else {
                    when (val check = TrustValidator.validateAppTrust(app, record)) {
                        TrustCheckResult.Valid -> app.copy(isUserTrusted = true)
                        is TrustCheckResult.Invalidated -> {
                            updatedTrustedApps.remove(app.packageName)
                            trustBrokenEvents += PermissionChange(
                                packageName = app.packageName,
                                appName = app.appName,
                                type = ChangeType.TRUST_BROKEN,
                                detail = check.reasons.joinToString("; "),
                                oldScore = app.riskScore,
                                newScore = app.riskScore,
                                timestamp = now
                            )
                            app.copy(isUserTrusted = false)
                        }
                    }
                }
            }

            if (trustBrokenEvents.isNotEmpty()) {
                trustStore.save(trustState.copy(trustedApps = updatedTrustedApps))
            }

            val previousSnapshot = snapshotStore.load()
            val appChanges = PermissionDiff.compute(previousSnapshot, evaluatedApps, now, userAppsOnly = true)
            snapshotStore.save(PermissionDiff.snapshotOf(evaluatedApps, userAppsOnly = true))
            snapshotStore.setLastScanTime(now)

            val posture = postureScanner.scan(evaluatedApps, trustState.dismissedPosture)
            val historyBefore = historyStore.load()
            val postureRegressions = AuditHistoryStore.detectPostureRegressions(
                historyBefore.lastPostureStates,
                posture,
                now
            )

            val allEvents = (trustBrokenEvents + appChanges + postureRegressions)
                .distinctBy { "${it.type.name}:${it.packageName}" }

            val recSummary = RecommendationEngine.generate(evaluatedApps, posture, null)
            historyStore.recordScan(
                newEvents = allEvents,
                posture = posture,
                deviceScore = recSummary.deviceScore,
                appScore = recSummary.scoreBreakdown["App Safety"],
                postureScore = posture.protectionScore,
                now = now
            )
            prefs.lastBackgroundCheckTime = now

            dispatchNotifications(ctx, allEvents, evaluatedApps)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private fun dispatchNotifications(
        context: Context,
        events: List<PermissionChange>,
        apps: List<com.idiots3.riskscanner.model.AppInfo>
    ) {
        if (events.isEmpty()) return
        val appMap = apps.associateBy { it.packageName }
        var sent = 0

        for (event in events) {
            if (sent >= MAX_ALERTS_PER_RUN) break
            when (event.type) {
                ChangeType.SIGNER_CHANGED -> {
                    InstallAlertNotifier.notifySecurityEvent(
                        context = context,
                        notificationId = ("signer:" + event.packageName).hashCode(),
                        title = "Signing certificate changed: ${event.appName}",
                        body = "The developer signature of ${event.appName} changed since your last scan. Inspect immediately.",
                        targetPackage = event.packageName,
                        highPriority = true
                    )
                    sent++
                }

                ChangeType.TRUST_BROKEN -> {
                    InstallAlertNotifier.notifySecurityEvent(
                        context = context,
                        notificationId = ("trust:" + event.packageName).hashCode(),
                        title = "Reviewed app changed: ${event.appName}",
                        body = "${event.detail ?: "Permissions or privileges changed"}. Trust was automatically cleared — tap to review.",
                        targetPackage = event.packageName,
                        highPriority = true
                    )
                    sent++
                }

                ChangeType.SPECIAL_ACCESS_GRANTED -> {
                    val app = appMap[event.packageName]
                    if (app?.isUserTrusted == true) continue
                    InstallAlertNotifier.notifySecurityEvent(
                        context = context,
                        notificationId = ("special:" + event.packageName).hashCode(),
                        title = "Special access enabled: ${event.appName}",
                        body = "${event.detail ?: "High-privilege access was enabled"}. Tap to verify you intended this.",
                        targetPackage = event.packageName,
                        highPriority = true
                    )
                    sent++
                }

                ChangeType.NEW_APP, ChangeType.PERMISSIONS_ADDED -> {
                    val app = appMap[event.packageName] ?: continue
                    if (app.isUserTrusted || app.isSelfApp) continue
                    if (app.riskScore < RiskLevel.HIGH.minScore) continue
                    val prefix = if (event.type == ChangeType.PERMISSIONS_ADDED && event.addedPermissions.isNotEmpty()) {
                        "Added ${event.addedPermissions.take(2).joinToString(", ")}."
                    } else null
                    InstallAlertNotifier.notifyHighRisk(context, app, prefix)
                    sent++
                }

                ChangeType.POSTURE_REGRESSED -> {
                    InstallAlertNotifier.notifySecurityEvent(
                        context = context,
                        notificationId = ("posture:" + event.packageName).hashCode(),
                        title = "Device setting weakened: ${event.appName}",
                        body = event.detail ?: "A device protection setting changed. Tap to inspect.",
                        openTab = InstallAlertNotifier.TAB_DEVICE,
                        highPriority = false
                    )
                    sent++
                }

                else -> Unit
            }
        }
    }

    private companion object {
        const val MAX_ALERTS_PER_RUN = 4
    }
}
