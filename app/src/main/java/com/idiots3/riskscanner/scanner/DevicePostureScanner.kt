package com.idiots3.riskscanner.scanner

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.DevicePosture
import com.idiots3.riskscanner.model.PostureAction
import com.idiots3.riskscanner.model.PostureCheck
import com.idiots3.riskscanner.model.PostureStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Rootless device-security checks using only public Android APIs.
 * Unknown results are kept distinct from failed checks, and privileged claims
 * (root, bootloader unlock, Play Protect) are intentionally never guessed at.
 */
class DevicePostureScanner(private val context: Context) {

    suspend fun scan(apps: List<AppInfo>): DevicePosture = withContext(Dispatchers.IO) {
        DevicePosture(
            checks = listOf(
                screenLockCheck(),
                DevicePostureEvaluator.evaluateSecurityPatch(Build.VERSION.SECURITY_PATCH),
                DevicePostureEvaluator.evaluateOsVersion(Build.VERSION.SDK_INT, Build.VERSION.RELEASE.orEmpty()),
                encryptionCheck(),
                developerOptionsCheck(),
                userCaCertsCheck(),
                httpProxyCheck(),
                defaultRoleProvenanceCheck(apps),
                privilegedAccessCheck(apps)
            )
        )
    }

    private fun screenLockCheck(): PostureCheck {
        val secure = (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
            ?.isDeviceSecure
        return when (secure) {
            true -> PostureCheck(
                "screen_lock", "Screen lock", PostureStatus.PROTECTED,
                "A secure lock screen is enabled.",
                "Keep a PIN, password, or strong biometric-backed lock enabled."
            )
            false -> PostureCheck(
                "screen_lock", "Screen lock", PostureStatus.AT_RISK,
                "No secure lock screen was detected.",
                "Set a PIN, password, or pattern to protect data if the phone is lost.",
                PostureAction.SECURITY_SETTINGS
            )
            null -> PostureCheck(
                "screen_lock", "Screen lock", PostureStatus.UNKNOWN,
                "Android did not expose lock-screen status on this device.",
                "Check that a PIN, password, or pattern is configured.",
                PostureAction.SECURITY_SETTINGS
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun encryptionCheck(): PostureCheck {
        val status = runCatching {
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager)
                ?.storageEncryptionStatus
        }.getOrNull()
        return when (status) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> PostureCheck(
                "encryption", "Device encryption", PostureStatus.PROTECTED,
                "Android reports that device storage is encrypted.",
                "Keep a secure lock screen enabled so encryption protects data at rest."
            )
            DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> PostureCheck(
                "encryption", "Device encryption", PostureStatus.AT_RISK,
                "Android reports that device storage encryption is inactive.",
                "Update Android or consult your device manufacturer for encryption options.",
                PostureAction.SECURITY_SETTINGS
            )
            else -> PostureCheck(
                "encryption", "Device encryption", PostureStatus.UNKNOWN,
                "Encryption status is not exposed by this Android version or device policy.",
                "Most current Android devices use file-based encryption; confirm in Settings if needed.",
                PostureAction.SECURITY_SETTINGS
            )
        }
    }

    private fun developerOptionsCheck(): PostureCheck {
        val usbAdb = globalSetting(Settings.Global.ADB_ENABLED)
        val wifiAdb = globalSetting("adb_wifi_enabled")
        val developer = globalSetting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
        return when {
            usbAdb == true && wifiAdb == true -> PostureCheck(
                "adb", "USB & Wireless debugging", PostureStatus.AT_RISK,
                "Both USB debugging and Wireless debugging are enabled.",
                "Turn off debugging when you are not actively developing or recovering the device.",
                PostureAction.DEVELOPER_SETTINGS
            )
            usbAdb == true -> PostureCheck(
                "adb", "USB debugging", PostureStatus.REVIEW,
                "USB debugging is enabled.",
                "Turn off USB debugging when you are not actively developing or recovering the device.",
                PostureAction.DEVELOPER_SETTINGS
            )
            wifiAdb == true -> PostureCheck(
                "adb_wifi", "Wireless debugging", PostureStatus.REVIEW,
                "Wireless debugging is enabled.",
                "Turn off Wireless debugging when you are not actively developing.",
                PostureAction.DEVELOPER_SETTINGS
            )
            developer == true -> PostureCheck(
                "developer_options", "Developer options", PostureStatus.REVIEW,
                "Developer options are enabled; USB and wireless debugging are off.",
                "Review developer settings and disable options you do not need.",
                PostureAction.DEVELOPER_SETTINGS
            )
            usbAdb == false || developer == false -> PostureCheck(
                "developer_options", "Developer options", PostureStatus.PROTECTED,
                "USB and wireless debugging are off.",
                "Keep debugging disabled except during active development."
            )
            else -> PostureCheck(
                "developer_options", "Developer options", PostureStatus.UNKNOWN,
                "Android did not expose developer-setting status.",
                "If you do not use developer tools, keep USB debugging switched off.",
                PostureAction.DEVELOPER_SETTINGS
            )
        }
    }

    private fun userCaCertsCheck(): PostureCheck {
        val count = runCatching {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null, null)
            var found = 0
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                if (aliases.nextElement().startsWith("user:")) found++
            }
            found
        }.getOrNull()
        return DevicePostureEvaluator.evaluateUserCaCerts(count)
    }

