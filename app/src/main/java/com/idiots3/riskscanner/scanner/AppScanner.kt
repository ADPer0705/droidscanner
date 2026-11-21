package com.idiots3.riskscanner.scanner

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationManagerCompat
import com.idiots3.riskscanner.engine.PermissionMapper
import com.idiots3.riskscanner.engine.RiskScorer
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.InstallProvenance
import com.idiots3.riskscanner.model.PermissionDetail
import com.idiots3.riskscanner.model.ProtectionLevel
import com.idiots3.riskscanner.model.SpecialAccessGrantState
import com.idiots3.riskscanner.model.SpecialAccessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the installed-app inventory and evaluates every app with the deterministic
 * rule engine. Everything runs on-device using only public, rootless Android APIs.
 */
class AppScanner(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun scanAllApps(includeSystemApps: Boolean = false): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS

            val packages: List<PackageInfo> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledPackages(flags)
                }

            val registry = SpecialAccessRegistry(context)
            val launcherPackages = loadLauncherPackages()
            val roles = DeviceRoles.resolve(context)
            val labelCache = HashMap<String, String>()
            val result = ArrayList<AppInfo>(packages.size)

            for (packageInfo in packages) {
                val app = packageInfo.applicationInfo ?: continue
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystemApps && isSystem) continue
                val info = try {
                    buildAppInfo(packageInfo, app, isSystem, registry, launcherPackages, roles, labelCache)
                } catch (_: Throwable) {
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
        registry: SpecialAccessRegistry,
        launcherPackages: Set<String>?,
        roles: DeviceRoles,
        labelCache: MutableMap<String, String>
    ): AppInfo {
        val packageName = packageInfo.packageName
        val permissions = extractPermissions(packageInfo)
        val specialAccess = detectSpecialAccess(packageInfo, app, registry)
        val provenance = resolveInstallProvenance(packageName, isSystem, labelCache)
        val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val hasLauncher = launcherPackages?.contains(packageName) ?: hasLauncherFallback(packageName)

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
            installerSource = provenance.displayLabel,
            targetSdk = app.targetSdkVersion,
            minSdk = app.minSdkVersion,
            isSystemApp = isSystem,
            hasLauncherActivity = hasLauncher,
            androidCategory = categoryLabel(app.category),
            apkPath = app.sourceDir ?: "",
            permissions = permissions,
            specialAccess = specialAccess,
            categories = permissions.groupBy { it.category },
            installerPackage = provenance.installingPackage,
            initiatingPackage = provenance.initiatingPackage,
            installProvenance = provenance.kind,
            isUpdatedSystemApp = isUpdatedSystem,
            isDefaultSmsApp = roles.defaultSms == packageName,
            isDefaultDialer = roles.defaultDialer == packageName,
            isEnabledInputMethod = roles.enabledImes.contains(packageName)
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

    private fun loadLauncherPackages(): Set<String>? = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        resolved.mapNotNull { it.activityInfo?.packageName }.toSet().takeIf { it.isNotEmpty() }
    } catch (_: Throwable) {
        null
    }

    private fun hasLauncherFallback(packageName: String): Boolean = try {
        packageManager.getLaunchIntentForPackage(packageName) != null
    } catch (_: Throwable) {
        true
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
        val info = packageManager.getPermissionInfo(permissionName, 0)
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.protection
        } else {
            @Suppress("DEPRECATION")
            info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE
        }
        when (base) {
            android.content.pm.PermissionInfo.PROTECTION_DANGEROUS -> ProtectionLevel.DANGEROUS
            android.content.pm.PermissionInfo.PROTECTION_SIGNATURE -> ProtectionLevel.SIGNATURE
            else -> ProtectionLevel.NORMAL
        }
    } catch (_: Throwable) {
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
        val receivers = packageInfo.receivers.orEmpty()

        val overlayDeclared = requested.contains("android.permission.SYSTEM_ALERT_WINDOW")
        val overlayState = registry.appOpsState(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, app.uid, packageName, overlayDeclared
        )

        val installDeclared = requested.contains("android.permission.REQUEST_INSTALL_PACKAGES")
        val installState = registry.appOpsState(
            SpecialAccessRegistry.OP_INSTALL_PACKAGES, app.uid, packageName, installDeclared
        )

        val usageDeclared = requested.contains("android.permission.PACKAGE_USAGE_STATS")
        val usageState = registry.appOpsState(
            AppOpsManager.OPSTR_GET_USAGE_STATS, app.uid, packageName, usageDeclared
        )

        return SpecialAccessInfo(
            accessibilityEnabled = registry.accessibilityEnabled.contains(packageName),
            accessibilityDeclared = services.any {
                it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE"
            },
            deviceAdminActive = registry.deviceAdmins.contains(packageName),
            deviceAdminDeclared = receivers.any {
                it.permission == "android.permission.BIND_DEVICE_ADMIN"
            },
            notificationListenerEnabled = registry.notificationListeners.contains(packageName),
            notificationListenerDeclared = services.any {
                it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            },
            overlayEnabled = overlayState == SpecialAccessGrantState.ENABLED,
            overlayDeclared = overlayDeclared,
            overlayState = overlayState,
            installPackagesEnabled = installState == SpecialAccessGrantState.ENABLED,
            installPackagesDeclared = installDeclared,
            installPackagesState = installState,
            usageStatsEnabled = usageState == SpecialAccessGrantState.ENABLED,
            usageStatsDeclared = usageDeclared,
            usageStatsState = usageState,
            allFilesAccessDeclared = requested.contains("android.permission.MANAGE_EXTERNAL_STORAGE"),
            vpnServiceDeclared = services.any {
                it.permission == "android.permission.BIND_VPN_SERVICE"
            },
            nfcHceDeclared = services.any {
                it.permission == "android.permission.BIND_NFC_SERVICE"
            }
        )
    }

    private data class ResolvedProvenance(
        val displayLabel: String,
        val kind: InstallProvenance,
        val installingPackage: String?,
        val initiatingPackage: String?
    )

    private fun resolveInstallProvenance(
        packageName: String,
        isSystem: Boolean,
        labelCache: MutableMap<String, String>
    ): ResolvedProvenance = try {
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

        val knownStore = listOfNotNull(installing, initiating).firstNotNullOfOrNull { knownStoreFor(it) }
        when {
            knownStore != null -> ResolvedProvenance(knownStore.first, knownStore.second, installing, initiating)
            isSystem && installing == null && initiating == null ->
                ResolvedProvenance("Pre-installed (System)", InstallProvenance.PREINSTALLED_SYSTEM, null, null)
            installing == SHELL_PKG || initiating == SHELL_PKG ->
                ResolvedProvenance("Installed via ADB", InstallProvenance.ADB_OR_SHELL, installing, initiating)
            initiating != null && !PACKAGE_INSTALLER_PKGS.contains(initiating) -> {
                val appLabel = resolveAppLabel(initiating, labelCache)
                ResolvedProvenance("Sideloaded via $appLabel", InstallProvenance.BROWSER_OR_APP_SIDELOAD, installing, initiating)
            }
            installing != null && PACKAGE_INSTALLER_PKGS.contains(installing) ->
                ResolvedProvenance("Sideloaded (APK)", InstallProvenance.PACKAGE_INSTALLER, installing, initiating)
            installing != null && !isSystem -> {
                val appLabel = resolveAppLabel(installing, labelCache)
                ResolvedProvenance("Sideloaded via $appLabel", InstallProvenance.BROWSER_OR_APP_SIDELOAD, installing, initiating)
            }
            isSystem ->
                ResolvedProvenance("Pre-installed (System)", InstallProvenance.PREINSTALLED_SYSTEM, installing, initiating)
            else ->
                ResolvedProvenance("Sideloaded", InstallProvenance.UNKNOWN, installing, initiating)
        }
    } catch (_: Throwable) {
        if (isSystem) {
            ResolvedProvenance("Pre-installed (System)", InstallProvenance.PREINSTALLED_SYSTEM, null, null)
        } else {
            ResolvedProvenance("Unknown", InstallProvenance.UNKNOWN, null, null)
        }
    }

    private fun resolveAppLabel(pkg: String, cache: MutableMap<String, String>): String {
        cache[pkg]?.let { return it }
        val label = runCatching {
            packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: pkg
        cache[pkg] = label
        return label
    }

    private fun knownStoreFor(installer: String?): Pair<String, InstallProvenance>? = when (installer) {
        "com.android.vending" -> "Google Play Store" to InstallProvenance.OFFICIAL_STORE
        "com.sec.android.app.samsungapps", "com.samsung.android.vending" ->
            "Samsung Galaxy Store" to InstallProvenance.OFFICIAL_STORE
        "com.amazon.venezia" -> "Amazon App Store" to InstallProvenance.OFFICIAL_STORE
        "com.huawei.appmarket" -> "Huawei AppGallery" to InstallProvenance.OFFICIAL_STORE
        "com.xiaomi.mipicks" -> "Xiaomi GetApps" to InstallProvenance.OFFICIAL_STORE
        "com.heytap.market", "com.oppo.market" -> "App Market" to InstallProvenance.OFFICIAL_STORE
        "com.vivo.appstore" -> "Vivo App Store" to InstallProvenance.OFFICIAL_STORE
        "app.grapheneos.apps" -> "GrapheneOS Apps" to InstallProvenance.OFFICIAL_STORE
        "org.fdroid.fdroid", "org.fdroid.fdroid.privileged" -> "F-Droid" to InstallProvenance.ALTERNATIVE_STORE
        "com.looker.droidify" -> "Droid-ify" to InstallProvenance.ALTERNATIVE_STORE
        "com.machiav3lli.fdroid" -> "Neo Store" to InstallProvenance.ALTERNATIVE_STORE
        "com.aurora.store" -> "Aurora Store" to InstallProvenance.ALTERNATIVE_STORE
        "dev.imranr.obtainium" -> "Obtainium" to InstallProvenance.ALTERNATIVE_STORE
        else -> null
    }

    private data class DeviceRoles(
        val defaultSms: String?,
        val defaultDialer: String?,
        val enabledImes: Set<String>
    ) {
        companion object {
            fun resolve(context: Context): DeviceRoles {
                val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
                val dialer = runCatching {
                    (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
                }.getOrNull()
                val imes = runCatching {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.enabledInputMethodList
                        ?.map { it.packageName }
                        ?.toSet()
                }.getOrNull().orEmpty()
                return DeviceRoles(sms, dialer, imes)
            }
        }
    }

    private companion object {
        const val SHELL_PKG = "com.android.shell"
        val PACKAGE_INSTALLER_PKGS = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller"
        )
    }
}

