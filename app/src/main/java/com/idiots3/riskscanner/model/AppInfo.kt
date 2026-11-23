package com.idiots3.riskscanner.model

/**
 * Honest verification state for AppOps-backed special access permissions.
 * On Android 10+, third-party apps cannot reliably read another app's AppOps state,
 * so declared privileges that cannot be verified are marked [DECLARED_UNVERIFIED]
 * rather than falsely reporting them as denied.
 */
enum class SpecialAccessGrantState(val label: String) {
    ENABLED("Enabled (verified)"),
    DECLARED_UNVERIFIED("Declared — verify in Settings"),
    DECLARED_DENIED("Declared — not granted"),
    NOT_DECLARED("Not requested")
}

/**
 * Structured install origin for an application.
 */
enum class InstallProvenance(val label: String, val isSideloaded: Boolean) {
    OFFICIAL_STORE("Official store", false),
    ALTERNATIVE_STORE("App store", false),
    PREINSTALLED_SYSTEM("Pre-installed", false),
    DEVICE_MIGRATION("Transferred / Restored", false),
    BROWSER_OR_APP_SIDELOAD("Sideloaded via app", true),
    PACKAGE_INSTALLER("Sideloaded APK", true),
    ADB_OR_SHELL("Installed via ADB", true),
    UNKNOWN("Unknown source", true)
}

/**
 * Semantic origin of a [RiskReason], used by the UI instead of brittle title matching.
 */
