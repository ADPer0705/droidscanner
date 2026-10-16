package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.ProtectionLevel

object PermissionMapper {
    // Map of permission name to (category, protection level, description)
    data class PermissionInfo(
        val category: PermissionCategory,
        val protectionLevel: ProtectionLevel,
        val description: String
    )
    
    val permissionMap: Map<String, PermissionInfo> = mapOf(
        // SPECIAL ACCESS
        "android.permission.SYSTEM_ALERT_WINDOW" to PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Draw overlays on top of other apps - can create fake login screens"),
        "android.permission.REQUEST_INSTALL_PACKAGES" to PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Install other apps without user going to Play Store"),
        "android.permission.PACKAGE_USAGE_STATS" to PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Monitor which apps you use and when"),
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Read screen content and perform actions - highest risk permission"),
        "android.permission.BIND_DEVICE_ADMIN" to PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Device admin can prevent uninstall and wipe device"),
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Read all notifications including OTPs and messages"),
        "android.permission.WRITE_SECURE_SETTINGS" to PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Modify secure system settings"),
        "android.permission.REQUEST_DELETE_PACKAGES" to PermissionInfo(PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL, "Request to delete other apps"),
        
        // SMS
        "android.permission.READ_SMS" to PermissionInfo(PermissionCategory.SMS, ProtectionLevel.DANGEROUS, "Read your text messages including OTPs"),
        "android.permission.RECEIVE_SMS" to PermissionInfo(PermissionCategory.SMS, ProtectionLevel.DANGEROUS, "Intercept incoming text messages"),
        "android.permission.SEND_SMS" to PermissionInfo(PermissionCategory.SMS, ProtectionLevel.DANGEROUS, "Send text messages - can send premium SMS"),
        "android.permission.RECEIVE_MMS" to PermissionInfo(PermissionCategory.SMS, ProtectionLevel.DANGEROUS, "Receive MMS messages"),
        "android.permission.RECEIVE_WAP_PUSH" to PermissionInfo(PermissionCategory.SMS, ProtectionLevel.DANGEROUS, "Receive WAP push messages"),
        
        // CONTACTS & CALL LOGS
        "android.permission.READ_CONTACTS" to PermissionInfo(PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS, "Read your contact list"),
        "android.permission.WRITE_CONTACTS" to PermissionInfo(PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS, "Modify your contacts"),
        "android.permission.READ_CALL_LOG" to PermissionInfo(PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS, "Read your call history"),
        "android.permission.WRITE_CALL_LOG" to PermissionInfo(PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS, "Modify your call history"),
        "android.permission.GET_ACCOUNTS" to PermissionInfo(PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS, "Access accounts on the device"),
        
        // CAMERA & MICROPHONE
        "android.permission.CAMERA" to PermissionInfo(PermissionCategory.CAMERA_MICROPHONE, ProtectionLevel.DANGEROUS, "Take photos and record video"),
        "android.permission.RECORD_AUDIO" to PermissionInfo(PermissionCategory.CAMERA_MICROPHONE, ProtectionLevel.DANGEROUS, "Record audio from microphone"),
        
        // LOCATION
        "android.permission.ACCESS_FINE_LOCATION" to PermissionInfo(PermissionCategory.LOCATION, ProtectionLevel.DANGEROUS, "Track your precise GPS location"),
        "android.permission.ACCESS_COARSE_LOCATION" to PermissionInfo(PermissionCategory.LOCATION, ProtectionLevel.DANGEROUS, "Track your approximate location"),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to PermissionInfo(PermissionCategory.LOCATION, ProtectionLevel.DANGEROUS, "Track your location even when app is closed"),
        "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS" to PermissionInfo(PermissionCategory.LOCATION, ProtectionLevel.NORMAL, "Access extra location provider commands"),
        
        // PHONE & IDENTITY
        "android.permission.READ_PHONE_STATE" to PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Read phone status and identity (IMEI, phone number)"),
        "android.permission.READ_PHONE_NUMBERS" to PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Read your phone numbers"),
        "android.permission.CALL_PHONE" to PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Make phone calls without your interaction"),
        "android.permission.ANSWER_PHONE_CALLS" to PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Answer incoming calls programmatically"),
        "android.permission.ADD_VOICEMAIL" to PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Add voicemail messages"),
        "android.permission.USE_SIP" to PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Make SIP calls"),
        "android.permission.PROCESS_OUTGOING_CALLS" to PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Intercept outgoing calls"),
        "android.permission.ACCEPT_HANDOVER" to PermissionInfo(PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS, "Continue a call from another app"),
        
        // STORAGE & MEDIA
        "android.permission.READ_EXTERNAL_STORAGE" to PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Read files from shared storage"),
        "android.permission.WRITE_EXTERNAL_STORAGE" to PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Write files to shared storage"),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.SPECIAL, "Full access to all files on device"),
        "android.permission.READ_MEDIA_IMAGES" to PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Read your photos"),
        "android.permission.READ_MEDIA_VIDEO" to PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Read your videos"),
        "android.permission.READ_MEDIA_AUDIO" to PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Read your audio files"),
        "android.permission.ACCESS_MEDIA_LOCATION" to PermissionInfo(PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS, "Access location data in your photos"),
        
        // BACKGROUND & PERSISTENCE
        "android.permission.RECEIVE_BOOT_COMPLETED" to PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Start automatically when device boots"),
        "android.permission.FOREGROUND_SERVICE" to PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run as a foreground service"),
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Avoid being killed to save battery"),
        "android.permission.WAKE_LOCK" to PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Prevent device from sleeping"),
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC" to PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run data sync in foreground"),
        "android.permission.FOREGROUND_SERVICE_LOCATION" to PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run location tracking in foreground"),
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" to PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run media playback in foreground"),
        "android.permission.FOREGROUND_SERVICE_CAMERA" to PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run camera in foreground service"),
        "android.permission.FOREGROUND_SERVICE_MICROPHONE" to PermissionInfo(PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL, "Run microphone in foreground service"),
        
        // NETWORK
        "android.permission.INTERNET" to PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Full network access"),
        "android.permission.ACCESS_NETWORK_STATE" to PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "View network connections"),
        "android.permission.ACCESS_WIFI_STATE" to PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "View Wi-Fi connections"),
        "android.permission.CHANGE_NETWORK_STATE" to PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Change network connectivity"),
        "android.permission.CHANGE_WIFI_STATE" to PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Change Wi-Fi connectivity"),
        "android.permission.BLUETOOTH" to PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Access Bluetooth"),
        "android.permission.BLUETOOTH_ADMIN" to PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Manage Bluetooth settings"),
        "android.permission.BLUETOOTH_CONNECT" to PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.DANGEROUS, "Connect to Bluetooth devices"),
        "android.permission.BLUETOOTH_SCAN" to PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.DANGEROUS, "Scan for Bluetooth devices"),
        "android.permission.NFC" to PermissionInfo(PermissionCategory.NETWORK, ProtectionLevel.NORMAL, "Access NFC")
    )
    
    fun getPermissionInfo(permissionName: String): PermissionInfo? = permissionMap[permissionName]
    
    fun getCategory(permissionName: String): PermissionCategory = 
        permissionMap[permissionName]?.category ?: PermissionCategory.NETWORK
    
    fun getProtectionLevel(permissionName: String): ProtectionLevel =
        permissionMap[permissionName]?.protectionLevel ?: ProtectionLevel.NORMAL
    
    fun getDescription(permissionName: String): String =
        permissionMap[permissionName]?.description ?: "Permission: ${permissionName.substringAfterLast('.')}"
}