    private fun httpProxyCheck(): PostureCheck {
        val globalProxy = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY)
        }.getOrNull()?.trim()
        val propHost = System.getProperty("http.proxyHost")?.trim()
        val propPort = System.getProperty("http.proxyPort")?.trim()
        val effective = when {
            !globalProxy.isNullOrBlank() && globalProxy != ":0" -> globalProxy
            !propHost.isNullOrBlank() -> if (!propPort.isNullOrBlank()) "$propHost:$propPort" else propHost
            else -> null
        }
        return DevicePostureEvaluator.evaluateHttpProxy(effective)
    }

    private fun defaultRoleProvenanceCheck(apps: List<AppInfo>): PostureCheck {
        val riskyHolders = apps.filter { (it.isDefaultSmsApp || it.isDefaultDialer) && it.isSideloaded }
        return if (riskyHolders.isEmpty()) {
            PostureCheck(
                "default_roles", "Default SMS & Phone apps", PostureStatus.PROTECTED,
                "Default SMS and dialer apps are from recognized stores or pre-installed.",
                "Only set trusted apps as your default SMS or Phone handler."
            )
        } else {
            val names = riskyHolders.joinToString(", ") { it.appName }
            PostureCheck(
                "default_roles", "Default SMS & Phone apps", PostureStatus.AT_RISK,
                "A sideloaded app holds a critical default role: $names.",
                "Switch default SMS/Phone back to your trusted messaging or dialer app.",
                PostureAction.APP_REVIEW
            )
        }
    }

    private fun privilegedAccessCheck(apps: List<AppInfo>): PostureCheck {
        val accessibility = apps.count { it.specialAccess.accessibilityEnabled }
        val notifications = apps.count { it.specialAccess.notificationListenerEnabled }
        val admins = apps.count { it.specialAccess.deviceAdminActive }
        val total = accessibility + notifications + admins
        return if (total == 0) {
            PostureCheck(
                "privileged_access", "High-impact app access", PostureStatus.PROTECTED,
                "No enabled accessibility, notification-listener, or device-admin access was found in this scan.",
                "Review special access whenever you install a new app."
            )
        } else {
            PostureCheck(
                "privileged_access", "High-impact app access", PostureStatus.REVIEW,
                "$total active grant(s): $accessibility accessibility, $notifications notification, $admins device-admin.",
                "These can be legitimate, but verify every app that holds them in the Special Access Hub below.",
                PostureAction.APP_REVIEW
            )
        }
    }

    private fun globalSetting(key: String): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, key, 0) != 0
    }.getOrNull()
}

/**
 * Pure-JVM posture evaluators for patch recency, OS lifecycle, user CA certs, and proxy settings.
 */
object DevicePostureEvaluator {

    const val PATCH_PROTECTED_MAX_DAYS = 120L
    const val PATCH_REVIEW_MAX_DAYS = 365L
    const val OS_MIN_SUPPORTED_API = 33 // Android 13

