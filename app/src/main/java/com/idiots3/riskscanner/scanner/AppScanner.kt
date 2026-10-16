package com.idiots3.riskscanner.scanner

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.idiots3.riskscanner.engine.PermissionMapper
import com.idiots3.riskscanner.engine.RiskScorer
import com.idiots3.riskscanner.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class AppScanner(
    private val context: Context
) {
    private val packageManager = context.packageManager

    suspend fun scanAllApps(includeSystemApps: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS).toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(
                PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
            )
        }

        val accessibilityServices = getEnabledAccessibilityServices()
        val deviceAdmins = getActiveDeviceAdmins()
        val notificationListeners = getEnabledNotificationListeners()

        packages.mapNotNull { packageInfo ->
            val isSystem = (packageInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
            if (!includeSystemApps && isSystem) return@mapNotNull null
            
            try {
                buildAppInfo(packageInfo, isSystem, accessibilityServices, deviceAdmins, notificationListeners)
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.riskScore }
    }

    private fun buildAppInfo(
        packageInfo: PackageInfo,
        isSystem: Boolean,
        accessibilityServices: Set<String>,
        deviceAdmins: Set<String>,
        notificationListeners: Set<String>
    ): AppInfo {
        val appInfo = packageInfo.applicationInfo
        val packageName = packageInfo.packageName

        // Extract permissions
        val permissions = extractPermissions(packageInfo)
        
        // Detect special access
        val specialAccess = detectSpecialAccess(
            packageInfo, packageName, accessibilityServices, deviceAdmins, notificationListeners
        )

        // Categorize permissions
        val categories = permissions.groupBy { it.category }

        // Get installer source
        val installerSource = getInstallerSource(packageName)

        // Get app name
        val appName = appInfo?.loadLabel(packageManager)?.toString() ?: packageName

        // Calculate risk
        val scoringResult = RiskScorer.calculateRisk(permissions, specialAccess, installerSource, appName)

        return AppInfo(
            appName = appName,
            packageName = packageName,
            versionName = packageInfo.versionName ?: "Unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong(),
            installDate = packageInfo.firstInstallTime,
            updateDate = packageInfo.lastUpdateTime,
            installerSource = installerSource,
            targetSdk = appInfo?.targetSdkVersion ?: 0,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo?.minSdkVersion ?: 0 else 0,
            icon = try { appInfo?.loadIcon(packageManager) } catch (e: Exception) { null },
            isSystemApp = isSystem,
            apkPath = appInfo?.sourceDir ?: "",
            permissions = permissions,
            specialAccess = specialAccess,
            riskScore = scoringResult.score,
            riskLevel = scoringResult.level,
            riskReasons = scoringResult.reasons,
            categories = categories
        )
    }

    private fun extractPermissions(packageInfo: PackageInfo): List<PermissionDetail> {
        val requestedPermissions = packageInfo.requestedPermissions ?: return emptyList()
        val requestedPermissionsFlags = packageInfo.requestedPermissionsFlags ?: IntArray(0)

        return requestedPermissions.mapIndexed { index, permissionName ->
            val isGranted = if (index < requestedPermissionsFlags.size) {
                (requestedPermissionsFlags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            } else false

            val mappedInfo = PermissionMapper.getPermissionInfo(permissionName)
            val protectionLevel = mappedInfo?.protectionLevel ?: getProtectionLevelFromSystem(permissionName)

            PermissionDetail(
                name = permissionName,
                shortName = permissionName.substringAfterLast('.'),
                isDeclared = true,
                isGranted = isGranted,
                protectionLevel = protectionLevel,
                category = mappedInfo?.category ?: guessCategory(permissionName),
                description = mappedInfo?.description ?: "Permission: ${permissionName.substringAfterLast('.')}"
            )
        }
    }

    private fun getProtectionLevelFromSystem(permissionName: String): ProtectionLevel {
        return try {
            val permInfo = packageManager.getPermissionInfo(permissionName, 0)
            when (permInfo.protection) {
                android.content.pm.PermissionInfo.PROTECTION_DANGEROUS -> ProtectionLevel.DANGEROUS
                android.content.pm.PermissionInfo.PROTECTION_SIGNATURE -> ProtectionLevel.SIGNATURE
                else -> ProtectionLevel.NORMAL
            }
        } catch (e: Exception) {
            ProtectionLevel.NORMAL
        }
    }

    private fun guessCategory(permissionName: String): PermissionCategory {
        val name = permissionName.uppercase()
        return when {
            name.contains("SMS") || name.contains("MMS") -> PermissionCategory.SMS
            name.contains("CONTACT") || name.contains("CALL_LOG") -> PermissionCategory.CONTACTS_CALL_LOGS
            name.contains("CAMERA") || name.contains("AUDIO") || name.contains("MICROPHONE") -> PermissionCategory.CAMERA_MICROPHONE
            name.contains("LOCATION") -> PermissionCategory.LOCATION
            name.contains("PHONE") || name.contains("CALL") -> PermissionCategory.PHONE_IDENTITY
            name.contains("STORAGE") || name.contains("MEDIA") || name.contains("EXTERNAL") -> PermissionCategory.STORAGE_MEDIA
            name.contains("BOOT") || name.contains("FOREGROUND") || name.contains("WAKE") || name.contains("BATTERY") -> PermissionCategory.BACKGROUND_PERSISTENCE
            name.contains("INTERNET") || name.contains("NETWORK") || name.contains("WIFI") || name.contains("BLUETOOTH") -> PermissionCategory.NETWORK
            name.contains("ACCESSIBILITY") || name.contains("OVERLAY") || name.contains("ADMIN") || name.contains("INSTALL_PACKAGES") || name.contains("NOTIFICATION_LISTENER") || name.contains("USAGE_STATS") -> PermissionCategory.SPECIAL_ACCESS
            else -> PermissionCategory.NETWORK
        }
    }

    private fun detectSpecialAccess(
        packageInfo: PackageInfo,
        packageName: String,
        accessibilityServices: Set<String>,
        deviceAdmins: Set<String>,
        notificationListeners: Set<String>
    ): SpecialAccessInfo {
        val requestedPerms = packageInfo.requestedPermissions?.toSet() ?: emptySet()
        val activeSpecial = mutableListOf<String>()
        
        val hasAccessibility = accessibilityServices.any { it.startsWith("$packageName/") }
        if (hasAccessibility) activeSpecial.add("Accessibility Service")
        
        val hasDeviceAdmin = deviceAdmins.contains(packageName)
        if (hasDeviceAdmin) activeSpecial.add("Device Administrator")
        
        val hasNotificationListener = notificationListeners.any { it.startsWith("$packageName/") }
        if (hasNotificationListener) activeSpecial.add("Notification Listener")
        
        val hasOverlay = "android.permission.SYSTEM_ALERT_WINDOW" in requestedPerms
        if (hasOverlay) activeSpecial.add("Screen Overlay")
        
        val hasInstall = "android.permission.REQUEST_INSTALL_PACKAGES" in requestedPerms
        if (hasInstall) activeSpecial.add("Install Packages")
        
        val hasUsageStats = "android.permission.PACKAGE_USAGE_STATS" in requestedPerms
        if (hasUsageStats) activeSpecial.add("Usage Statistics")

        // Check services for accessibility declarations
        val hasAccessibilityDeclared = packageInfo.services?.any { service ->
            service.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE"
        } ?: false
        if (hasAccessibilityDeclared && !hasAccessibility) activeSpecial.add("Accessibility Service (declared)")

        return SpecialAccessInfo(
            hasAccessibilityService = hasAccessibility || hasAccessibilityDeclared,
            hasDeviceAdmin = hasDeviceAdmin,
            hasNotificationListener = hasNotificationListener,
            hasOverlayPermission = hasOverlay,
            hasInstallPackages = hasInstall,
            hasUsageStats = hasUsageStats,
            activeSpecialAccess = activeSpecial
        )
    }

    private fun getInstallerSource(packageName: String): String {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
            when (installer) {
                "com.android.vending" -> "Google Play Store"
                "com.google.android.packageinstaller" -> "Google Play Store"
                "com.amazon.venezia" -> "Amazon App Store"
                "com.huawei.appmarket" -> "Huawei AppGallery"
                "com.samsung.android.vending" -> "Samsung Galaxy Store"
                "com.sec.android.app.samsungapps" -> "Samsung Galaxy Store"
                "org.fdroid.fdroid" -> "F-Droid"
                null -> "Sideloaded"
                else -> installer
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getEnabledAccessibilityServices(): Set<String> {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNull { it.resolveInfo?.serviceInfo?.let { si -> "${si.packageName}/${si.name}" } }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun getActiveDeviceAdmins(): Set<String> {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun getEnabledNotificationListeners(): Set<String> {
        return try {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return emptySet()
            flat.split(":").filter { it.isNotBlank() }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun computeApkHash(apkPath: String): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            File(apkPath).inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}

