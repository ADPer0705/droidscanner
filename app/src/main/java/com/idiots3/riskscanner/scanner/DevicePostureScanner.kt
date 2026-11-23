package com.idiots3.riskscanner.scanner

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.DevicePosture
import com.idiots3.riskscanner.model.DismissedPostureRecord
import com.idiots3.riskscanner.model.PostureAction
import com.idiots3.riskscanner.model.PostureCheck
import com.idiots3.riskscanner.model.PostureStatus
import com.idiots3.riskscanner.model.TrustValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Rootless device-security and configuration checks using only public Android APIs.
 * Unknown results are kept distinct from failed checks, and privileged claims
 * (root, bootloader unlock, OS compromise) are intentionally never guessed at.
 */
class DevicePostureScanner(private val context: Context) {

    suspend fun scan(
        apps: List<AppInfo>,
        dismissedPosture: Map<String, DismissedPostureRecord> = emptyMap()
    ): DevicePosture = withContext(Dispatchers.IO) {
        val secureLock = (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure
        val rawChecks = listOf(
            screenLockCheck(secureLock),
            lockScreenNotificationsCheck(secureLock),
            screenTimeoutAndAwakeCheck(),
            DevicePostureEvaluator.evaluateSecurityPatch(Build.VERSION.SECURITY_PATCH),
            DevicePostureEvaluator.evaluateOsVersion(Build.VERSION.SDK_INT, Build.VERSION.RELEASE.orEmpty()),
            osBuildHygieneCheck(),
            encryptionCheck(),
            developerOptionsCheck(),
            userCaCertsCheck(),
            privateDnsCheck(),
            httpProxyCheck(),
            networkTransportCheck(apps),
            defaultRoleProvenanceCheck(apps),
            inputAndAutofillCheck(apps),
            deviceManagementCheck(apps),
            privilegedAccessCheck(apps)
        )

        val evaluated = rawChecks.map { check ->
            val dismissedRecord = dismissedPosture[check.id]
            if (dismissedRecord != null && TrustValidator.isPostureDismissalValid(check, dismissedRecord)) {
                check.copy(isDismissed = true)
            } else {
                check
            }
        }
        DevicePosture(checks = evaluated)
    }

    private fun screenLockCheck(secure: Boolean?): PostureCheck = when (secure) {
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

    private fun lockScreenNotificationsCheck(secureLock: Boolean?): PostureCheck {
        val allowPrivate = secureIntSetting("lock_screen_allow_private_notifications")
        val showNotifications = secureIntSetting("lock_screen_show_notifications")
        return DevicePostureEvaluator.evaluateLockScreenPrivacy(
            hasSecureLock = secureLock,
            showNotifications = showNotifications,
            allowPrivateNotifications = allowPrivate
        )
    }

    private fun screenTimeoutAndAwakeCheck(): PostureCheck {
        val timeoutMs = systemIntSetting(Settings.System.SCREEN_OFF_TIMEOUT)
        val stayAwakeFlags = globalIntSetting(Settings.Global.STAY_ON_WHILE_PLUGGED_IN)
        return DevicePostureEvaluator.evaluateScreenTimeoutAndAwake(timeoutMs, stayAwakeFlags)
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
                "Most modern Android devices use file-based encryption; confirm in Settings if needed.",
                PostureAction.SECURITY_SETTINGS
            )
        }
    }

