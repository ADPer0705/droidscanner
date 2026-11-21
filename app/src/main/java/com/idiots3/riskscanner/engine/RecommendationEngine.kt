package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.BehaviourProfile
import com.idiots3.riskscanner.model.BehaviourScanResult
import com.idiots3.riskscanner.model.DevicePosture
import com.idiots3.riskscanner.model.InstallProvenance
import com.idiots3.riskscanner.model.PostureAction
import com.idiots3.riskscanner.model.PostureStatus
import com.idiots3.riskscanner.model.RecommendationAction
import com.idiots3.riskscanner.model.RecommendationPriority
import com.idiots3.riskscanner.model.RecommendationSummary
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.SecurityRecommendation
import kotlin.math.roundToInt

/**
 * Single source of truth for the unified Device Security Score and prioritized,
 * actionable user recommendations across App Safety, Device Hardening, Special Access,
 * and optional Behaviour Insights.
 */
object RecommendationEngine {

    fun generate(
        apps: List<AppInfo>,
        posture: DevicePosture?,
        behaviourResult: BehaviourScanResult? = null
    ): RecommendationSummary {
        val recs = mutableListOf<SecurityRecommendation>()

        recs += appRiskRecommendations(apps)
        recs += specialAccessRecommendations(apps)
        if (posture != null) {
            recs += postureRecommendations(posture)
        }
        if (behaviourResult != null && behaviourResult.permissionGranted) {
            recs += behaviourRecommendations(behaviourResult.profiles, apps)
        }
        recs += sideloadRecommendations(apps)

        val sorted = recs
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<SecurityRecommendation> { it.priority.weight }
                    .thenBy { it.action == RecommendationAction.NONE }
                    .thenBy { it.title }
            )

        val appScore = appSafetyScore(apps)
        val postureScore = posture?.protectionScore ?: 100
        val hasBehaviourData = behaviourResult?.permissionGranted == true && behaviourResult.profiles.isNotEmpty()

        val breakdown = linkedMapOf(
            "App Safety" to appScore,
            "Device Hardening" to postureScore
        )
        val deviceScore = if (hasBehaviourData) {
            val behScore = behaviourScore(behaviourResult!!.profiles)
            breakdown["Behaviour"] = behScore
            (appScore * 0.50 + postureScore * 0.35 + behScore * 0.15).roundToInt().coerceIn(0, 100)
        } else {
            // Do not penalize users who choose not to grant optional Usage Access.
            (appScore * 0.60 + postureScore * 0.40).roundToInt().coerceIn(0, 100)
        }

