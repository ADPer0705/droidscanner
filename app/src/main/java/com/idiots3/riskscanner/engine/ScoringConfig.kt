package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PermissionDetail

/**
 * SINGLE SOURCE OF TRUTH for every number used by the risk engine.
 * Rendered directly by the in-app "How scoring works" screen.
 */
object ScoringConfig {

    const val VERSION = 3

    // ---------------------------------------------------------------------
    // Category weights (0..25). Special access is deliberately NOT scored here —
    // it has its own weight table below so "declared" and "enabled" can differ.
    // ---------------------------------------------------------------------
    fun categoryWeight(category: PermissionCategory): Int = when (category) {
        PermissionCategory.SPECIAL_ACCESS -> 0 // scored via SpecialAccess table
        PermissionCategory.SMS -> 12
        PermissionCategory.CONTACTS_CALL_LOGS -> 8
        PermissionCategory.CAMERA_MICROPHONE -> 10
        PermissionCategory.LOCATION -> 8
        PermissionCategory.PHONE_IDENTITY -> 6
        PermissionCategory.STORAGE_MEDIA -> 5
        PermissionCategory.BACKGROUND_PERSISTENCE -> 5
        PermissionCategory.NETWORK -> 2
        PermissionCategory.OTHER -> 0
    }

    /**
     * A category reaches its full weight when the "risk units" it holds reach this number.
     * A single granted sensitive permission already contributes two thirds of the weight;
     * two granted dangerous permissions saturate the category.
     */
    const val CATEGORY_SATURATION_UNITS = 1.5

    // Risk units contributed by a single permission.
    const val UNIT_SENSITIVE_GRANTED = 1.0
    const val UNIT_SENSITIVE_DECLARED_ONLY = 0.35
    const val UNIT_NORMAL_GRANTED = 0.25
    const val UNIT_NORMAL_DECLARED_ONLY = 0.10

    fun unitFor(permission: PermissionDetail): Double = when {
        permission.isSensitive && permission.isGranted -> UNIT_SENSITIVE_GRANTED
        permission.isSensitive -> UNIT_SENSITIVE_DECLARED_ONLY
        permission.isGranted -> UNIT_NORMAL_GRANTED
        else -> UNIT_NORMAL_DECLARED_ONLY
    }

    // ---------------------------------------------------------------------
    // Special access (highest weight). Enabled = verified through system settings/AppOps.
    // Declared-only = the app asked for it in its manifest or cannot be verified via AppOps.
    // ---------------------------------------------------------------------
    object Special {
        const val ACCESSIBILITY_ENABLED = 20
        const val ACCESSIBILITY_DECLARED = 6
        const val DEVICE_ADMIN_ACTIVE = 15
        const val DEVICE_ADMIN_DECLARED = 3
        const val NOTIFICATION_LISTENER_ENABLED = 15
        const val OVERLAY_ENABLED = 12
        const val OVERLAY_DECLARED = 4
        const val INSTALL_PACKAGES_ENABLED = 10
        const val INSTALL_PACKAGES_DECLARED = 3
        const val USAGE_STATS_ENABLED = 8
        const val USAGE_STATS_DECLARED = 2
        const val ALL_FILES_ENABLED = 8

        /** Special-access permissions we do not track individually (e.g. WRITE_SECURE_SETTINGS). */
        const val UNTRACKED_DECLARED_PER_PERMISSION = 4
        const val UNTRACKED_DECLARED_CAP = 8
    }

    // ---------------------------------------------------------------------
    // Fast static, signing, and impersonation red flags (evaluated on every scan)
    // ---------------------------------------------------------------------
    object StaticSignals {
        const val IMPERSONATION = 24
        const val DEBUG_CERT_SIGNED = 16
        const val DEBUGGABLE_APP = 12
        const val RUNTIME_PERM_BYPASS_SDK = 12 // targetSdk < 23 auto-grants dangerous permissions
        const val OLD_TARGET_SDK_WITH_SENSITIVE = 4 // targetSdk 23..28 holding sensitive grants
        const val WEAK_SIGNING_KEY = 6
    }

