package com.idiots3.riskscanner.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val installDate: Long,
    val updateDate: Long,
    val installerSource: String, // play store, sideloaded, etc.
    val targetSdk: Int,
    val minSdk: Int,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val apkPath: String,
    val permissions: List<PermissionDetail>,
    val specialAccess: SpecialAccessInfo,
    val riskScore: Int = 0,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val riskReasons: List<RiskReason> = emptyList(),
    val categories: Map<PermissionCategory, List<PermissionDetail>> = emptyMap()
)

data class PermissionDetail(
    val name: String,
    val shortName: String, // without android.permission. prefix
    val isDeclared: Boolean,
    val isGranted: Boolean,
    val protectionLevel: ProtectionLevel,
    val category: PermissionCategory,
    val description: String
)

data class SpecialAccessInfo(
    val hasAccessibilityService: Boolean = false,
    val hasDeviceAdmin: Boolean = false,
    val hasNotificationListener: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasInstallPackages: Boolean = false,
    val hasUsageStats: Boolean = false,
    val activeSpecialAccess: List<String> = emptyList()
)

data class RiskReason(
    val title: String,
    val description: String,
    val severity: RiskLevel,
    val category: PermissionCategory
)

enum class RiskLevel(val label: String, val minScore: Int, val maxScore: Int) {
    LOW("Low", 0, 25),
    MEDIUM("Medium", 26, 50),
    HIGH("High", 51, 75),
    CRITICAL("Critical", 76, 100)
}

enum class ProtectionLevel(val label: String) {
    NORMAL("Normal"),
    DANGEROUS("Dangerous"),
    SIGNATURE("Signature"),
    SPECIAL("Special")
}

enum class PermissionCategory(
    val label: String,
    val description: String,
    val baseWeight: Int,
    val icon: String  // Material icon name for reference
) {
    SPECIAL_ACCESS("Special Access", "Screen reading, fake login overlays, uninstall blocking, OTP capture", 25, "security"),
    SMS("SMS", "OTP theft, premium SMS fraud", 15, "sms"),
    CONTACTS_CALL_LOGS("Contacts & Call Logs", "Harvesting social graph for scams and extortion", 12, "contacts_phone"),
    CAMERA_MICROPHONE("Camera & Microphone", "Covert recording", 14, "videocam"),
    LOCATION("Location", "Tracking and stalking", 12, "location_on"),
    PHONE_IDENTITY("Phone & Identity", "Device fingerprinting, call fraud", 10, "phone_android"),
    STORAGE_MEDIA("Storage & Media", "Photo and document theft", 8, "folder"),
    BACKGROUND_PERSISTENCE("Background & Persistence", "Survives reboots and keeps running unseen", 8, "sync"),
    NETWORK("Network", "The exfiltration channel; low risk alone, high in combination", 3, "wifi")
}