        return RecommendationSummary(
            recommendations = sorted,
            deviceScore = deviceScore,
            scoreBreakdown = breakdown,
            behaviourIncluded = hasBehaviourData
        )
    }

    private fun appRiskRecommendations(apps: List<AppInfo>): List<SecurityRecommendation> {
        val recs = mutableListOf<SecurityRecommendation>()
        for (app in apps.filter { it.riskLevel == RiskLevel.CRITICAL }.sortedByDescending { it.riskScore }) {
            recs += SecurityRecommendation(
                id = "critical_app_${app.packageName}",
                priority = RecommendationPriority.CRITICAL,
                title = "Audit critical app: ${app.appName}",
                description = app.riskReasons.take(2).joinToString(". ") { it.title } +
                    ". Review why it was flagged and consider revoking access or uninstalling.",
                action = RecommendationAction.OPEN_APP_DETAIL,
                actionLabel = "Inspect app",
                targetPackage = app.packageName,
                category = "App Risk"
            )
        }
        for (app in apps.filter { it.riskLevel == RiskLevel.HIGH }.sortedByDescending { it.riskScore }.take(5)) {
            recs += SecurityRecommendation(
                id = "high_app_${app.packageName}",
                priority = RecommendationPriority.HIGH,
                title = "Review high-risk app: ${app.appName}",
                description = app.riskReasons.firstOrNull()?.description
                    ?: "Holds sensitive permissions or special privileges worth reviewing.",
                action = RecommendationAction.OPEN_APP_DETAIL,
                actionLabel = "Inspect app",
                targetPackage = app.packageName,
                category = "App Risk"
            )
        }
        return recs
    }

    private fun specialAccessRecommendations(apps: List<AppInfo>): List<SecurityRecommendation> {
        val recs = mutableListOf<SecurityRecommendation>()

        val sideloadedA11y = apps.filter { it.specialAccess.accessibilityEnabled && it.isSideloaded }
        for (app in sideloadedA11y) {
            recs += SecurityRecommendation(
                id = "sideloaded_a11y_${app.packageName}",
                priority = RecommendationPriority.CRITICAL,
                title = "Sideloaded app has Accessibility access: ${app.appName}",
                description = "Can read screens and simulate taps while installed outside official stores.",
                action = RecommendationAction.OPEN_ACCESSIBILITY_SETTINGS,
                actionLabel = "Accessibility settings",
                targetPackage = app.packageName,
                category = "Permissions"
            )
        }

        val accessibilityApps = apps.filter { it.specialAccess.accessibilityEnabled && !it.isSystemApp }
        if (accessibilityApps.size > 2) {
            recs += SecurityRecommendation(
                id = "many_accessibility",
                priority = RecommendationPriority.HIGH,
                title = "${accessibilityApps.size} non-system apps have Accessibility access",
                description = "Keep Accessibility enabled only for trusted tools (e.g., screen readers or password managers). " +
                    "Active: ${accessibilityApps.take(3).joinToString { it.appName }}.",
                action = RecommendationAction.OPEN_ACCESSIBILITY_SETTINGS,
                actionLabel = "Review accessibility",
                category = "Permissions"
            )
        }

        val adminApps = apps.filter { it.specialAccess.deviceAdminActive }
        for (app in adminApps) {
            if (!app.isSystemApp && !RiskScorer.isSecurityTool(app)) {
                recs += SecurityRecommendation(
                    id = "device_admin_${app.packageName}",
                    priority = RecommendationPriority.HIGH,
                    title = "Non-security Device Admin: ${app.appName}",
                    description = "Active device admins can prevent uninstall, lock the screen, and wipe the phone.",
                    action = RecommendationAction.OPEN_DEVICE_ADMIN_SETTINGS,
                    actionLabel = "Device admin settings",
                    targetPackage = app.packageName,
                    category = "Permissions"
                )
            }
        }

        val notificationApps = apps.filter {
            it.specialAccess.notificationListenerEnabled && !isKnownLegitimateNotificationListener(it)
        }
        for (app in notificationApps.take(3)) {
            recs += SecurityRecommendation(
                id = "notification_listener_${app.packageName}",
                priority = RecommendationPriority.MEDIUM,
                title = "Notification access: ${app.appName}",
                description = "Reads all incoming notifications, including SMS OTP codes and private chats.",
                action = RecommendationAction.OPEN_NOTIFICATION_SETTINGS,
                actionLabel = "Notification access",
                targetPackage = app.packageName,
                category = "Permissions"
            )
        }
        return recs
    }

    private fun postureRecommendations(posture: DevicePosture): List<SecurityRecommendation> {
        val atRisk = posture.checks
            .filter { it.status == PostureStatus.AT_RISK }
            .map { check ->
                SecurityRecommendation(
                    id = "posture_${check.id}",
                    priority = RecommendationPriority.HIGH,
                    title = check.title,
                    description = "${check.detail} ${check.recommendation}",
                    action = mapPostureAction(check.action),
                    actionLabel = "Fix now",
                    category = "Device Hardening"
                )
            }
        val review = posture.checks
            .filter { it.status == PostureStatus.REVIEW }
            .map { check ->
                SecurityRecommendation(
                    id = "posture_review_${check.id}",
                    priority = RecommendationPriority.MEDIUM,
                    title = "${check.title} — review",
                    description = "${check.detail} ${check.recommendation}",
                    action = mapPostureAction(check.action),
                    actionLabel = "Review",
                    category = "Device Hardening"
                )
            }
        return atRisk + review
    }

    private fun mapPostureAction(action: PostureAction?): RecommendationAction = when (action) {
        PostureAction.SECURITY_SETTINGS -> RecommendationAction.OPEN_SECURITY_SETTINGS
        PostureAction.SYSTEM_UPDATE -> RecommendationAction.OPEN_SYSTEM_UPDATE
        PostureAction.DEVELOPER_SETTINGS -> RecommendationAction.OPEN_DEVELOPER_SETTINGS
        PostureAction.ACCESSIBILITY_SETTINGS -> RecommendationAction.OPEN_ACCESSIBILITY_SETTINGS
        PostureAction.NOTIFICATION_SETTINGS -> RecommendationAction.OPEN_NOTIFICATION_SETTINGS
        PostureAction.CREDENTIAL_SETTINGS -> RecommendationAction.OPEN_CREDENTIAL_SETTINGS
        PostureAction.NETWORK_SETTINGS -> RecommendationAction.OPEN_NETWORK_SETTINGS
        PostureAction.USAGE_ACCESS_SETTINGS -> RecommendationAction.OPEN_USAGE_ACCESS_SETTINGS
        PostureAction.APP_REVIEW, null -> RecommendationAction.NONE
    }

    private fun behaviourRecommendations(
        profiles: Map<String, BehaviourProfile>,
        apps: List<AppInfo>
    ): List<SecurityRecommendation> {
        val recs = mutableListOf<SecurityRecommendation>()
        val appMap = apps.associateBy { it.packageName }
        for ((pkg, profile) in profiles) {
            if (!profile.hasAnomalies) continue
            val app = appMap[pkg] ?: continue
            for (anomaly in profile.anomalies.take(2)) {
                recs += SecurityRecommendation(
                    id = "behaviour_${pkg}_${anomaly.type.replace(' ', '_')}",
                    priority = RecommendationPriority.MEDIUM,
                    title = "${app.appName}: ${anomaly.type}",
                    description = "${anomaly.detail}. ${anomaly.recommendation}",
                    action = RecommendationAction.OPEN_APP_DETAIL,
                    actionLabel = "Inspect app",
                    targetPackage = pkg,
                    category = "Behaviour"
                )
            }
        }
        return recs
    }

    private fun sideloadRecommendations(apps: List<AppInfo>): List<SecurityRecommendation> {
        val browserSideloaded = apps.filter {
            !it.isSystemApp && it.installProvenance == InstallProvenance.BROWSER_OR_APP_SIDELOAD
        }
        val otherSideloaded = apps.filter {
            it.isSideloaded && it.installProvenance != InstallProvenance.BROWSER_OR_APP_SIDELOAD
        }

        val recs = mutableListOf<SecurityRecommendation>()
        if (browserSideloaded.isNotEmpty()) {
            recs += SecurityRecommendation(
                id = "browser_sideloaded_apps",
                priority = if (browserSideloaded.size > 1) RecommendationPriority.HIGH else RecommendationPriority.MEDIUM,
                title = "${browserSideloaded.size} app(s) sideloaded via another app",
                description = "Installed via browsers or messaging apps without app-store verification: " +
                    browserSideloaded.take(3).joinToString { it.appName } + ".",
                action = RecommendationAction.OPEN_APP_DETAIL,
                actionLabel = "Inspect first",
                targetPackage = browserSideloaded.maxByOrNull { it.riskScore }?.packageName,
                category = "App Risk"
            )
        } else if (otherSideloaded.isNotEmpty()) {
            recs += SecurityRecommendation(
                id = "sideloaded_apps",
                priority = if (otherSideloaded.size > 3) RecommendationPriority.HIGH else RecommendationPriority.INFO,
                title = "${otherSideloaded.size} sideloaded app(s) detected",
                description = "Installed outside recognized stores: ${otherSideloaded.take(3).joinToString { it.appName }}.",
                action = RecommendationAction.NONE,
                category = "App Risk"
            )
        }
        return recs
    }

    private fun appSafetyScore(apps: List<AppInfo>): Int {
        if (apps.isEmpty()) return 100
        val penalty = apps.sumOf { app ->
            when (app.riskLevel) {
                RiskLevel.CRITICAL -> 18
                RiskLevel.HIGH -> 7
                RiskLevel.MEDIUM -> 2
                RiskLevel.LOW -> 0
            }
        }
        return (100 - penalty).coerceIn(0, 100)
    }

    private fun behaviourScore(profiles: Map<String, BehaviourProfile>): Int {
        if (profiles.isEmpty()) return 100
        val anomalyApps = profiles.values.count { it.hasAnomalies }
        return (100 - anomalyApps * 10).coerceIn(0, 100)
    }

    private val knownListenerPackagePrefixes = listOf(
        "com.google.android",
        "com.samsung.android",
        "com.android.",
        "com.spotify",
        "com.shazam"
    )

    private fun isKnownLegitimateNotificationListener(app: AppInfo): Boolean =
        app.isSystemApp || knownListenerPackagePrefixes.any { app.packageName.startsWith(it) }
}
