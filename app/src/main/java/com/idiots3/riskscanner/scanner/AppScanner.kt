package com.idiots3.riskscanner.scanner

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.idiots3.riskscanner.engine.PermissionMapper
import com.idiots3.riskscanner.engine.RiskScorer
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.PermissionDetail
import com.idiots3.riskscanner.model.ProtectionLevel
import com.idiots3.riskscanner.model.SpecialAccessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the installed-app inventory and evaluates every app with the deterministic
 * rule engine. Everything runs on-device; no network calls are made anywhere in this class.
 */
class AppScanner(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun scanAllApps(includeSystemApps: Boolean = false): List<AppInfo> =
        withContext(Dispatchers.IO) {
            // Only the flags we actually need: keeps parsing fast and memory flat.
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
            val packages: List<PackageInfo> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledPackages(flags)
                }

            val registry = SpecialAccessRegistry(context)
            val result = ArrayList<AppInfo>(packages.size)

            for (packageInfo in packages) {
                val app = packageInfo.applicationInfo ?: continue
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystemApps && isSystem) continue
                val info = try {
                    buildAppInfo(packageInfo, app, isSystem, registry)
                } catch (t: Throwable) {
                    null
                }
                if (info != null) result.add(info)
            }

            result.sortByDescending { it.riskScore }
            result
        }

    private fun buildAppInfo(
        packageInfo: PackageInfo,
        app: ApplicationInfo,
        isSystem: Boolean,
        registry: SpecialAccessRegistry
    ): AppInfo {
        val packageName = packageInfo.packageName
        val permissions = extractPermissions(packageInfo)
        val specialAccess = detectSpecialAccess(packageInfo, app, registry)

        val base = AppInfo(
            appName = app.loadLabel(packageManager).toString(),
            packageName = packageName,
            versionName = packageInfo.versionName ?: "?",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
            },
            installDate = packageInfo.firstInstallTime,
            updateDate = packageInfo.lastUpdateTime,
            installerSource = getInstallerSource(packageName),
            targetSdk = app.targetSdkVersion,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) app.minSdkVersion else 0,
            isSystemApp = isSystem,
            hasLauncherActivity = hasLauncherActivity(packageName),
            androidCategory = categoryLabel(app.category),
            apkPath = app.sourceDir ?: "",
            permissions = permissions,
            specialAccess = specialAccess,
            categories = permissions.groupBy { it.category }
        )

        val result = RiskScorer.score(base, null)
        return base.copy(
            ruleScore = result.ruleScore,
            riskScore = result.score,
            riskLevel = result.level,
            riskReasons = result.reasons,
            scoreBreakdown = result.breakdown
        )
    }

    private fun hasLauncherActivity(packageName: String): Boolean = try {
        packageManager.getLaunchIntentForPackage(packageName) != null
    } catch (t: Throwable) {
        true // assume visible when unsure; "hidden" is an accusation that needs evidence
    }

    private fun categoryLabel(category: Int): String? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "Game"
        ApplicationInfo.CATEGORY_AUDIO -> "Audio"
        ApplicationInfo.CATEGORY_VIDEO -> "Video"
        ApplicationInfo.CATEGORY_IMAGE -> "Image"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_NEWS -> "News"
        ApplicationInfo.CATEGORY_MAPS -> "Maps"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
        else -> null
    }

    private fun extractPermissions(packageInfo: PackageInfo): List<PermissionDetail> {
        val requested = packageInfo.requestedPermissions ?: return emptyList()
        val flags = packageInfo.requestedPermissionsFlags ?: IntArray(0)

        return requested.mapIndexed { index, name ->
            val granted = index < flags.size &&
                (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            val mapped = PermissionMapper.getPermissionInfo(name)
            PermissionDetail(
                name = name,
                shortName = name.substringAfterLast('.'),
                isDeclared = true,
                isGranted = granted,
                protectionLevel = mapped?.protectionLevel ?: protectionLevelFromSystem(name),
                category = mapped?.category ?: PermissionMapper.guessCategory(name),
                description = mapped?.description ?: PermissionMapper.getDescription(name)
            )
        }
    }

    private fun protectionLevelFromSystem(permissionName: String): ProtectionLevel = try {
        when (packageManager.getPermissionInfo(permissionName, 0).protection) {
            android.content.pm.PermissionInfo.PROTECTION_DANGEROUS -> ProtectionLevel.DANGEROUS
            android.content.pm.PermissionInfo.PROTECTION_SIGNATURE -> ProtectionLevel.SIGNATURE
            else -> ProtectionLevel.NORMAL
        }
    } catch (t: Throwable) {
        ProtectionLevel.NORMAL
    }

    private fun detectSpecialAccess(
        packageInfo: PackageInfo,
        app: ApplicationInfo,
        registry: SpecialAccessRegistry
    ): SpecialAccessInfo {
        val packageName = packageInfo.packageName
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()
        val services = packageInfo.services.orEmpty()

        return SpecialAccessInfo(
            accessibilityEnabled = registry.accessibilityEnabled.contains(packageName),
            accessibilityDeclared = services.any {
                it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE"
            },
            deviceAdminActive = registry.deviceAdmins.contains(packageName),
            notificationListenerEnabled = registry.notificationListeners.contains(packageName),
            notificationListenerDeclared = services.any {
                it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            },
            overlayEnabled = registry.appOpsAllowed(
                AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, app.uid, packageName
            ),
            overlayDeclared = requested.contains("android.permission.SYSTEM_ALERT_WINDOW"),
            installPackagesEnabled = registry.appOpsAllowed(
                AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES, app.uid, packageName
            ),
            installPackagesDeclared = requested.contains("android.permission.REQUEST_INSTALL_PACKAGES"),
            usageStatsEnabled = registry.appOpsAllowed(
                AppOpsManager.OPSTR_GET_USAGE_STATS, app.uid, packageName
            ),
            usageStatsDeclared = requested.contains("android.permission.PACKAGE_USAGE_STATS")
        )
    }

    private fun getInstallerSource(packageName: String): String = try {
        val installing: String?
        val initiating: String?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val source = packageManager.getInstallSourceInfo(packageName)
            installing = source.installingPackageName
            initiating = source.initiatingPackageName
        } else {
            @Suppress("DEPRECATION")
            installing = packageManager.getInstallerPackageName(packageName)
            initiating = null
        }
        val known = listOfNotNull(installing, initiating).firstNotNullOfOrNull { storeName(it) }
        known ?: "Sideloaded"
    } catch (t: Throwable) {
        "Unknown"
    }

    private fun storeName(installer: String?): String? = when (installer) {
        "com.android.vending" -> "Google Play Store"
        "com.amazon.venezia" -> "Amazon App Store"
        "com.huawei.appmarket" -> "Huawei AppGallery"
        "com.samsung.android.vending", "com.sec.android.app.samsungapps" -> "Samsung Galaxy Store"
        "org.fdroid.fdroid" -> "F-Droid"
        "com.aurora.store" -> "Aurora Store"
        "com.google.android.packageinstaller", "com.android.packageinstaller" -> null // APK sideload
        else -> null
    }
}

