package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.*

object RiskScorer {

    data class ScoringResult(
        val score: Int,
        val level: RiskLevel,
        val reasons: List<RiskReason>
    )

    fun calculateRisk(permissions: List<PermissionDetail>, specialAccess: SpecialAccessInfo, installerSource: String, appName: String): ScoringResult {
        var score = 0
        val reasons = mutableListOf<RiskReason>()

        // 1. Category-based scoring
        val grantedByCategory = permissions.filter { it.isGranted || it.isDeclared }.groupBy { it.category }
        
        for ((category, perms) in grantedByCategory) {
            val categoryScore = calculateCategoryScore(category, perms)
            score += categoryScore
            if (categoryScore > 0 && category != PermissionCategory.NETWORK) {
                val dangerousPerms = perms.filter { it.protectionLevel == ProtectionLevel.DANGEROUS || it.protectionLevel == ProtectionLevel.SPECIAL }
                if (dangerousPerms.isNotEmpty()) {
                    reasons.add(RiskReason(
                        title = "${category.label} Access",
                        description = "Has ${dangerousPerms.size} sensitive ${category.label.lowercase()} permission(s): ${dangerousPerms.take(3).joinToString(", ") { it.shortName }}",
                        severity = if (categoryScore >= 15) RiskLevel.CRITICAL else if (categoryScore >= 10) RiskLevel.HIGH else RiskLevel.MEDIUM,
                        category = category
                    ))
                }
            }
        }

        // 2. Special access bonus scoring (highest weight)
        if (specialAccess.hasAccessibilityService) {
            score += 20
            reasons.add(RiskReason("Accessibility Service", "Can read your screen content, capture passwords, and perform actions on your behalf", RiskLevel.CRITICAL, PermissionCategory.SPECIAL_ACCESS))
        }
        if (specialAccess.hasDeviceAdmin) {
            score += 18
            reasons.add(RiskReason("Device Administrator", "Can lock your device, wipe data, and prevent its own uninstall", RiskLevel.CRITICAL, PermissionCategory.SPECIAL_ACCESS))
        }
        if (specialAccess.hasNotificationListener) {
            score += 15
            reasons.add(RiskReason("Notification Listener", "Can read ALL your notifications including banking OTPs and private messages", RiskLevel.CRITICAL, PermissionCategory.SPECIAL_ACCESS))
        }
        if (specialAccess.hasOverlayPermission) {
            score += 12
            reasons.add(RiskReason("Screen Overlay", "Can draw over other apps to create fake login screens", RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS))
        }
        if (specialAccess.hasInstallPackages) {
            score += 10
            reasons.add(RiskReason("Install Apps", "Can install additional apps without Play Store", RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS))
        }
        if (specialAccess.hasUsageStats) {
            score += 8
            reasons.add(RiskReason("Usage Statistics", "Can monitor which apps you use and when", RiskLevel.MEDIUM, PermissionCategory.SPECIAL_ACCESS))
        }

        // 3. Combination rules (Should Have #07)
        score += applyCombinationRules(permissions, specialAccess, installerSource, appName, reasons)

        // 4. Sideloaded app penalty
        if (installerSource == "Sideloaded" || installerSource == "Unknown") {
            val hasSensitive = permissions.any { it.protectionLevel == ProtectionLevel.DANGEROUS && it.isGranted }
            if (hasSensitive) {
                score += 10
                reasons.add(RiskReason("Sideloaded App", "This app was not installed from the Play Store and has sensitive permissions", RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS))
            }
        }

        // Clamp to 0-100
        score = score.coerceIn(0, 100)
        val level = when {
            score >= 76 -> RiskLevel.CRITICAL
            score >= 51 -> RiskLevel.HIGH
            score >= 26 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return ScoringResult(score, level, reasons.sortedByDescending { it.severity.ordinal })
    }

    private fun calculateCategoryScore(category: PermissionCategory, perms: List<PermissionDetail>): Int {
        val dangerousCount = perms.count { it.protectionLevel == ProtectionLevel.DANGEROUS || it.protectionLevel == ProtectionLevel.SPECIAL }
        val grantedDangerous = perms.count { it.isGranted && (it.protectionLevel == ProtectionLevel.DANGEROUS || it.protectionLevel == ProtectionLevel.SPECIAL) }
        
        // Base weight scaled by number of dangerous permissions, with bonus for granted ones
        val baseScore = (category.baseWeight * dangerousCount.coerceAtMost(3)) / 3
        val grantedBonus = grantedDangerous * 2
        return (baseScore + grantedBonus).coerceAtMost(category.baseWeight)
    }

    private fun applyCombinationRules(permissions: List<PermissionDetail>, specialAccess: SpecialAccessInfo, installerSource: String, appName: String, reasons: MutableList<RiskReason>): Int {
        var comboScore = 0
        val permNames = permissions.map { it.name }.toSet()
        val hasSms = permNames.any { it.contains("SMS") }
        val hasInternet = "android.permission.INTERNET" in permNames
        val hasBoot = "android.permission.RECEIVE_BOOT_COMPLETED" in permNames
        val hasCamera = "android.permission.CAMERA" in permNames
        val hasMic = "android.permission.RECORD_AUDIO" in permNames
        val hasLocation = permNames.any { it.contains("LOCATION") }
        val hasContacts = "android.permission.READ_CONTACTS" in permNames
        val hasCallLog = "android.permission.READ_CALL_LOG" in permNames
        val hasPhoneState = "android.permission.READ_PHONE_STATE" in permNames
        val hasStorage = permNames.any { it.contains("STORAGE") || it.contains("MEDIA") }

        // Rule 1: SMS + Internet + Boot (classic data stealer)
        if (hasSms && hasInternet && hasBoot) {
            comboScore += 15
            reasons.add(RiskReason("Data Stealer Pattern", "Combines SMS access + internet + auto-start â€” classic pattern for stealing OTPs and sending them to attackers", RiskLevel.CRITICAL, PermissionCategory.SMS))
        }

        // Rule 2: Accessibility + Overlay (banking trojan pattern)
        if (specialAccess.hasAccessibilityService && specialAccess.hasOverlayPermission) {
            comboScore += 20
            reasons.add(RiskReason("Banking Trojan Pattern", "Accessibility + screen overlay is the #1 technique used by banking trojans to steal credentials", RiskLevel.CRITICAL, PermissionCategory.SPECIAL_ACCESS))
        }

        // Rule 3: Device admin on non-security app
        if (specialAccess.hasDeviceAdmin) {
            val nameLower = appName.lowercase()
            val isSecurityApp = nameLower.contains("security") || nameLower.contains("antivirus") || nameLower.contains("mdm") || nameLower.contains("enterprise") || nameLower.contains("admin")
            if (!isSecurityApp) {
                comboScore += 12
                reasons.add(RiskReason("Suspicious Device Admin", "Device admin privilege on an app that doesn't appear to be a security or MDM tool", RiskLevel.CRITICAL, PermissionCategory.SPECIAL_ACCESS))
            }
        }

        // Rule 4: Camera + Mic + Location + Internet (spyware pattern)
        if (hasCamera && hasMic && hasLocation && hasInternet) {
            comboScore += 10
            reasons.add(RiskReason("Spyware Pattern", "Can record audio/video, track location, and send it over the internet", RiskLevel.HIGH, PermissionCategory.CAMERA_MICROPHONE))
        }

        // Rule 5: Contacts + Call Log + SMS + Internet (data harvester)
        if (hasContacts && hasCallLog && hasSms && hasInternet) {
            comboScore += 12
            reasons.add(RiskReason("Data Harvester Pattern", "Can harvest your entire social graph: contacts, call history, and messages", RiskLevel.CRITICAL, PermissionCategory.CONTACTS_CALL_LOGS))
        }

        // Rule 6: SMS read from sideloaded source
        if (hasSms && (installerSource == "Sideloaded" || installerSource == "Unknown")) {
            comboScore += 10
            reasons.add(RiskReason("Sideloaded SMS Access", "Sideloaded app with SMS access â€” major red flag for OTP theft", RiskLevel.CRITICAL, PermissionCategory.SMS))
        }

        // Rule 7: Notification listener + Internet (OTP exfil)
        if (specialAccess.hasNotificationListener && hasInternet) {
            comboScore += 8
            reasons.add(RiskReason("OTP Exfiltration Risk", "Can read notification OTPs and send them over the internet", RiskLevel.HIGH, PermissionCategory.SPECIAL_ACCESS))
        }
        
        // Rule 8: Background location + Internet (stalking)
        if ("android.permission.ACCESS_BACKGROUND_LOCATION" in permNames && hasInternet) {
            comboScore += 8
            reasons.add(RiskReason("Background Tracking", "Tracks your location in the background and can send it over the internet", RiskLevel.HIGH, PermissionCategory.LOCATION))
        }

        // Context mismatch detection (Should Have #08)
        val nameLower = appName.lowercase()
        val isSuspiciousCategory = nameLower.contains("torch") || nameLower.contains("flashlight") || 
            nameLower.contains("calculator") || nameLower.contains("wallpaper") || 
            nameLower.contains("weather") || nameLower.contains("clock") || 
            nameLower.contains("compass") || nameLower.contains("note") || 
            nameLower.contains("qr") || nameLower.contains("scanner") || 
            nameLower.contains("cleaner") || nameLower.contains("booster") ||
            nameLower.contains("battery saver") || nameLower.contains("ram")
        
        if (isSuspiciousCategory) {
            val hasSensitive = hasSms || hasContacts || hasCallLog || hasCamera || hasMic
            if (hasSensitive) {
                comboScore += 15
                reasons.add(RiskReason("Context Mismatch", "A '${appName}' app should not need SMS, contacts, camera, or microphone access", RiskLevel.CRITICAL, PermissionCategory.SPECIAL_ACCESS))
            }
        }

        return comboScore
    }
}

