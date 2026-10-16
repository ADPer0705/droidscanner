package com.idiots3.riskscanner.model

/**
 * Everything the auditor knows about a single installed application.
 *
 * NOTE: the app icon is deliberately NOT stored here. Icons are loaded lazily and
 * kept in a small LRU cache by the UI layer so that scanning 200+ apps does not
 * hold hundreds of Drawables in memory.
 */
data class AppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val installDate: Long,
    val updateDate: Long,
    val installerSource: String,
    val targetSdk: Int,
    val minSdk: Int,
    val isSystemApp: Boolean,
    val hasLauncherActivity: Boolean,
    val androidCategory: String?,
    val apkPath: String,
    val permissions: List<PermissionDetail>,
    val specialAccess: SpecialAccessInfo,
    val ruleScore: Int = 0,
    val aiDelta: Int = 0,
    val riskScore: Int = 0,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val riskReasons: List<RiskReason> = emptyList(),
    val scoreBreakdown: List<ScoreLine> = emptyList(),
    val aiAssessment: AiAssessment? = null,
    val categories: Map<PermissionCategory, List<PermissionDetail>> = emptyMap()
) {
    /** Sensitive = dangerous or special protection level. */
    val grantedSensitive: List<PermissionDetail>
        get() = permissions.filter { it.isSensitive && it.isGranted }

    val isSideloaded: Boolean
        get() = installerSource.equals("Sideloaded", ignoreCase = true) ||
            installerSource.equals("Unknown", ignoreCase = true)

    fun permission(name: String): PermissionDetail? = permissions.firstOrNull { it.name == name }

    fun hasDeclared(name: String): Boolean = permissions.any { it.name == name }

    fun hasGranted(name: String): Boolean = permissions.any { it.name == name && it.isGranted }

    fun hasGrantedAny(names: Collection<String>): Boolean =
        permissions.any { it.isGranted && names.contains(it.name) }

    /** The on-device context model only needs to run for apps that actually hold sensitive access. */
    fun needsAiAnalysis(): Boolean = grantedSensitive.isNotEmpty()
}

data class PermissionDetail(
    val name: String,
    val shortName: String, // without android.permission. prefix
    val isDeclared: Boolean,
    val isGranted: Boolean,
    val protectionLevel: ProtectionLevel,
    val category: PermissionCategory,
    val description: String
) {
    val isSensitive: Boolean
        get() = protectionLevel == ProtectionLevel.DANGEROUS || protectionLevel == ProtectionLevel.SPECIAL
}

/** Special access is granted from separate system settings screens — highest risk weight. */
data class SpecialAccessInfo(
    val accessibilityEnabled: Boolean = false,
    val accessibilityDeclared: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val notificationListenerDeclared: Boolean = false,
    val overlayEnabled: Boolean = false,
    val overlayDeclared: Boolean = false,
    val installPackagesEnabled: Boolean = false,
    val installPackagesDeclared: Boolean = false,
    val usageStatsEnabled: Boolean = false,
    val usageStatsDeclared: Boolean = false
) {
    val enabledLabels: List<String>
        get() = buildList {
            if (accessibilityEnabled) add("Accessibility Service")
            if (deviceAdminActive) add("Device Administrator")
            if (notificationListenerEnabled) add("Notification Listener")
            if (overlayEnabled) add("Screen Overlay")
            if (installPackagesEnabled) add("Install Packages")
            if (usageStatsEnabled) add("Usage Statistics")
        }

    val declaredOnlyLabels: List<String>
        get() = buildList {
            if (accessibilityDeclared && !accessibilityEnabled) add("Accessibility Service")
            if (notificationListenerDeclared && !notificationListenerEnabled) add("Notification Listener")
            if (overlayDeclared && !overlayEnabled) add("Screen Overlay")
            if (installPackagesDeclared && !installPackagesEnabled) add("Install Packages")
            if (usageStatsDeclared && !usageStatsEnabled) add("Usage Statistics")
        }

    val activeCount: Int get() = enabledLabels.size

    val hasAny: Boolean get() = enabledLabels.isNotEmpty() || declaredOnlyLabels.isNotEmpty()
}

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

/**
 * The nine mandatory permission categories from the problem statement, plus an
 * "Other" bucket so unmapped permissions stay visible without being scored.
 */
enum class PermissionCategory(
    val label: String,
    val description: String,
    val icon: String
) {
    SPECIAL_ACCESS(
        "Special Access",
        "Screen reading, fake login overlays, uninstall blocking, OTP capture",
        "security"
    ),
    SMS("SMS", "OTP theft, premium SMS fraud", "sms"),
    CONTACTS_CALL_LOGS(
        "Contacts & Call Logs",
        "Harvesting social graph for scams and extortion",
        "contacts_phone"
    ),
    CAMERA_MICROPHONE("Camera & Microphone", "Covert recording", "videocam"),
    LOCATION("Location", "Tracking and stalking", "location_on"),
    PHONE_IDENTITY("Phone & Identity", "Device fingerprinting, call fraud", "phone_android"),
    STORAGE_MEDIA("Storage & Media", "Photo and document theft", "folder"),
    BACKGROUND_PERSISTENCE(
        "Background & Persistence",
        "Survives reboots and keeps running unseen",
        "sync"
    ),
    NETWORK("Network", "The exfiltration channel; low risk alone, high in combination", "wifi"),
    OTHER("Other", "Unclassified permissions (shown, not scored)", "help")
}