/**
 * Resolves "special access" grants that live outside the normal permission model.
 * Computed once per scan and reused for every app.
 */
class SpecialAccessRegistry(context: Context) {

    private val appOps: AppOpsManager? =
        context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

    val accessibilityEnabled: Set<String> = loadAccessibility(context)
    val notificationListeners: Set<String> = loadNotificationListeners(context)
    val deviceAdmins: Set<String> = loadDeviceAdmins(context)

    fun appOpsAllowed(op: String, uid: Int, packageName: String): Boolean {
        val manager = appOps ?: return false
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                manager.unsafeCheckOpNoThrow(op, uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                manager.checkOpNoThrow(op, uid, packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (t: Throwable) {
            false
        }
    }

    private fun loadAccessibility(context: Context): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            val raw = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            raw?.split(':')?.forEach { entry ->
                entry.substringBeforeLast('/').trim().takeIf { it.isNotEmpty() }?.let { packages.add(it) }
            }
        } catch (_: Throwable) {
        }
        try {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.forEach { info ->
                    info.resolveInfo?.serviceInfo?.packageName?.let { packages.add(it) }
                }
        } catch (_: Throwable) {
        }
        return packages
    }

    private fun loadNotificationListeners(context: Context): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.getEnabledListenerPackages(context)?.forEach { packages.add(it) }
            }
        } catch (_: Throwable) {
        }
        if (packages.isEmpty()) {
            try {
                val raw = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )
                raw?.split(':')?.forEach { entry ->
                    entry.substringBeforeLast('/').trim().takeIf { it.isNotEmpty() }?.let { packages.add(it) }
                }
            } catch (_: Throwable) {
            }
        }
        return packages
    }

    private fun loadDeviceAdmins(context: Context): Set<String> = try {
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        manager?.activeAdmins?.map { it.packageName }?.toSet().orEmpty()
    } catch (t: Throwable) {
        emptySet()
    }
}