/**
 * Resolves "special access" grants that live outside the normal permission model.
 * Computed once per scan and reused for every app.
 */
class SpecialAccessRegistry(context: Context) {

    companion object {
        /**
         * AppOps name for REQUEST_INSTALL_PACKAGES. The platform SDK does not expose a
         * constant for it, but the op string itself is stable across Android versions.
         */
        const val OP_INSTALL_PACKAGES = "android:request_install_packages"
    }

    private val appOps: AppOpsManager? =
        context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

    val accessibilityEnabled: Set<String> = loadAccessibility(context)
    val notificationListeners: Set<String> = loadNotificationListeners(context)
    val deviceAdmins: Set<String> = loadDeviceAdmins(context)

    fun appOpsState(
        op: String,
        uid: Int,
        packageName: String,
        declared: Boolean
    ): SpecialAccessGrantState {
        if (!declared) return SpecialAccessGrantState.NOT_DECLARED
        val manager = appOps ?: return SpecialAccessGrantState.DECLARED_UNVERIFIED
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                manager.unsafeCheckOpNoThrow(op, uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                manager.checkOpNoThrow(op, uid, packageName)
            }
            when (mode) {
                AppOpsManager.MODE_ALLOWED -> SpecialAccessGrantState.ENABLED
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> SpecialAccessGrantState.DECLARED_DENIED
                else -> SpecialAccessGrantState.DECLARED_UNVERIFIED
            }
        } catch (_: Throwable) {
            // Android 10+ throws SecurityException when checking AppOps for another UID.
            SpecialAccessGrantState.DECLARED_UNVERIFIED
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
            packages.addAll(NotificationManagerCompat.getEnabledListenerPackages(context))
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
    } catch (_: Throwable) {
        emptySet()
    }
}
