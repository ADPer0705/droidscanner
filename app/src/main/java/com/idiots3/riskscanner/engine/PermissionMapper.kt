package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.ProtectionLevel

/**
 * Static knowledge base: maps Android permissions to one of the nine mandatory
 * categories, a protection level and a plain-language explanation.
 * Permissions that are not in the table are surfaced as "Other" (visible, not scored).
 */
object PermissionMapper {

    data class PermissionInfo(
        val category: PermissionCategory,
        val protectionLevel: ProtectionLevel,
        val description: String
    )

    private val permissionMap: Map<String, PermissionInfo> = buildMap {
        // ---------------------------------------------------------------- SPECIAL ACCESS
        put("android.permission.SYSTEM_ALERT_WINDOW", PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Draw overlays on top of other apps - can create fake login screens"))
        put("android.permission.REQUEST_INSTALL_PACKAGES", PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Install other apps without going through the Play Store"))
        put("android.permission.PACKAGE_USAGE_STATS", PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Monitor which apps you use and when"))
        put("android.permission.BIND_ACCESSIBILITY_SERVICE", PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Read screen content and perform actions - highest risk access"))
        put("android.permission.BIND_DEVICE_ADMIN", PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Device admin can prevent uninstall and wipe the device"))
        put("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Read all notifications including OTPs and messages"))
        put("android.permission.WRITE_SECURE_SETTINGS", PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Modify secure system settings"))
        put("android.permission.WRITE_SETTINGS", PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Modify system settings"))
        put("android.permission.REQUEST_DELETE_PACKAGES", PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Request deletion of other apps"))

        // ---------------------------------------------------------------- SMS
        put("android.permission.READ_SMS", PermissionInfo(PermissionCategory.SMS, ProtectionLevel.DANGEROUS, "Read text messages including one-time passwords"))
        put("android.permission.RECEIVE_SMS", PermissionInfo(PermissionCategory.SMS, ProtectionLevel.DANGEROUS, "Intercept incoming text messages"))
        put("android.permission.SEND_SMS", PermissionInfo(PermissionCategory.SMS, ProtectionLevel.DANGEROUS, "Send text messages - can be abused for premium SMS fraud"))
        put("android.permission.RECEIVE_MMS", PermissionInfo(PermissionCategory.SMS, ProtectionLevel.DANGEROUS, "Receive MMS messages"))
        put("android.permission.RECEIVE_WAP_PUSH", PermissionInfo(PermissionCategory.SMS, ProtectionLevel.DANGEROUS, "Receive WAP push messages"))

        // ---------------------------------------------------------------- CONTACTS & CALL LOGS
        put("android.permission.READ_CONTACTS", PermissionInfo(PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS, "Read your contact list"))
        put("android.permission.WRITE_CONTACTS", PermissionInfo(PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS, "Modify your contacts"))
        put("android.permission.READ_CALL_LOG", PermissionInfo(PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS, "Read your call history"))
        put("android.permission.WRITE_CALL_LOG", PermissionInfo(PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS, "Modify your call history"))
        put("android.permission.GET_ACCOUNTS", PermissionInfo(PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS, "Access accounts on the device"))

        // ---------------------------------------------------------------- CAMERA & MICROPHONE
        put("android.permission.CAMERA", PermissionInfo(PermissionCategory.CAMERA_MICROPHONE, ProtectionLevel.DANGEROUS, "Take photos and record video"))
        put("android.permission.RECORD_AUDIO", PermissionInfo(PermissionCategory.CAMERA_MICROPHONE, ProtectionLevel.DANGEROUS, "Record audio from the microphone"))
        put("android.permission.RECORD_BACKGROUND_AUDIO", PermissionInfo(PermissionCategory.CAMERA_MICROPHONE, ProtectionLevel.DANGEROUS, "Record audio while running in the background"))

        // ---------------------------------------------------------------- LOCATION
        put("android.permission.ACCESS_FINE_LOCATION", PermissionInfo(PermissionCategory.LOCATION, ProtectionLevel.DANGEROUS, "Track your precise GPS location"))
        put("android.permission.ACCESS_COARSE_LOCATION", PermissionInfo(PermissionCategory.LOCATION, ProtectionLevel.DANGEROUS, "Track your approximate location"))
        put("android.permission.ACCESS_BACKGROUND_LOCATION", PermissionInfo(PermissionCategory.LOCATION, ProtectionLevel.DANGEROUS, "Track your location even when the app is closed"))

        // ---------------------------------------------------------------- PHONE & IDENTITY
        put("android.permission.READ_PHONE_STATE", PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Read phone status and identity (device fingerprinting)"))
        put("android.permission.READ_PHONE_NUMBERS", PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Read your phone numbers"))
        put("android.permission.CALL_PHONE", PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Make phone calls without your interaction"))
        put("android.permission.ANSWER_PHONE_CALLS", PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Answer incoming calls programmatically"))
        put("android.permission.ADD_VOICEMAIL", PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Add voicemail messages"))
        put("android.permission.USE_SIP", PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Make SIP calls"))
        put("android.permission.PROCESS_OUTGOING_CALLS", PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Intercept outgoing calls"))
        put("android.permission.ACCEPT_HANDOVER", PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Continue a call from another app"))
        put("android.permission.MANAGE_OWN_CALLS", PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.NORMAL, "Route calls through the app's own calling service"))

        // ---------------------------------------------------------------- STORAGE & MEDIA
        put("android.permission.READ_EXTERNAL_STORAGE", PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Read files from shared storage"))
        put("android.permission.WRITE_EXTERNAL_STORAGE", PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Write files to shared storage"))
        put("android.permission.MANAGE_EXTERNAL_STORAGE", PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.SPECIAL, "Full access to all files on the device"))
        put("android.permission.READ_MEDIA_IMAGES", PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Read your photos"))
        put("android.permission.READ_MEDIA_VIDEO", PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Read your videos"))
        put("android.permission.READ_MEDIA_AUDIO", PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Read your audio files"))
        put("android.permission.READ_MEDIA_VISUAL_USER_SELECTED", PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Access the photos you selected for the app"))
        put("android.permission.ACCESS_MEDIA_LOCATION", PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Read location data embedded in your photos"))

        // ---------------------------------------------------------------- BACKGROUND & PERSISTENCE
        put("android.permission.RECEIVE_BOOT_COMPLETED", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Start automatically when the device boots"))
        put("android.permission.FOREGROUND_SERVICE", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run as a foreground service"))
        put("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Avoid being killed to save battery"))
        put("android.permission.WAKE_LOCK", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Prevent the device from sleeping"))
        put("android.permission.SCHEDULE_EXACT_ALARM", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Schedule exact alarms"))
        put("android.permission.USE_EXACT_ALARM", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Schedule exact alarms without asking"))
        put("android.permission.FOREGROUND_SERVICE_DATA_SYNC", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run data sync in the foreground"))
        put("android.permission.FOREGROUND_SERVICE_LOCATION", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run location tracking in the foreground"))
        put("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run media playback in the foreground"))
        put("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Capture the screen in the foreground"))
        put("android.permission.FOREGROUND_SERVICE_CAMERA", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run the camera in a foreground service"))
        put("android.permission.FOREGROUND_SERVICE_MICROPHONE", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run the microphone in a foreground service"))
        put("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Stay connected to a device in the background"))
        put("android.permission.FOREGROUND_SERVICE_HEALTH", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run health tracking in the foreground"))
        put("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Deliver messages from the background"))
        put("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED", PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run an exempted foreground service"))

        // ---------------------------------------------------------------- NETWORK
        put("android.permission.INTERNET", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Full network access"))
        put("android.permission.ACCESS_NETWORK_STATE", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "View network connections"))
        put("android.permission.ACCESS_WIFI_STATE", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "View Wi-Fi connections"))
        put("android.permission.CHANGE_NETWORK_STATE", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Change network connectivity"))
        put("android.permission.CHANGE_WIFI_STATE", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Change Wi-Fi connectivity"))
        put("android.permission.BLUETOOTH", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Access Bluetooth"))
        put("android.permission.BLUETOOTH_ADMIN", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Manage Bluetooth settings"))
        put("android.permission.BLUETOOTH_CONNECT", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.DANGEROUS, "Connect to Bluetooth devices"))
        put("android.permission.BLUETOOTH_SCAN", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.DANGEROUS, "Scan for Bluetooth devices"))
        put("android.permission.NEARBY_WIFI_DEVICES", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.DANGEROUS, "Find nearby Wi-Fi devices"))
        put("android.permission.NFC", PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Access NFC"))

        // ---------------------------------------------------------------- OTHER (visible, not scored)
        put("android.permission.POST_NOTIFICATIONS", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.NORMAL, "Show notifications"))
        put("android.permission.QUERY_ALL_PACKAGES", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.NORMAL, "See all installed apps"))
        put("android.permission.VIBRATE", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.NORMAL, "Control vibration"))
        put("android.permission.BODY_SENSORS", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.DANGEROUS, "Read body sensors (e.g. heart rate)"))
        put("android.permission.ACTIVITY_RECOGNITION", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.DANGEROUS, "Recognise physical activity"))
        put("android.permission.USE_BIOMETRIC", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.NORMAL, "Use biometric unlock"))
        put("android.permission.USE_FINGERPRINT", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.NORMAL, "Use fingerprint sensor"))
        put("android.permission.READ_CALENDAR", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.DANGEROUS, "Read your calendar"))
        put("android.permission.WRITE_CALENDAR", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.DANGEROUS, "Modify your calendar"))
        put("android.permission.AD_ID", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.NORMAL, "Access the advertising ID"))
        put("android.permission.TURN_SCREEN_ON", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.NORMAL, "Turn the screen on"))
        put("android.permission.REORDER_TASKS", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.NORMAL, "Reorder app tasks"))
        put("android.permission.GET_TASKS", PermissionInfo(PermissionCategory.OTHER, ProtectionLevel.NORMAL, "See running app tasks"))
    }

    fun getPermissionInfo(permissionName: String): PermissionInfo? = permissionMap[permissionName]

    fun getCategory(permissionName: String): PermissionCategory =
        permissionMap[permissionName]?.category ?: guessCategory(permissionName)

    fun getProtectionLevel(permissionName: String): ProtectionLevel =
        permissionMap[permissionName]?.protectionLevel ?: ProtectionLevel.NORMAL

    fun getDescription(permissionName: String): String =
        permissionMap[permissionName]?.description ?: "Permission: ${permissionName.substringAfterLast('.')}"

    fun guessesCount(): Int = permissionMap.size

    /** Last-resort heuristic for permissions missing from the table. */
    fun guessCategory(permissionName: String): PermissionCategory {
        val name = permissionName.uppercase()
        return when {
            name.contains("SMS") || name.contains("MMS") -> PermissionCategory.SMS
            name.contains("CONTACT") || name.contains("CALL_LOG") -> PermissionCategory.CONTACTS_CALL_LOGS
            name.contains("CAMERA") || name.contains("AUDIO") || name.contains("MICROPHONE") -> PermissionCategory.CAMERA_MICROPHONE
            name.contains("LOCATION") -> PermissionCategory.LOCATION
            name.contains("PHONE") || name.contains("CALL") -> PermissionCategory.PHONE_IDENTITY
            name.contains("STORAGE") || name.contains("MEDIA") || name.contains("EXTERNAL") -> PermissionCategory.STORAGE_MEDIA
            name.contains("BOOT") || name.contains("FOREGROUND") || name.contains("WAKE") || name.contains("ALARM") -> PermissionCategory.BACKGROUND_PERSISTENCE
            name.contains("OVERLAY") || name.contains("ADMIN") || name.contains("INSTALL_PACKAGES") ||
                name.contains("NOTIFICATION_LISTENER") || name.contains("USAGE_STATS") -> PermissionCategory.SPECIAL_ACCESS
            name.contains("INTERNET") || name.contains("NETWORK") || name.contains("WIFI") ||
                name.contains("BLUETOOTH") || name.contains("NFC") -> PermissionCategory.NETWORK
            else -> PermissionCategory.OTHER
        }
    }
}