    private fun developerOptionsCheck(): PostureCheck {
        val usbAdb = globalBoolSetting(Settings.Global.ADB_ENABLED)
        val wifiAdb = globalBoolSetting("adb_wifi_enabled")
        val developer = globalBoolSetting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
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
        val certs: List<String>? = runCatching {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null, null)
            val details = mutableListOf<String>()
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (!alias.startsWith("user:")) continue
                val cert = ks.getCertificate(alias) as? X509Certificate
                if (cert != null) {
                    val subject = cert.subjectX500Principal.name
                    val cn = extractRdn(subject, "CN") ?: extractRdn(subject, "O") ?: subject
                    val expiredTag = if (cert.notAfter.time < System.currentTimeMillis()) " (expired)" else ""
                    details += "$cn$expiredTag"
                } else {
                    details += alias
                }
            }
            details
        }.getOrNull()
        return DevicePostureEvaluator.evaluateUserCaCerts(certs?.size, certs.orEmpty())
    }

    private fun privateDnsCheck(): PostureCheck {
        val mode = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        }.getOrNull()
        val specifier = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_specifier")
        }.getOrNull()
        return DevicePostureEvaluator.evaluatePrivateDns(mode, specifier)
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

    private fun networkTransportCheck(apps: List<AppInfo>): PostureCheck {
        val vpnApps = apps.filter { it.specialAccess.vpnServiceDeclared && !it.isSystemApp }
            .map { if (it.isSideloaded) "${it.appName} (sideloaded)" else it.appName }
        val transport = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching null
            val active = cm.activeNetwork
            val caps = active?.let { cm.getNetworkCapabilities(it) }
            val vpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            val captivePortal = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
            Pair(vpnActive, captivePortal)
        }.getOrNull()

        return DevicePostureEvaluator.evaluateNetworkTransport(
            vpnActive = transport?.first,
            captivePortal = transport?.second,
            vpnAppNames = vpnApps
        )
    }

    private fun defaultRoleProvenanceCheck(apps: List<AppInfo>): PostureCheck {
        val riskyHolders = apps.filter {
            (it.isDefaultSmsApp || it.isDefaultDialer || it.isDefaultBrowser || it.isDefaultLauncher) &&
                it.isSideloaded
        }
        val details = riskyHolders.map { app ->
            val roles = buildList {
                if (app.isDefaultSmsApp) add("Default SMS")
                if (app.isDefaultDialer) add("Default Phone")
                if (app.isDefaultBrowser) add("Default Browser")
                if (app.isDefaultLauncher) add("Default Home")
            }.joinToString(", ")
            "${app.appName} ($roles · ${app.installerSource})"
        }
        return if (riskyHolders.isEmpty()) {
            PostureCheck(
                "default_roles", "Default core apps", PostureStatus.PROTECTED,
                "Default SMS, Phone, Browser, and Launcher apps are from recognized stores or pre-installed.",
                "Only set trusted apps as your default messaging, calling, browsing, or home handler."
            )
        } else {
            PostureCheck(
                "default_roles", "Default core apps", PostureStatus.AT_RISK,
                "${riskyHolders.size} sideloaded app(s) hold critical default roles.",
                "Switch default SMS, Phone, Browser, or Home back to a trusted app in Default Apps settings.",
                PostureAction.DEFAULT_APPS_SETTINGS,
                details = details
            )
        }
    }

    private fun inputAndAutofillCheck(apps: List<AppInfo>): PostureCheck {
        val thirdPartyImes = apps.filter { it.isEnabledInputMethod && !it.isSystemApp }
        val sideloadedNetImes = thirdPartyImes.filter { it.isSideloaded && it.hasGranted("android.permission.INTERNET") }
        val playNetImes = thirdPartyImes.filter { !it.isSideloaded && it.hasGranted("android.permission.INTERNET") }
        val sideloadedAutofill = apps.filter { it.isAutofillService && it.isSideloaded }
        val sideloadedAssistant = apps.filter { it.isDefaultAssistant && it.isSideloaded }

        return DevicePostureEvaluator.evaluateInputAndAutofill(
            sideloadedNetworkKeyboards = sideloadedNetImes.map { it.appName },
            storeNetworkKeyboards = playNetImes.map { it.appName },
            sideloadedAutofillApps = sideloadedAutofill.map { it.appName },
            sideloadedAssistantApps = sideloadedAssistant.map { it.appName }
        )
    }

    private fun deviceManagementCheck(apps: List<AppInfo>): PostureCheck {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val owners = mutableListOf<String>()
        if (dpm != null) {
            for (app in apps) {
                val isDo = runCatching { dpm.isDeviceOwnerApp(app.packageName) }.getOrDefault(false)
                val isPo = runCatching { dpm.isProfileOwnerApp(app.packageName) }.getOrDefault(false)
                if (isDo) owners += "${app.appName} (Device Owner)"
                else if (isPo) owners += "${app.appName} (Profile Owner)"
            }
        }
        val profileCount = runCatching {
            (context.getSystemService(Context.USER_SERVICE) as? UserManager)?.userProfiles?.size
        }.getOrNull() ?: 1

        return DevicePostureEvaluator.evaluateDeviceManagement(owners, profileCount)
    }

    private fun osBuildHygieneCheck(): PostureCheck {
        val verifierEnabled = globalIntSetting("package_verifier_enable")
        val adbVerifyEnabled = globalIntSetting("verifier_verify_adb_installs")
        val legacyUnknownSources = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            secureIntSetting(Settings.Secure.INSTALL_NON_MARKET_APPS)
        } else null

        return DevicePostureEvaluator.evaluateOsBuildHygiene(
            buildTags = Build.TAGS,
            buildType = Build.TYPE,
            packageVerifierEnabled = verifierEnabled,
            adbVerifyEnabled = adbVerifyEnabled,
            legacyUnknownSources = legacyUnknownSources
        )
    }

    private fun privilegedAccessCheck(apps: List<AppInfo>): PostureCheck {
        val activeApps = apps.filter { !it.isUserTrusted }
        val accessibility = activeApps.count { it.specialAccess.accessibilityEnabled }
        val notifications = activeApps.count { it.specialAccess.notificationListenerEnabled }
        val admins = activeApps.count { it.specialAccess.deviceAdminActive }
        val total = accessibility + notifications + admins
        return if (total == 0) {
            PostureCheck(
                "privileged_access", "High-impact app access", PostureStatus.PROTECTED,
                "No unreviewed accessibility, notification-listener, or device-admin grants were found.",
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

    private fun globalIntSetting(key: String): Int? = try {
        Settings.Global.getInt(context.contentResolver, key)
    } catch (_: Throwable) {
        null
    }

    private fun globalBoolSetting(key: String): Boolean? = globalIntSetting(key)?.let { it != 0 }

    private fun secureIntSetting(key: String): Int? = try {
        Settings.Secure.getInt(context.contentResolver, key)
    } catch (_: Throwable) {
        null
    }

    private fun systemIntSetting(key: String): Int? = try {
        Settings.System.getInt(context.contentResolver, key)
    } catch (_: Throwable) {
        null
    }

    private fun extractRdn(dn: String, key: String): String? {
        val prefix = "$key="
        return dn.split(',')
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substring(prefix.length)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

/**
 * Pure-JVM posture evaluators for every device configuration check, keeping all logic
 * deterministic and unit-testable without an Android device.
 */
object DevicePostureEvaluator {

    const val PATCH_PROTECTED_MAX_DAYS = 120L
    const val PATCH_REVIEW_MAX_DAYS = 365L
    const val OS_MIN_SUPPORTED_API = 33 // Android 13

    private val KNOWN_REPUTABLE_DNS_SUFFIXES = listOf(
        "dns.google",
        "dns.google.com",
        "one.one.one.one",
        "1dot1dot1dot1.cloudflare-dns.com",
        "security.cloudflare-dns.com",
        "family.cloudflare-dns.com",
        "dns.quad9.net",
        "dns9.quad9.net",
        "dns.adguard-dns.com",
        "dns.adguard.com",
        "dns.mullvad.net",
        "dns.nextdns.io",
        "controld.com",
        "doh.cleanbrowsing.org",
        "dns.rethinkdns.com"
    )

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
                "Avoid sideloading untrusted apps and keep your browser and apps updated.",
                PostureAction.SYSTEM_UPDATE
            )
        }
    }

    fun evaluateUserCaCerts(userCertCount: Int?, certDetails: List<String> = emptyList()): PostureCheck = when {
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
            PostureAction.CREDENTIAL_SETTINGS,
            details = certDetails.take(6)
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

    fun evaluatePrivateDns(mode: String?, specifier: String?): PostureCheck {
        val m = mode?.trim()?.lowercase()
        val host = specifier?.trim()?.lowercase().orEmpty()
        return when (m) {
            null, "" -> PostureCheck(
                "private_dns", "Encrypted DNS (Private DNS)", PostureStatus.UNKNOWN,
                "Android did not expose Private DNS configuration on this device.",
                "Check Network & Internet > Private DNS and set it to Automatic or a trusted provider.",
                PostureAction.PRIVATE_DNS_SETTINGS
            )
            "off" -> PostureCheck(
                "private_dns", "Encrypted DNS (Private DNS)", PostureStatus.REVIEW,
                "Private DNS is turned off. Website lookups on public Wi-Fi may be visible or spoofed.",
                "Set Private DNS to Automatic (or a reputable encrypted DNS provider) in Network settings.",
                PostureAction.PRIVATE_DNS_SETTINGS
            )
            "opportunistic" -> PostureCheck(
                "private_dns", "Encrypted DNS (Private DNS)", PostureStatus.PROTECTED,
                "Private DNS is set to Automatic (encrypted DNS used whenever supported).",
                "Keep Private DNS on Automatic or a trusted provider."
            )
            "hostname" -> {
                val reputable = host.isNotBlank() && KNOWN_REPUTABLE_DNS_SUFFIXES.any { suffix ->
                    host == suffix || host.endsWith(".$suffix")
                }
                if (reputable) {
                    PostureCheck(
                        "private_dns", "Encrypted DNS (Private DNS)", PostureStatus.PROTECTED,
                        "Encrypted Private DNS active via recognized provider ($host).",
                        "All DNS lookups are encrypted over TLS."
                    )
                } else {
                    PostureCheck(
                        "private_dns", "Encrypted DNS (Private DNS)", PostureStatus.REVIEW,
                        "Custom Private DNS configured (${host.ifBlank { "unspecified" }}). All DNS lookups route through this host.",
                        "Verify you intentionally configured this DNS server and not a third-party profile or app.",
                        PostureAction.PRIVATE_DNS_SETTINGS,
                        details = listOfNotNull(host.takeIf { it.isNotBlank() }?.let { "Hostname: $it" })
                    )
                }
            }
            else -> PostureCheck(
                "private_dns", "Encrypted DNS (Private DNS)", PostureStatus.UNKNOWN,
                "Private DNS reported mode \"$m\".",
                "Verify Private DNS in Network & Internet settings.",
                PostureAction.PRIVATE_DNS_SETTINGS
            )
        }
    }

    fun evaluateLockScreenPrivacy(
        hasSecureLock: Boolean?,
        showNotifications: Int?,
        allowPrivateNotifications: Int?
    ): PostureCheck {
        if (hasSecureLock == false) {
            return PostureCheck(
                "lock_screen_privacy", "Lock-screen notification privacy", PostureStatus.UNKNOWN,
                "Applies once a secure screen lock is enabled.",
                "Set a screen lock first, then hide sensitive notifications while locked.",
                PostureAction.SECURITY_SETTINGS
            )
        }
        return when {
            showNotifications == 0 || allowPrivateNotifications == 0 -> PostureCheck(
                "lock_screen_privacy", "Lock-screen notification privacy", PostureStatus.PROTECTED,
                "Sensitive notification contents (such as SMS OTP codes) are hidden on the lock screen.",
                "Keep sensitive notifications hidden while locked."
            )
            allowPrivateNotifications == 1 -> PostureCheck(
                "lock_screen_privacy", "Lock-screen notification privacy", PostureStatus.REVIEW,
                "Sensitive notification contents can be read on the lock screen without unlocking.",
                "Hide sensitive content on the lock screen so anyone near your phone cannot read banking OTPs.",
                PostureAction.LOCK_SCREEN_SETTINGS
            )
            else -> PostureCheck(
                "lock_screen_privacy", "Lock-screen notification privacy", PostureStatus.UNKNOWN,
                "Android did not expose lock-screen notification privacy status.",
                "Verify under Settings > Notifications > Notifications on lock screen.",
                PostureAction.LOCK_SCREEN_SETTINGS
            )
        }
    }

    fun evaluateScreenTimeoutAndAwake(timeoutMs: Int?, stayAwakeFlags: Int?): PostureCheck {
        val stayAwake = stayAwakeFlags != null && stayAwakeFlags != 0
        val timeoutMinutes = timeoutMs?.let { if (it == Int.MAX_VALUE || it < 0) 999 else it / 60_000 }
        return when {
            stayAwake -> PostureCheck(
                "screen_timeout", "Auto-lock & Stay Awake", PostureStatus.REVIEW,
                "\"Stay awake while charging\" is enabled — the phone stays unlocked whenever plugged into USB power.",
                "Turn off Stay Awake in Developer options so public chargers or desks cannot leave your screen unlocked.",
                PostureAction.DEVELOPER_SETTINGS
            )
            timeoutMinutes != null && timeoutMinutes >= 30 -> PostureCheck(
                "screen_timeout", "Auto-lock & Stay Awake", PostureStatus.AT_RISK,
                "Screen timeout is set very high (${if (timeoutMinutes >= 999) "Never" else "${timeoutMinutes}m"}).",
                "Reduce screen timeout to 1–2 minutes so an unattended phone locks automatically.",
                PostureAction.DISPLAY_SETTINGS
            )
            timeoutMinutes != null && timeoutMinutes > 5 -> PostureCheck(
                "screen_timeout", "Auto-lock & Stay Awake", PostureStatus.REVIEW,
                "Screen stays unlocked for $timeoutMinutes minutes of inactivity.",
                "Consider setting Display > Screen timeout to 1–2 minutes.",
                PostureAction.DISPLAY_SETTINGS
            )
            timeoutMinutes != null -> PostureCheck(
                "screen_timeout", "Auto-lock & Stay Awake", PostureStatus.PROTECTED,
                "Screen locks promptly (${timeoutMs / 1000}s timeout) and Stay Awake is off.",
                "Short auto-lock timeouts limit physical-access exposure."
            )
            else -> PostureCheck(
                "screen_timeout", "Auto-lock & Stay Awake", PostureStatus.UNKNOWN,
                "Could not read screen auto-lock timeout on this device.",
                "Ensure Display > Screen timeout is 2 minutes or less.",
                PostureAction.DISPLAY_SETTINGS
            )
        }
    }

    fun evaluateNetworkTransport(
        vpnActive: Boolean?,
        captivePortal: Boolean?,
        vpnAppNames: List<String>
    ): PostureCheck = when {
        captivePortal == true -> PostureCheck(
            "network_transport", "Current network & VPN", PostureStatus.REVIEW,
            "Connected to a captive-portal network (public Wi-Fi sign-in page). Traffic may be monitored on this network.",
            "Avoid banking or entering passwords on captive Wi-Fi unless using a trusted VPN.",
            PostureAction.NETWORK_SETTINGS
        )
        vpnActive == true -> PostureCheck(
            "network_transport", "Current network & VPN", PostureStatus.REVIEW,
            "An active VPN tunnel is currently routing device traffic.",
            "Confirm you recognize and trust the active VPN in Settings > Network > VPN.",
            PostureAction.VPN_SETTINGS,
            details = if (vpnAppNames.isNotEmpty()) {
                listOf("Installed VPN-capable apps: ${vpnAppNames.take(5).joinToString(", ")}")
            } else emptyList()
        )
        vpnActive == false -> PostureCheck(
            "network_transport", "Current network & VPN", PostureStatus.PROTECTED,
            "No unexpected VPN tunnel or captive-portal sign-in network is currently active.",
            "Review VPN settings whenever you see a key icon in the status bar."
        )
        else -> PostureCheck(
            "network_transport", "Current network & VPN", PostureStatus.UNKNOWN,
            "No active network transport details available right now.",
            "Verify active VPN apps under Network > VPN settings.",
            PostureAction.VPN_SETTINGS
        )
    }

    fun evaluateInputAndAutofill(
        sideloadedNetworkKeyboards: List<String>,
        storeNetworkKeyboards: List<String>,
        sideloadedAutofillApps: List<String>,
        sideloadedAssistantApps: List<String>
    ): PostureCheck {
        val highRiskItems = buildList {
            sideloadedNetworkKeyboards.forEach { add("Sideloaded keyboard with internet: $it") }
            sideloadedAutofillApps.forEach { add("Sideloaded Autofill service: $it") }
            sideloadedAssistantApps.forEach { add("Sideloaded Assistant service: $it") }
        }
        return when {
            highRiskItems.isNotEmpty() -> PostureCheck(
                "input_autofill", "Keyboards, Autofill & Assistant", PostureStatus.AT_RISK,
                "A sideloaded app can observe everything you type or fill passwords.",
                "Disable unfamiliar third-party keyboards or autofill services immediately.",
                if (sideloadedNetworkKeyboards.isNotEmpty()) PostureAction.INPUT_METHOD_SETTINGS else PostureAction.AUTOFILL_SETTINGS,
                details = highRiskItems
            )
            storeNetworkKeyboards.isNotEmpty() -> PostureCheck(
                "input_autofill", "Keyboards, Autofill & Assistant", PostureStatus.REVIEW,
                "${storeNetworkKeyboards.size} third-party keyboard(s) with internet access enabled: ${storeNetworkKeyboards.take(3).joinToString(", ")}.",
                "Keyboards see every password and message you type — keep only keyboards you fully trust.",
                PostureAction.INPUT_METHOD_SETTINGS,
                details = storeNetworkKeyboards.map { "Enabled keyboard: $it" }
            )
            else -> PostureCheck(
                "input_autofill", "Keyboards, Autofill & Assistant", PostureStatus.PROTECTED,
                "No risky third-party networked keyboards, autofill, or assistant services detected.",
                "Only enable trusted keyboards and password managers."
            )
        }
    }

    fun evaluateDeviceManagement(ownerApps: List<String>, profileCount: Int): PostureCheck = when {
        ownerApps.isNotEmpty() -> PostureCheck(
            "device_management", "Device / Work Profile management", PostureStatus.REVIEW,
            "An enterprise or MDM controller is active on this device (${ownerApps.joinToString(", ")}).",
            "Expected on employer-managed devices; on a personal phone, remove unfamiliar management profiles.",
            PostureAction.DEVICE_ADMIN_SETTINGS,
            details = ownerApps
        )
        profileCount > 1 -> PostureCheck(
            "device_management", "Device / Work Profile management", PostureStatus.PROTECTED,
            "$profileCount user/work profiles present; no unexpected Device Owner found in this profile.",
            "Verify your Work Profile belongs to an organization or app isolation tool you set up."
        )
        else -> PostureCheck(
            "device_management", "Device / Work Profile management", PostureStatus.PROTECTED,
            "No external Device Owner or enterprise MDM profile detected.",
            "Keep device management clear on personal phones."
        )
    }

    fun evaluateOsBuildHygiene(
        buildTags: String?,
        buildType: String?,
        packageVerifierEnabled: Int?,
        adbVerifyEnabled: Int?,
        legacyUnknownSources: Int?
    ): PostureCheck {
        val issues = mutableListOf<String>()
        if (buildTags?.contains("test-keys", ignoreCase = true) == true) {
            issues += "OS build is signed with unofficial test-keys"
        }
        if (!buildType.isNullOrBlank() && !buildType.equals("user", ignoreCase = true)) {
            issues += "Non-production OS build type ($buildType)"
        }
        if (packageVerifierEnabled == 0) {
            issues += "Android package verifier appears switched off"
        }
        if (adbVerifyEnabled == 0) {
            issues += "ADB install verification is switched off"
        }
        if (legacyUnknownSources == 1) {
            issues += "Legacy global \"Unknown sources\" toggle is enabled"
        }
        return if (issues.isEmpty()) {
            PostureCheck(
                "os_build_hygiene", "OS build & app verification", PostureStatus.PROTECTED,
                "Production Android user build (${buildType ?: "user"}) with no exposed build-hygiene flags.",
                "Note: Rootless checks cannot prove bootloader lock or Play Protect status — verify Play Protect in the Play Store."
            )
        } else {
            PostureCheck(
                "os_build_hygiene", "OS build & app verification", PostureStatus.AT_RISK,
                issues.first(),
                "On everyday consumer phones, keep Play Protect / app verification enabled and run signed release OS builds.",
                PostureAction.SECURITY_SETTINGS,
                details = issues
            )
        }
    }
}
