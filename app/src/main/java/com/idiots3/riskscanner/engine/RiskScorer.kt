package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.*
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic, explainable risk engine. The embedding/AI signal is only ever
 * applied as a small, clearly labelled addition on top of this result.
 */
object RiskScorer {

    private val SMS_PERMS = setOf(
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_MMS",
        "android.permission.RECEIVE_WAP_PUSH"
    )
    private val CONTACTS_PERMS = setOf(
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS"
    )
    private val CALL_LOG_PERMS = setOf(
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG"
    )
    private val TRACKED_SPECIAL = setOf(
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.PACKAGE_USAGE_STATS"
    )
    private val INTERNET = "android.permission.INTERNET"
    private val BOOT = "android.permission.RECEIVE_BOOT_COMPLETED"
    private val CAMERA = "android.permission.CAMERA"
    private val MIC = "android.permission.RECORD_AUDIO"
    private val BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
    private val ANY_LOCATION = setOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION"
    )

    fun score(app: AppInfo, ai: AiAssessment? = null): ScoreResult {
        val breakdown = mutableListOf<ScoreLine>()
        val reasons = mutableListOf<RiskReason>()
        var rulesScore = 0

        fun addLine(label: String, points: Int, detail: String) {
            if (points <= 0) return
            rulesScore += points
            breakdown += ScoreLine(label, points, detail, ScoreSource.RULES)
        }

        // ------------------------------------------------------------------
        // 1. Category scoring across the nine mandatory domains
        // ------------------------------------------------------------------
        val byCategory = app.permissions.groupBy { it.category }
        for (category in PermissionCategory.entries) {
            if (category == PermissionCategory.SPECIAL_ACCESS) continue
            val perms = byCategory[category] ?: continue
            val units = perms.sumOf { ScoringConfig.unitFor(it) }
            val weight = ScoringConfig.categoryWeight(category)
            if (units <= 0.0 || weight <= 0) continue

            val points = (weight * min(1.0, units / ScoringConfig.CATEGORY_SATURATION_UNITS)).roundToInt()
            if (points <= 0) continue

            val granted = perms.count { it.isGranted && it.isSensitive }
            val declaredOnly = perms.count { !it.isGranted && it.isSensitive }

            addLine(
                label = "${category.label} access",
                points = points,
                detail = buildString {
                    append("${perms.size} permission(s)")
                    if (granted > 0) append(" · $granted granted")
                    if (declaredOnly > 0) append(" · $declaredOnly declared only")
                    append(" — ")
                    append(perms.take(4).joinToString(", ") { it.shortName })
                }
            )

            if (granted > 0 || declaredOnly > 0) {
                reasons += RiskReason(
                    title = "${category.label} access",
                    description = buildString {
                        if (granted > 0) {
                            append("$granted sensitive permission(s) granted: ")
                            append(perms.filter { it.isGranted && it.isSensitive }.take(4).joinToString(", ") { it.shortName })
                        }
                        if (declaredOnly > 0) {
                            if (granted > 0) append(". ")
                            append("$declaredOnly declared but not granted")
                        }
                        append(". ${category.description}.")
                    },
                    severity = severityFor(points, weight),
                    category = category
                )
            }
        }

        // ------------------------------------------------------------------
        // 2. Special access (highest weight; enabled vs declared-only)
        // ------------------------------------------------------------------
        val sa = app.specialAccess
        if (sa.accessibilityEnabled) {
            addLine("Accessibility service enabled", ScoringConfig.Special.ACCESSIBILITY_ENABLED, "Verified through Android's enabled accessibility services list")
            reasons += RiskReason(
                "Accessibility service enabled",
                "Can read everything on screen, capture passwords, and tap buttons for you. This is the single most abused special access on Android.",
                RiskLevel.CRITICAL, PermissionCategory.SPECIAL_ACCESS
            )
        } else if (sa.accessibilityDeclared) {
            addLine("Offers an accessibility service", ScoringConfig.Special.ACCESSIBILITY_DECLARED, "Declared in the manifest but NOT currently enabled")
            reasons += RiskReason(
                "Offers an accessibility service",
                "Ships an accessibility service that is not currently enabled. Verify you recognise this app before enabling it.",
                RiskLevel.MEDIUM, PermissionCategory.SPECIAL_ACCESS
            )
        }
        if (sa.deviceAdminActive) {
            addLine("Device administrator active", ScoringConfig.Special.DEVICE_ADMIN_ACTIVE, "App is an active device administrator")
            reasons += RiskReason(
                "Device administrator active",
                "Can lock the screen, wipe data, and may block its own uninstall. Remove device-admin rights if you did not grant them on purpose.",
                RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS
            )
        }
        if (sa.notificationListenerEnabled) {
            addLine("Notification listener enabled", ScoringConfig.Special.NOTIFICATION_LISTENER_ENABLED, "Verified through the system notification access registry")
            reasons += RiskReason(
                "Notification access enabled",
                "Reads every notification, including banking OTP codes and private messages.",
                RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS
            )
        }
        if (sa.overlayEnabled) {
            addLine("Screen overlay enabled", ScoringConfig.Special.OVERLAY_ENABLED, "Can draw over other apps (AppOps verified)")
            reasons += RiskReason(
                "Screen overlay enabled",
                "Can draw windows on top of other apps — the classic way to fake a login screen.",
                RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS
            )
        } else if (sa.overlayDeclared) {
            addLine("Requests screen overlay", ScoringConfig.Special.OVERLAY_DECLARED, "Declared but not granted")
        }
        if (sa.installPackagesEnabled) {
            addLine("Can install apps", ScoringConfig.Special.INSTALL_PACKAGES_ENABLED, "REQUEST_INSTALL_PACKAGES granted (AppOps verified)")
            reasons += RiskReason(
                "Can install other apps",
                "Can push additional APK installs without the Play Store — a common second-stage malware step.",
                RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS
            )
        } else if (sa.installPackagesDeclared) {
            addLine("Requests install-apps access", ScoringConfig.Special.INSTALL_PACKAGES_DECLARED, "Declared but not granted")
        }
        if (sa.usageStatsEnabled) {
            addLine("Usage statistics access", ScoringConfig.Special.USAGE_STATS_ENABLED, "Can see which apps you open and when (AppOps verified)")
        } else if (sa.usageStatsDeclared) {
            addLine("Requests usage statistics", ScoringConfig.Special.USAGE_STATS_DECLARED, "Declared but not granted")
        }

        val untrackedSpecial = app.permissions.filter {
            it.category == PermissionCategory.SPECIAL_ACCESS && !TRACKED_SPECIAL.contains(it.name) && it.isDeclared
        }
        if (untrackedSpecial.isNotEmpty()) {
            val points = min(
                ScoringConfig.Special.UNTRACKED_DECLARED_CAP,
                ScoringConfig.Special.UNTRACKED_DECLARED_PER_PERMISSION * untrackedSpecial.size
            )
            addLine(
                "Other privileged permissions",
                points,
                untrackedSpecial.joinToString(", ") { it.shortName }
            )
            reasons += RiskReason(
                "Privileged permissions",
                "Requests high-privilege permissions: ${untrackedSpecial.joinToString(", ") { it.shortName }}.",
                RiskLevel.MEDIUM, PermissionCategory.SPECIAL_ACCESS
            )
        }

        // ------------------------------------------------------------------
        // 3. Combination rules
        // ------------------------------------------------------------------
        val grantedNames = app.permissions.filter { it.isGranted }.map { it.name }.toSet()
        val smsGranted = grantedNames.any { SMS_PERMS.contains(it) }
        val contactsGranted = grantedNames.any { CONTACTS_PERMS.contains(it) }
        val callLogGranted = grantedNames.any { CALL_LOG_PERMS.contains(it) }
        val internet = grantedNames.contains(INTERNET)
        val boot = grantedNames.contains(BOOT)
        val camera = grantedNames.contains(CAMERA)
        val mic = grantedNames.contains(MIC)
        val location = grantedNames.any { ANY_LOCATION.contains(it) }
        val backgroundLocation = grantedNames.contains(BACKGROUND_LOCATION)
        val hidden = !app.hasLauncherActivity
        val sideloaded = app.isSideloaded

        fun combo(id: String, detail: String, severity: RiskLevel = RiskLevel.CRITICAL, category: PermissionCategory = PermissionCategory.SPECIAL_ACCESS) {
            val rule = ScoringConfig.comboRules.first { it.id == id }
            addLine(rule.name, rule.points, detail)
            reasons += RiskReason(rule.name, "${rule.rationale} ($detail)", severity, category)
        }

        if (sa.accessibilityEnabled && sa.overlayEnabled) {
            combo("banking_trojan", "accessibility + overlay both enabled", RiskLevel.CRITICAL, PermissionCategory.SPECIAL_ACCESS)
        }
        if (smsGranted && internet && boot) {
            combo("sms_stealer", "SMS access + internet + starts on boot", RiskLevel.CRITICAL, PermissionCategory.SMS)
        }
        if (isUtilityApp(app) && (smsGranted || contactsGranted || callLogGranted || mic || camera || location)) {
            val matched = buildList {
                if (smsGranted) add("SMS")
                if (contactsGranted) add("contacts")
                if (callLogGranted) add("call log")
                if (mic) add("microphone")
                if (camera) add("camera")
                if (location) add("location")
            }.joinToString(", ")
            combo(
                "utility_mismatch",
                "a ${app.appName} normally needs none of: $matched",
                RiskLevel.CRITICAL, PermissionCategory.SPECIAL_ACCESS
            )
        }
        if (camera && mic && location && internet && (hidden || sideloaded)) {
            combo(
                "hidden_spyware",
                if (hidden) "records, locates and uploads with no launcher icon" else "sideloaded surveillance capability set",
                RiskLevel.CRITICAL, PermissionCategory.CAMERA_MICROPHONE
            )
        }
        if (contactsGranted && callLogGranted && smsGranted && internet) {
            combo("data_harvester", "contacts + call log + SMS + internet", RiskLevel.CRITICAL, PermissionCategory.CONTACTS_CALL_LOGS)
        }
        if (sa.deviceAdminActive && !isSecurityTool(app)) {
            combo("device_admin_mismatch", "device admin on a non-security app", RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS)
        }
        if (sideloaded && smsGranted) {
            combo("sideloaded_sms", "sideloaded + SMS access", RiskLevel.CRITICAL, PermissionCategory.SMS)
        }
        if (sa.notificationListenerEnabled && internet && (sideloaded || hidden || sa.accessibilityEnabled || smsGranted)) {
            combo("otp_exfiltration", "notification listener + internet + secondary capability", RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS)
        }
        if (backgroundLocation && internet) {
            combo("background_tracking", "background location + internet", RiskLevel.HIGH, PermissionCategory.LOCATION)
        }

        // ------------------------------------------------------------------
        // 4. Sideloading provenance
        // ------------------------------------------------------------------
        if (sideloaded && app.grantedSensitive.isNotEmpty()) {
            addLine(
                "Sideloaded source",
                ScoringConfig.SIDELOAD_PENALTY,
                "Not installed from an app store, holds sensitive granted permissions"
            )
            reasons += RiskReason(
                "Sideloaded app with sensitive access",
                "Installed outside app stores (no Play Protect screening) while holding sensitive permissions.",
                RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS
            )
        }

        // ------------------------------------------------------------------
        // 5. Bounded on-device AI signal (never authoritative, never alone Critical)
        // ------------------------------------------------------------------
        var aiDelta = 0
        if (ai != null && ai.modelUsed) {
            if (ai.unexplainedCategories.isNotEmpty() && rulesScore >= ScoringConfig.AI_MIN_RULE_SCORE) {
                aiDelta = aiDeltaFor(ai.unexplainedCategories)
                breakdown += ScoreLine(
                    label = "AI context signal (weak)",
                    points = aiDelta,
                    detail = "On-device purpose check: " +
                        ai.unexplainedCategories.joinToString(", ") { it.label } +
                        " is not explained by \"${ai.purposeLabel ?: "unknown purpose"}\"",
                    source = ScoreSource.AI
                )
                reasons += RiskReason(
                    "Unexplained access (on-device AI check)",
                    "This app looks like \"${ai.purposeLabel ?: "an unclear purpose"}\", which does not explain its " +
                        ai.unexplainedCategories.joinToString(", ") { it.label.lowercase() } +
                        " permissions. Weak signal — please verify manually.",
                    RiskLevel.MEDIUM,
                    ai.unexplainedCategories.first()
                )
            } else if (ai.unexplainedCategories.isNotEmpty()) {
                breakdown += ScoreLine(
                    label = "AI context signal (weak)",
                    points = 0,
                    detail = "Unexplained access noted for explanation only (rules score below Medium, so no points added)",
                    source = ScoreSource.AI
                )
            }
        }

        var total = (rulesScore + aiDelta).coerceIn(0, 100)
        // The AI component can never create a Critical verdict on its own.
        if (rulesScore < ScoringConfig.AI_CRITICAL_FLOOR) {
            total = min(total, ScoringConfig.AI_CRITICAL_FLOOR - 1)
        }
        val level = levelFor(total)

        // Guarantee at least three plain-language reasons for High/Critical apps.
        if (level == RiskLevel.HIGH || level == RiskLevel.CRITICAL) {
            val paddable = breakdown
                .filter { it.source == ScoreSource.RULES && it.points > 0 }
                .sortedByDescending { it.points }
            var index = 0
            while (reasons.size < ScoringConfig.MIN_REASONS_FOR_HIGH_RISK && index < paddable.size) {
                val line = paddable[index]
                if (reasons.none { it.title.equals(line.label, ignoreCase = true) }) {
                    reasons += RiskReason(
                        title = line.label,
                        description = "Score driver: ${line.detail}.",
                        severity = RiskLevel.MEDIUM,
                        category = PermissionCategory.OTHER
                    )
                }
                index++
            }
        }

        val sortedReasons = reasons.sortedByDescending { it.severity.ordinal }
        val sortedBreakdown = breakdown.sortedByDescending { it.points }

        return ScoreResult(
            score = total,
            level = level,
            ruleScore = rulesScore.coerceIn(0, 100),
            aiDelta = aiDelta,
            reasons = sortedReasons,
            breakdown = sortedBreakdown
        )
    }

    fun levelFor(score: Int): RiskLevel = when {
        score >= 76 -> RiskLevel.CRITICAL
        score >= 51 -> RiskLevel.HIGH
        score >= 26 -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

    /**
     * Bounded AI contribution:
     *   1 unexplained category  -> +2 (or +3 if it is SMS / contacts+call log)
     *   2 unexplained           -> +4
     *   3 unexplained           -> +6
     *   4+ unexplained          -> +8
     */
    fun aiDeltaFor(unexplained: Collection<PermissionCategory>): Int {
        val count = unexplained.size
        val base = when {
            count >= 4 -> 8
            count == 3 -> 6
            count == 2 -> 4
            else -> if (unexplained.any { ScoringConfig.highSensitivityCategories.contains(it) }) 3 else 2
        }
        return min(base, ScoringConfig.AI_MAX_DELTA)
    }

    private fun severityFor(points: Int, weight: Int): RiskLevel = when {
        weight <= 0 -> RiskLevel.LOW
        points >= weight * 0.75 -> RiskLevel.HIGH
        points >= weight * 0.4 -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

    fun isUtilityApp(app: AppInfo): Boolean {
        val haystack = (app.appName + " " + app.packageName).lowercase()
        return ScoringConfig.utilityKeywords.any { haystack.contains(it) }
    }

    fun isSecurityTool(app: AppInfo): Boolean {
        val haystack = (app.appName + " " + app.packageName).lowercase()
        return ScoringConfig.securityKeywords.any { haystack.contains(it) }
    }
}