    fun evaluateSecurityPatch(patch: String?, today: LocalDate = LocalDate.now()): PostureCheck {
        val cleaned = patch?.trim().orEmpty()
        val ageDays = if (cleaned.isBlank()) null else runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(cleaned), today)
        }.getOrNull()

        return when {
            ageDays == null -> PostureCheck(
                "security_patch", "Android security update", PostureStatus.UNKNOWN,
                "The device did not report a valid Android security patch date.",
                "Check for system and Google Play system updates.",
                PostureAction.SYSTEM_UPDATE
            )
            ageDays <= PATCH_PROTECTED_MAX_DAYS -> PostureCheck(
                "security_patch", "Android security update", PostureStatus.PROTECTED,
                "Android security patch: $cleaned (${ageDays.coerceAtLeast(0)} days old).",
                "Continue installing system and Google Play system updates promptly.",
                PostureAction.SYSTEM_UPDATE
            )
            ageDays <= PATCH_REVIEW_MAX_DAYS -> PostureCheck(
                "security_patch", "Android security update", PostureStatus.REVIEW,
                "Android security patch: $cleaned ($ageDays days old).",
                "Check Settings for pending system or Google Play system updates.",
                PostureAction.SYSTEM_UPDATE
            )
            else -> PostureCheck(
                "security_patch", "Android security update", PostureStatus.AT_RISK,
                "Android security patch: $cleaned ($ageDays days old).",
                "Install available system updates. Patches older than a year leave known vulnerabilities unaddressed.",
                PostureAction.SYSTEM_UPDATE
            )
        }
    }

    fun evaluateOsVersion(sdkInt: Int, releaseName: String): PostureCheck {
        val label = if (releaseName.isNotBlank()) "Android $releaseName (API $sdkInt)" else "API $sdkInt"
        return when {
            sdkInt >= 34 -> PostureCheck(
                "os_version", "Android OS support", PostureStatus.PROTECTED,
                "Running $label, which receives active platform security updates.",
                "Install OS upgrades as your manufacturer provides them."
            )
            sdkInt == OS_MIN_SUPPORTED_API -> PostureCheck(
                "os_version", "Android OS support", PostureStatus.REVIEW,
                "Running $label — security support is nearing end-of-life.",
                "Upgrade to a newer Android version if your device offers one.",
                PostureAction.SYSTEM_UPDATE
            )
            else -> PostureCheck(
                "os_version", "Android OS support", PostureStatus.AT_RISK,
                "Running $label — this OS version no longer receives AOSP security patches.",
                "Avoid sideloading untrusted apps and keep browser/Play Store apps updated.",
                PostureAction.SYSTEM_UPDATE
            )
        }
    }

    fun evaluateUserCaCerts(userCertCount: Int?): PostureCheck = when {
        userCertCount == null -> PostureCheck(
            "user_ca_certs", "User-installed CA certificates", PostureStatus.UNKNOWN,
            "Could not inspect the Android certificate store.",
            "Review Trusted Credentials in Security Settings if you suspect HTTPS inspection.",
            PostureAction.CREDENTIAL_SETTINGS
        )
        userCertCount == 0 -> PostureCheck(
            "user_ca_certs", "User-installed CA certificates", PostureStatus.PROTECTED,
            "No custom user CA certificates are installed.",
            "Only install user CA certificates for trusted corporate networks or debugging."
        )
        else -> PostureCheck(
            "user_ca_certs", "User-installed CA certificates", PostureStatus.AT_RISK,
            "$userCertCount user-installed CA certificate(s) found. Can allow HTTPS traffic interception.",
            "Remove unfamiliar user certificates under Settings > Security > Encryption & credentials.",
            PostureAction.CREDENTIAL_SETTINGS
        )
    }

    fun evaluateHttpProxy(proxyEndpoint: String?): PostureCheck = if (proxyEndpoint.isNullOrBlank()) {
        PostureCheck(
            "http_proxy", "System HTTP proxy", PostureStatus.PROTECTED,
            "No global HTTP proxy is configured.",
            "Keep proxy settings clear unless using a trusted corporate or local proxy."
        )
    } else {
        PostureCheck(
            "http_proxy", "System HTTP proxy", PostureStatus.REVIEW,
            "Global HTTP proxy active ($proxyEndpoint). Traffic from compatible apps routes through this proxy.",
            "Verify this proxy is intentional in your Wi-Fi / Network settings.",
            PostureAction.NETWORK_SETTINGS
        )
    }
}