enum class ReasonKind {
    CATEGORY,
    SPECIAL_ACCESS,
    COMBO_RULE,
    PROVENANCE,
    STATIC,
    IMPERSONATION,
    TRUSTED_CONTEXT,
    AI
}

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
    val categories: Map<PermissionCategory, List<PermissionDetail>> = emptyMap(),
    val installerPackage: String? = null,
    val initiatingPackage: String? = null,
    val installProvenance: InstallProvenance = inferProvenance(installerSource, isSystemApp),
    val isUpdatedSystemApp: Boolean = false,
    val isDefaultSmsApp: Boolean = false,
    val isDefaultDialer: Boolean = false,
    val isEnabledInputMethod: Boolean = false,
    val isSelfApp: Boolean = false,
    val isDefaultBrowser: Boolean = false,
    val isDefaultLauncher: Boolean = false,
    val isActiveInputMethod: Boolean = false,
    val isAutofillService: Boolean = false,
    val isDefaultAssistant: Boolean = false,
    val isDebuggable: Boolean = false,
    val isTestOnly: Boolean = false,
    val allowsCleartextTraffic: Boolean = false,
    val signingCertSha256: String? = null,
    val isDebugSigned: Boolean = false,
    val isWeakSigningKey: Boolean = false,
    val isCertExpired: Boolean = false,
    val verifiedKnownSafeName: String? = null,
    val isUserTrusted: Boolean = false,
    val impersonationTarget: String? = null
) {
    /** Sensitive = dangerous or special protection level. */
    val grantedSensitive: List<PermissionDetail>
        get() = permissions.filter { it.isSensitive && it.isGranted }

    val isSideloaded: Boolean
        get() = !isSystemApp && installProvenance.isSideloaded

    fun permission(name: String): PermissionDetail? = permissions.firstOrNull { it.name == name }

    fun hasDeclared(name: String): Boolean = permissions.any { it.name == name }

    fun hasGranted(name: String): Boolean = permissions.any { it.name == name && it.isGranted }

    fun hasGrantedAny(names: Collection<String>): Boolean =
        permissions.any { it.isGranted && names.contains(it.name) }

    /** The on-device context model only needs to run for apps that actually hold sensitive access. */
    fun needsAiAnalysis(): Boolean = grantedSensitive.isNotEmpty() && !isSelfApp

    companion object {
        fun inferProvenance(installerSource: String, isSystemApp: Boolean): InstallProvenance {
            if (isSystemApp && (
                    installerSource.equals("Unknown", ignoreCase = true) ||
                        installerSource.contains("Pre-installed", ignoreCase = true) ||
                        installerSource.equals("Sideloaded", ignoreCase = true)
                    )
            ) {
                return InstallProvenance.PREINSTALLED_SYSTEM
            }
            return when {
                installerSource.contains("Google Play", ignoreCase = true) ||
                    installerSource.contains("Galaxy Store", ignoreCase = true) ||
                    installerSource.contains("Amazon", ignoreCase = true) ||
                    installerSource.contains("Huawei", ignoreCase = true) ||
                    installerSource.contains("Xiaomi", ignoreCase = true) ||
                    installerSource.contains("GetApps", ignoreCase = true) ||
                    installerSource.contains("App Market", ignoreCase = true) ||
                    installerSource.contains("Vivo", ignoreCase = true) ||
                    installerSource.contains("GrapheneOS", ignoreCase = true) -> InstallProvenance.OFFICIAL_STORE

                installerSource.contains("F-Droid", ignoreCase = true) ||
                    installerSource.contains("Droid-ify", ignoreCase = true) ||
                    installerSource.contains("Neo Store", ignoreCase = true) ||
                    installerSource.contains("Aurora", ignoreCase = true) ||
                    installerSource.contains("Obtainium", ignoreCase = true) ||
                    installerSource.contains("Accrescent", ignoreCase = true) ||
                    installerSource.contains("App Lounge", ignoreCase = true) -> InstallProvenance.ALTERNATIVE_STORE

                installerSource.contains("Transferred", ignoreCase = true) ||
                    installerSource.contains("Restored", ignoreCase = true) ||
                    installerSource.contains("Smart Switch", ignoreCase = true) -> InstallProvenance.DEVICE_MIGRATION

                installerSource.contains("ADB", ignoreCase = true) -> InstallProvenance.ADB_OR_SHELL
                installerSource.contains("via ", ignoreCase = true) -> InstallProvenance.BROWSER_OR_APP_SIDELOAD
                installerSource.contains("APK", ignoreCase = true) ||
                    installerSource.equals("Sideloaded", ignoreCase = true) -> InstallProvenance.PACKAGE_INSTALLER

                else -> InstallProvenance.UNKNOWN
            }
        }
    }
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
    val deviceAdminDeclared: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val notificationListenerDeclared: Boolean = false,
    val overlayEnabled: Boolean = false,
    val overlayDeclared: Boolean = false,
    val overlayState: SpecialAccessGrantState = deriveState(overlayEnabled, overlayDeclared),
    val installPackagesEnabled: Boolean = false,
    val installPackagesDeclared: Boolean = false,
    val installPackagesState: SpecialAccessGrantState = deriveState(installPackagesEnabled, installPackagesDeclared),
    val usageStatsEnabled: Boolean = false,
    val usageStatsDeclared: Boolean = false,
    val usageStatsState: SpecialAccessGrantState = deriveState(usageStatsEnabled, usageStatsDeclared),
    val allFilesAccessDeclared: Boolean = false,
    val allFilesAccessState: SpecialAccessGrantState = deriveState(false, allFilesAccessDeclared),
    val vpnServiceDeclared: Boolean = false,
    val nfcHceDeclared: Boolean = false,
    val screenCaptureDeclared: Boolean = false
) {
    val enabledLabels: List<String>
        get() = buildList {
            if (accessibilityEnabled) add("Accessibility Service")
            if (deviceAdminActive) add("Device Administrator")
            if (notificationListenerEnabled) add("Notification Listener")
            if (overlayEnabled || overlayState == SpecialAccessGrantState.ENABLED) add("Screen Overlay")
            if (installPackagesEnabled || installPackagesState == SpecialAccessGrantState.ENABLED) add("Install Packages")
            if (usageStatsEnabled || usageStatsState == SpecialAccessGrantState.ENABLED) add("Usage Statistics")
            if (allFilesAccessState == SpecialAccessGrantState.ENABLED) add("All Files Access")
        }

    val declaredOnlyLabels: List<String>
        get() = buildList {
            if (accessibilityDeclared && !accessibilityEnabled) add("Accessibility Service")
            if (deviceAdminDeclared && !deviceAdminActive) add("Device Administrator")
            if (notificationListenerDeclared && !notificationListenerEnabled) add("Notification Listener")
            if (overlayDeclared && !overlayEnabled && overlayState != SpecialAccessGrantState.ENABLED) add("Screen Overlay")
            if (installPackagesDeclared && !installPackagesEnabled && installPackagesState != SpecialAccessGrantState.ENABLED) add("Install Packages")
            if (usageStatsDeclared && !usageStatsEnabled && usageStatsState != SpecialAccessGrantState.ENABLED) add("Usage Statistics")
            if (allFilesAccessDeclared && allFilesAccessState != SpecialAccessGrantState.ENABLED) add("All Files Access")
            if (vpnServiceDeclared) add("VPN Service")
            if (nfcHceDeclared) add("NFC Card Emulation")
            if (screenCaptureDeclared) add("Screen Capture")
        }

    val activeCount: Int get() = enabledLabels.size

    val hasAny: Boolean get() = enabledLabels.isNotEmpty() || declaredOnlyLabels.isNotEmpty()

    companion object {
        fun deriveState(enabled: Boolean, declared: Boolean): SpecialAccessGrantState = when {
            enabled -> SpecialAccessGrantState.ENABLED
            declared -> SpecialAccessGrantState.DECLARED_UNVERIFIED
            else -> SpecialAccessGrantState.NOT_DECLARED
        }
    }
}

data class RiskReason(
    val title: String,
    val description: String,
    val severity: RiskLevel,
    val category: PermissionCategory,
    val kind: ReasonKind = ReasonKind.CATEGORY
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
