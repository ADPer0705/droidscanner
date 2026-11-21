package com.idiots3.riskscanner.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.idiots3.riskscanner.model.PostureAction
import com.idiots3.riskscanner.model.RecommendationAction
import com.idiots3.riskscanner.model.SecurityRecommendation

/**
 * Centralized, crash-safe Android Settings and remediation intent launchers.
 * Every launcher falls back gracefully across Android versions and OEM ROMs.
 */
object SettingsIntents {

    fun openAppDetails(context: Context, packageName: String) {
        launchFirstAvailable(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    fun requestUninstall(context: Context, packageName: String) {
        launchFirstAvailable(
            context,
            Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    fun openOverlaySettings(context: Context, packageName: String? = null) {
        val candidates = buildList {
            if (!packageName.isNullOrBlank()) {
                add(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
            add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
        launchFirstAvailable(context, *candidates.toTypedArray())
    }

    fun openInstallUnknownAppsSettings(context: Context, packageName: String? = null) {
        val candidates = buildList {
            if (!packageName.isNullOrBlank()) {
                add(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
            add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
            add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
        launchFirstAvailable(context, *candidates.toTypedArray())
    }

    fun openAllFilesAccessSettings(context: Context, packageName: String? = null) {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!packageName.isNullOrBlank()) {
                    add(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
                add(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            if (!packageName.isNullOrBlank()) {
                add(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
            add(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        }
        launchFirstAvailable(context, *candidates.toTypedArray())
    }

    fun openAccessibilitySettings(context: Context) {
        launchFirstAvailable(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openNotificationListenerSettings(context: Context) {
        launchFirstAvailable(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openDeviceAdminSettings(context: Context) {
        launchFirstAvailable(
            context,
            Intent("android.settings.DEVICE_ADMIN_SETTINGS"),
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        )
    }

    fun openUsageAccessSettings(context: Context) {
        launchFirstAvailable(
            context,
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        )
    }

    fun openSystemUpdateSettings(context: Context) {
        launchFirstAvailable(
            context,
            Intent("android.settings.SYSTEM_UPDATE_SETTINGS"),
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    fun openCredentialSettings(context: Context) {
        launchFirstAvailable(
            context,
            Intent("com.android.settings.TRUSTED_CREDENTIALS"),
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        )
    }

    fun openNetworkSettings(context: Context) {
        launchFirstAvailable(
            context,
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    fun openPostureAction(context: Context, action: PostureAction?) {
        when (action) {
            PostureAction.SECURITY_SETTINGS -> launchFirstAvailable(context, Intent(Settings.ACTION_SECURITY_SETTINGS))
            PostureAction.SYSTEM_UPDATE -> openSystemUpdateSettings(context)
            PostureAction.DEVELOPER_SETTINGS -> launchFirstAvailable(
                context,
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            PostureAction.ACCESSIBILITY_SETTINGS -> openAccessibilitySettings(context)
            PostureAction.NOTIFICATION_SETTINGS -> openNotificationListenerSettings(context)
            PostureAction.CREDENTIAL_SETTINGS -> openCredentialSettings(context)
            PostureAction.NETWORK_SETTINGS -> openNetworkSettings(context)
            PostureAction.USAGE_ACCESS_SETTINGS -> openUsageAccessSettings(context)
            PostureAction.APP_REVIEW, null -> Unit
        }
    }

    fun openRecommendation(
        context: Context,
        rec: SecurityRecommendation,
        onNavigateToAppDetail: (String) -> Unit,
        onExternalLaunched: () -> Unit
    ) {
        val pkg = rec.targetPackage
        when (rec.action) {
            RecommendationAction.OPEN_APP_DETAIL -> {
                if (!pkg.isNullOrBlank()) onNavigateToAppDetail(pkg)
            }
            RecommendationAction.OPEN_APP_SETTINGS -> {
                if (!pkg.isNullOrBlank()) {
                    onExternalLaunched()
                    openAppDetails(context, pkg)
                }
            }
            RecommendationAction.OPEN_ACCESSIBILITY_SETTINGS -> {
                onExternalLaunched()
                openAccessibilitySettings(context)
            }
            RecommendationAction.OPEN_DEVICE_ADMIN_SETTINGS -> {
                onExternalLaunched()
                openDeviceAdminSettings(context)
            }
            RecommendationAction.OPEN_NOTIFICATION_SETTINGS -> {
                onExternalLaunched()
                openNotificationListenerSettings(context)
            }
            RecommendationAction.OPEN_OVERLAY_SETTINGS -> {
                onExternalLaunched()
                openOverlaySettings(context, pkg)
            }
            RecommendationAction.OPEN_INSTALL_APPS_SETTINGS -> {
                onExternalLaunched()
                openInstallUnknownAppsSettings(context, pkg)
            }
            RecommendationAction.OPEN_SECURITY_SETTINGS -> {
                onExternalLaunched()
                launchFirstAvailable(context, Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            RecommendationAction.OPEN_DEVELOPER_SETTINGS -> {
                onExternalLaunched()
                launchFirstAvailable(
                    context,
                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS)
                )
            }
            RecommendationAction.OPEN_SYSTEM_UPDATE -> {
                onExternalLaunched()
                openSystemUpdateSettings(context)
            }
            RecommendationAction.OPEN_CREDENTIAL_SETTINGS -> {
                onExternalLaunched()
                openCredentialSettings(context)
            }
            RecommendationAction.OPEN_NETWORK_SETTINGS -> {
                onExternalLaunched()
                openNetworkSettings(context)
            }
            RecommendationAction.OPEN_USAGE_ACCESS_SETTINGS -> {
                onExternalLaunched()
                openUsageAccessSettings(context)
            }
            RecommendationAction.NONE -> Unit
        }
    }

    private fun launchFirstAvailable(context: Context, vararg intents: Intent) {
        for (intent in intents) {
            val launched = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
    }
}