    // ---------------------------------------------------------------------
    // Combination rules. Conditions live in RiskScorer; points + wording live here.
    // ---------------------------------------------------------------------
    data class ComboRule(
        val id: String,
        val name: String,
        val points: Int,
        val condition: String,
        val rationale: String
    )

    val comboRules: List<ComboRule> = listOf(
        ComboRule(
            "banking_trojan",
            "Banking Trojan Pattern",
            22,
            "Accessibility service enabled + screen overlay enabled",
            "The #1 technique used by banking trojans to inject fake login screens over real banking apps."
        ),
        ComboRule(
            "banking_trojan_potential",
            "Accessibility + Overlay Capability",
            16,
            "Accessibility service enabled + screen overlay declared (verify overlay in Settings)",
            "Combines live screen-reading/input control with an overlay capability; verify in Settings that \"Display over other apps\" is off."
        ),
        ComboRule(
            "uninstall_blocker",
            "Uninstall-Blocking Control Pattern",
            20,
            "Accessibility service enabled + Device Administrator active (non-security app)",
            "Trojans combine Accessibility and Device Admin to block users from opening Settings to revoke admin rights and uninstall."
        ),
        ComboRule(
            "screen_capture_a11y",
            "Screen Capture + Accessibility Pattern",
            16,
            "Accessibility service enabled + screen recording (MediaProjection) capability",
            "Can capture PINs, banking screens, or recovery phrases while simulating touches."
        ),
        ComboRule(
            "sms_stealer",
            "SMS Data Stealer Pattern",
            20,
            "SMS read/receive/send granted + INTERNET + RECEIVE_BOOT_COMPLETED (non-default SMS)",
            "Intercepts 2FA one-time passwords and exfiltrates them after surviving a reboot."
        ),
        ComboRule(
            "utility_mismatch",
            "Context Mismatch (utility app)",
            18,
            "Torch/calculator/wallpaper-class app holding intrusive personal data access",
            "A low-complexity utility has no functional reason to hold intrusive data access."
        ),
        ComboRule(
            "hidden_spyware",
            "Hidden Surveillance Pattern",
            28,
            "Camera + microphone + location + INTERNET on an app with no launcher icon",
            "Capability set of spyware: record, locate, exfiltrate — with no user-facing UI to explain why."
        ),
        ComboRule(
            "hidden_app",
            "Invisible app with sensitive access",
            12,
            "No launcher icon while holding sensitive granted permissions (excluding keyboards)",
            "Background-only apps are legitimate for keyboards and widgets, but combined with sensitive access they deserve scrutiny."
        ),
        ComboRule(
            "data_harvester",
            "Social Graph Harvester",
            12,
            "Contacts + call log + SMS all granted + INTERNET",
            "Extracts address book, call history and messages for social-engineering extortion."
        ),
        ComboRule(
            "device_admin_mismatch",
            "Device Admin on non-security app",
            10,
            "Active device administrator on an app that is not a security/MDM tool",
            "Can block uninstall and wipe the device; legitimate only for security or enterprise tools."
        ),
        ComboRule(
            "sideloaded_sms",
            "Sideloaded SMS Access",
            12,
            "Sideloaded app holding SMS access",
            "Bypasses Play SMS policy; the classic delivery vehicle for OTP-stealing droppers."
        ),
        ComboRule(
            "sideloaded_keyboard",
            "Sideloaded Networked Keyboard",
            18,
            "Enabled third-party keyboard installed outside stores with INTERNET access",
            "Keyboards observe every password and message you type; sideloaded keyboards with internet pose keylogger risk."
        ),
        ComboRule(
            "sideloaded_autofill",
            "Sideloaded Autofill Service",
            15,
            "Active Autofill service installed outside recognized stores",
            "Autofill services can read and inject login credentials across every app on your phone."
        ),
        ComboRule(
            "sideloaded_core_role",
            "Sideloaded Default Browser or Launcher",
            14,
            "Sideloaded app set as the default web browser or home launcher",
            "Controls every web link you open or your home screen outside normal store screening."
        ),
        ComboRule(
            "repackaged_apk",
            "Repackaged / Tampered APK Signal",
            14,
            "Sideloaded app that is debuggable or signed with a debug certificate",
            "Production apps are never debug-signed or debuggable; this strongly suggests a modified or repackaged APK."
        ),
        ComboRule(
            "dropper_chain",
            "Browser/App-Sideloaded Dropper Risk",
            12,
            "Sideloaded via browser or messaging app while requesting install-packages or accessibility",
            "Apps installed outside stores that can install additional packages or use accessibility are common multi-stage droppers."
        ),
        ComboRule(
            "nfc_accessibility",
            "NFC Relay + Accessibility Pattern",
            18,
            "Accessibility service enabled alongside NFC card-emulation capability",
            "Used by modern banking malware to intercept PINs while relaying NFC payment cards."
        ),
        ComboRule(
            "otp_exfiltration",
            "Notification OTP Exfiltration",
            8,
            "Notification listener enabled + INTERNET + another strong capability",
            "Reads banking/2FA notifications and sends them off the device."
        ),
        ComboRule(
            "background_tracking",
            "Stealth Background Tracker",
            6,
            "ACCESS_BACKGROUND_LOCATION granted + INTERNET",
            "Continuous location tracking that keeps running while the app is closed."
        )
    )

    fun comboPoints(id: String): Int = comboRules.firstOrNull { it.id == id }?.points ?: 0

    const val SIDELOAD_PENALTY = 10

    // ---------------------------------------------------------------------
    // On-device AI (embedding) signal — deliberately bounded.
    //  * only applied to apps that already score Medium or above on the rules
    //  * never pushes an app into Critical on its own
    // ---------------------------------------------------------------------
    const val AI_MAX_DELTA = 8
    const val AI_MIN_RULE_SCORE = 26
    const val AI_CRITICAL_FLOOR = 76

    /** Cosine similarity thresholds for the embedding comparison (tuned for USE-lite). */
    const val AI_FIT_THRESHOLD = 0.45f
    const val AI_UNCLEAR_THRESHOLD = 0.30f
    const val AI_ARCHETYPE_TRUST_THRESHOLD = 0.40f

    val highSensitivityCategories: Set<PermissionCategory> =
        setOf(PermissionCategory.SMS, PermissionCategory.CONTACTS_CALL_LOGS)

    /** App-name keywords for low-complexity utilities that never need camera or intrusive data access. */
    val strictUtilityKeywords: List<String> = listOf(
        "torch", "flashlight", "calculator", "wallpaper", "compass", "spirit level",
        "bubble level", "unit converter", "converter", "stopwatch", "timer",
        "ringtone", "emoji", "cleaner", "booster", "battery saver", "ram booster"
    )

    /** Utilities that legitimately require CAMERA (e.g. QR scanners, mirrors), but NOT SMS/contacts/location/mic. */
    val cameraUtilityKeywords: List<String> = listOf(
        "qr code", "qr scanner", "barcode", "mirror"
    )

    val utilityKeywords: List<String> = strictUtilityKeywords + cameraUtilityKeywords

    /** Keywords that mark an app as a legitimate security / device-management tool. */
    val securityKeywords: List<String> = listOf(
        "security", "antivirus", "anti-virus", "malware", "mdm", "mobile iron",
        "mobileiron", "intune", "workspace one", "airwatch", "enterprise",
        "device policy", "sophos", "kaspersky", "bitdefender", "avast", "norton",
        "malwarebytes", "eset", "trend micro", "360 security", "find my device"
    )

    // ---------------------------------------------------------------------
    // Explainability requirements
    // ---------------------------------------------------------------------
    const val MIN_REASONS_FOR_HIGH_RISK = 3
}
