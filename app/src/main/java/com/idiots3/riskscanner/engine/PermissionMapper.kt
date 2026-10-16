package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.ProtectionLevel

object PermissionMapper {
    data class PermissionInfo(
        val category: PermissionCategory,
        val protectionLevel: ProtectionLevel,
        val description: String
    )

    val permissionMap: Map<String, PermissionInfo> = mapOf(
        "android.permission.ACCESS_ALL_DOWNLOADS" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to access all downloads."
        ),
        "android.permission.ACCESS_BLUETOOTH_SHARE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access bluetooth share."
        ),
        "android.permission.ACCESS_CACHE_FILESYSTEM" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.SIGNATURE,
            "Allows an application to read and write the cache file system."
        ),
        "android.permission.ACCESS_CHECKIN_PROPERTIES" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows read/write access to properties uploaded by the check-in service. Not for use by common applications."
        ),
        "android.permission.ACCESS_CONTENT_PROVIDERS_EXTERNALLY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access content providers externally."
        ),
        "android.permission.ACCESS_DOWNLOAD_MANAGER" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to access download manager."
        ),
        "android.permission.ACCESS_DOWNLOAD_MANAGER_ADVANCED" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to access download manager advanced."
        ),
        "android.permission.ACCESS_DRM_CERTIFICATES" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to access drm certificates."
        ),
        "android.permission.ACCESS_EPHEMERAL_APPS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access ephemeral apps."
        ),
        "android.permission.ACCESS_FM_RADIO" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access fm radio."
        ),
        "android.permission.ACCESS_INPUT_FLINGER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access input flinger."
        ),
        "android.permission.ACCESS_KEYGUARD_SECURE_STORAGE" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to access keyguard secure storage."
        ),
        "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS" to PermissionInfo(
            PermissionCategory.LOCATION,
            ProtectionLevel.DANGEROUS,
            "Access extra location provider commands. Malicious applications could use this to interfere with the operation of the GPS or other location sources."
        ),
        "android.permission.ACCESS_MOCK_LOCATION" to PermissionInfo(
            PermissionCategory.LOCATION,
            ProtectionLevel.DANGEROUS,
            "Create mock location sources for testing. Malicious applications can use this to override the location and/or status returned by real-location sources such as GPS or Network providers."
        ),
        "android.permission.ACCESS_MTP" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to access mtp."
        ),
        "android.permission.ACCESS_NETWORK_CONDITIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access network conditions."
        ),
        "android.permission.ACCESS_NETWORK_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to view the status of all networks."
        ),
        "android.permission.ACCESS_NOTIFICATIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access notifications."
        ),
        "android.permission.ACCESS_NOTIFICATION_POLICY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Marker permission for applications that wish to access notification policy."
        ),
        "android.permission.ACCESS_PDB_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access pdb state."
        ),
        "android.permission.ACCESS_SURFACE_FLINGER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows application to use SurfaceFlinger low-level features."
        ),
        "android.permission.ACCESS_VOICE_INTERACTION_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access voice interaction service."
        ),
        "android.permission.ACCESS_VR_MANAGER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access vr manager."
        ),
        "android.permission.ACCESS_WIFI_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to view the information about the status of Wi-Fi."
        ),
        "android.permission.ACCESS_WIMAX_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to access wimax state."
        ),
        "android.permission.ACCOUNT_MANAGER" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.SIGNATURE,
            "Allows an application to make calls to Account Authenticators."
        ),
        "android.permission.ALLOW_ANY_CODEC_FOR_PLAYBACK" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to allow any codec for playback."
        ),
        "android.permission.ASEC_ACCESS" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.SIGNATURE,
            "Allows the application to get information on internal storage."
        ),
        "android.permission.ASEC_CREATE" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.SIGNATURE,
            "Allows the application to create internal storage."
        ),
        "android.permission.ASEC_DESTROY" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.SIGNATURE,
            "Allows the application to destroy internal storage."
        ),
        "android.permission.ASEC_MOUNT_UNMOUNT" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.SIGNATURE,
            "Allows the application to mount/unmount internal storage."
        ),
        "android.permission.ASEC_RENAME" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.SIGNATURE,
            "Allows the application to rename internal storage."
        ),
        "android.permission.AUTHENTICATE_ACCOUNTS" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.DANGEROUS,
            "Allows an application to use the account authenticator capabilities of the Account Manager, including creating accounts as well as obtaining and setting their passwords."
        ),
        "android.permission.BACKUP" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows the application to control the system's back-up and restore mechanism. Not for use by common applications."
        ),
        "android.permission.BATTERY_STATS" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.SIGNATURE,
            "Allows the modification of collected battery statistics. Not for use by common applications."
        ),
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Must be required by an AccessibilityService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_APPWIDGET" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows the application to tell the system which widgets can be used by which application. With this permission, applications can give access to personal data to other applications. Not for use by common applications."
        ),
        "android.permission.BIND_CARRIER_MESSAGING_SERVICE" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.SIGNATURE,
            "The system process that is allowed to bind to services in carrier apps will have this permission."
        ),
        "android.permission.BIND_CARRIER_SERVICES" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.SIGNATURE,
            "The system process that is allowed to bind to services in carrier apps will have this permission. Carrier apps should use this permission to protect their services that only the system is allowed to bind to."
        ),
        "android.permission.BIND_CHOOSER_TARGET_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by a ChooserTargetService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_CONDITION_PROVIDER_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by a ConditionProviderService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_CONNECTION_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind connection service."
        ),
        "android.permission.BIND_DEVICE_ADMIN" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows the holder to send intents to a device administrator. Should never be needed for common applications."
        ),
        "android.permission.BIND_DIRECTORY_SEARCH" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind directory search."
        ),
        "android.permission.BIND_DREAM_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by an DreamService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_INCALL_SERVICE" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.SIGNATURE,
            "Must be required by a InCallService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_INPUT_METHOD" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows the holder to bind to the top-level interface of an input method. Should never be needed for common applications."
        ),
        "android.permission.BIND_INTENT_FILTER_VERIFIER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind intent filter verifier."
        ),
        "android.permission.BIND_JOB_SERVICE" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.NORMAL,
            "Allows application to bind job service."
        ),
        "android.permission.BIND_KEYGUARD_APPWIDGET" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind keyguard appwidget."
        ),
        "android.permission.BIND_MIDI_DEVICE_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by an MidiDeviceService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_NFC_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by a HostApduService or OffHostApduService to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Must be required by an NotificationListenerService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_NOTIFICATION_RANKER_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind notification ranker service."
        ),
        "android.permission.BIND_PACKAGE_VERIFIER" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows application to bind package verifier."
        ),
        "android.permission.BIND_PRINT_RECOMMENDATION_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind print recommendation service."
        ),
        "android.permission.BIND_PRINT_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by a PrintService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_PRINT_SPOOLER_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind print spooler service."
        ),
        "android.permission.BIND_QUICK_SETTINGS_TILE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to bind to third party quick settings tiles."
        ),
        "android.permission.BIND_REMOTEVIEWS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by a RemoteViewsService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_REMOTE_DISPLAY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind remote display."
        ),
        "android.permission.BIND_ROUTE_PROVIDER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind route provider."
        ),
        "android.permission.BIND_RUNTIME_PERMISSION_PRESENTER_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind runtime permission presenter service."
        ),
        "android.permission.BIND_SCREENING_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by a CallScreeningService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_TELECOM_CONNECTION_SERVICE" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.SIGNATURE,
            "Must be required by a ConnectionService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_TEXT_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by a TextService (e.g. SpellCheckerService) to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_TRUST_AGENT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind trust agent."
        ),
        "android.permission.BIND_TV_INPUT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by a TvInputService to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_TV_REMOTE_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bind tv remote service."
        ),
        "android.permission.BIND_VOICE_INTERACTION" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by a VoiceInteractionService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_VPN_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by a VpnService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_VR_LISTENER_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Must be required by an VrListenerService, to ensure that only the system can bind to it."
        ),
        "android.permission.BIND_WALLPAPER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows the holder to bind to the top-level interface of wallpaper. Should never be needed for common applications."
        ),
        "android.permission.BLUETOOTH" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows applications to connect to paired bluetooth devices."
        ),
        "android.permission.BLUETOOTH_ADMIN" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows applications to discover and pair bluetooth devices."
        ),
        "android.permission.BLUETOOTH_MAP" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bluetooth map."
        ),
        "android.permission.BLUETOOTH_PRIVILEGED" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows applications to pair bluetooth devices without user interaction, and to allow or disallow phonebook access or message access. This is not available to third party applications."
        ),
        "android.permission.BLUETOOTH_STACK" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to bluetooth stack."
        ),
        "android.permission.BRICK" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows the application to disable the entire phone permanently. This is very dangerous."
        ),
        "android.permission.BROADCAST_CALLLOG_INFO" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.NORMAL,
            "Allows application to broadcast calllog info."
        ),
        "android.permission.BROADCAST_NETWORK_PRIVILEGED" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to broadcast network privileged."
        ),
        "android.permission.BROADCAST_PACKAGE_REMOVED" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to broadcast a notification that an application package has been removed. Malicious applications may use this to kill any other application running."
        ),
        "android.permission.BROADCAST_PHONE_ACCOUNT_REGISTRATION" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.NORMAL,
            "Allows application to broadcast phone account registration."
        ),
        "android.permission.BROADCAST_SMS" to PermissionInfo(
            PermissionCategory.SMS,
            ProtectionLevel.SIGNATURE,
            "Allows an application to broadcast a notification that an SMS message has been received. Malicious applications may use this to forge incoming SMS messages."
        ),
        "android.permission.BROADCAST_STICKY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to send sticky broadcasts, which remain after the broadcast ends. Malicious applications can make the phone slow or unstable by causing it to use too much memory."
        ),
        "android.permission.BROADCAST_WAP_PUSH" to PermissionInfo(
            PermissionCategory.SMS,
            ProtectionLevel.SIGNATURE,
            "Allows an application to broadcast a notification that a WAP-PUSH message has been received. Malicious applications may use this to forge MMS message receipt or to replace the content of any web page silently with malicious variants."
        ),
        "android.permission.CACHE_CONTENT" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to cache content."
        ),
        "android.permission.CALL_PRIVILEGED" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.SIGNATURE,
            "Allows the application to call any phone number, including emergency numbers, without your intervention. Malicious applications may place unnecessary and illegal calls to emergency services."
        ),
        "android.permission.CAMERA_DISABLE_TRANSMIT_LED" to PermissionInfo(
            PermissionCategory.CAMERA_MICROPHONE,
            ProtectionLevel.DANGEROUS,
            "Allows application to camera disable transmit led."
        ),
        "android.permission.CAMERA_SEND_SYSTEM_EVENTS" to PermissionInfo(
            PermissionCategory.CAMERA_MICROPHONE,
            ProtectionLevel.DANGEROUS,
            "Allows application to camera send system events."
        ),
        "android.permission.CAPTURE_AUDIO_HOTWORD" to PermissionInfo(
            PermissionCategory.CAMERA_MICROPHONE,
            ProtectionLevel.DANGEROUS,
            "Allows application to capture audio hotword."
        ),
        "android.permission.CAPTURE_AUDIO_OUTPUT" to PermissionInfo(
            PermissionCategory.CAMERA_MICROPHONE,
            ProtectionLevel.SIGNATURE,
            "Allows an application to capture audio output."
        ),
        "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT" to PermissionInfo(
            PermissionCategory.CAMERA_MICROPHONE,
            ProtectionLevel.NORMAL,
            "Allows an application to capture secure video output."
        ),
        "android.permission.CAPTURE_TV_INPUT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to capture tv input."
        ),
        "android.permission.CAPTURE_VIDEO_OUTPUT" to PermissionInfo(
            PermissionCategory.CAMERA_MICROPHONE,
            ProtectionLevel.NORMAL,
            "Allows an application to capture video output."
        ),
        "android.permission.CARRIER_FILTER_SMS" to PermissionInfo(
            PermissionCategory.SMS,
            ProtectionLevel.DANGEROUS,
            "Allows application to carrier filter sms."
        ),
        "android.permission.CHANGE_APP_IDLE_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to change app idle state."
        ),
        "android.permission.CHANGE_BACKGROUND_DATA_SETTING" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to change the background data usage setting."
        ),
        "android.permission.CHANGE_COMPONENT_ENABLED_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to change whether or not a component of another application is enabled. Malicious applications can use this to disable important phone capabilities. It is important to be careful with permission, as it is possible to bring application components into an unusable, inconsistent or unstable state."
        ),
        "android.permission.CHANGE_CONFIGURATION" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to change the current configuration, such as the locale or overall font size."
        ),
        "android.permission.CHANGE_DEVICE_IDLE_TEMP_WHITELIST" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to change device idle temp whitelist."
        ),
        "android.permission.CHANGE_NETWORK_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows applications to change network connectivity state."
        ),
        "android.permission.CHANGE_WIFI_MULTICAST_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to receive packets not directly addressed to your device. This can be useful when discovering services offered nearby. It uses more power than the non-multicast mode."
        ),
        "android.permission.CHANGE_WIFI_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to connect to and disconnect from Wi-Fi access points and to make changes to configured Wi-Fi networks."
        ),
        "android.permission.CHANGE_WIMAX_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to change wimax state."
        ),
        "android.permission.CLEAR_APP_CACHE" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.SIGNATURE,
            "Allows an application to free phone storage by deleting files in application cache directory. Access is usually very restricted to system process."
        ),
        "android.permission.CLEAR_APP_GRANTED_URI_PERMISSIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to clear app granted uri permissions."
        ),
        "android.permission.CLEAR_APP_USER_DATA" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to clear user data."
        ),
        "android.permission.CONFIGURE_DISPLAY_COLOR_TRANSFORM" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to configure display color transform."
        ),
        "android.permission.CONFIGURE_WIFI_DISPLAY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to configure and connect to Wifi displays"
        ),
        "android.permission.CONFIRM_FULL_BACKUP" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to confirm full backup."
        ),
        "android.permission.CONNECTIVITY_INTERNAL" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to connectivity internal."
        ),
        "android.permission.CONTROL_INCALL_EXPERIENCE" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.NORMAL,
            "Allows application to control incall experience."
        ),
        "android.permission.CONTROL_KEYGUARD" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to control keyguard."
        ),
        "android.permission.CONTROL_LOCATION_UPDATES" to PermissionInfo(
            PermissionCategory.LOCATION,
            ProtectionLevel.SIGNATURE,
            "Allows enabling/disabling location update notifications from the radio. Not for use by common applications."
        ),
        "android.permission.CONTROL_VPN" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to control vpn."
        ),
        "android.permission.CONTROL_WIFI_DISPLAY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to control wifi display."
        ),
        "android.permission.COPY_PROTECTED_DATA" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows to invoke default container service to copy content. Not for use by common applications."
        ),
        "android.permission.CREATE_USERS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to create users."
        ),
        "android.permission.CRYPT_KEEPER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to crypt keeper."
        ),
        "android.permission.DELETE_CACHE_FILES" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.SIGNATURE,
            "Allows an application to delete cache files."
        ),
        "android.permission.DELETE_PACKAGES" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows an application to delete Android packages. Malicious applications can use this to delete important applications."
        ),
        "android.permission.DEVICE_POWER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows the application to turn the phone on or off."
        ),
        "android.permission.DIAGNOSTIC" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to read and write to any resource owned by the diag group; for example, files in /dev. This could potentially affect system stability and security. This should ONLY be used for hardware-specific diagnostics by the manufacturer or operator."
        ),
        "android.permission.DISABLE_KEYGUARD" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows applications to disable the keyguard if it is not secure."
        ),
        "android.permission.DISPATCH_NFC_MESSAGE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to dispatch nfc message."
        ),
        "android.permission.DISPATCH_PROVISIONING_MESSAGE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to dispatch provisioning message."
        ),
        "android.permission.DOWNLOAD_CACHE_NON_PURGEABLE" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to download cache non purgeable."
        ),
        "android.permission.DUMP" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows application to retrieve internal status of the system. Malicious applications may retrieve a wide variety of private and secure information that they should never commonly need."
        ),
        "android.permission.DVB_DEVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to dvb device."
        ),
        "android.permission.EXPAND_STATUS_BAR" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to expand or collapse the status bar."
        ),
        "android.permission.FACTORY_TEST" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Run as a low-level manufacturer test, allowing complete access to the phone hardware. Only available when a phone is running in manufacturer test mode."
        ),
        "android.permission.FILTER_EVENTS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to filter events."
        ),
        "android.permission.FLASHLIGHT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows the application to control the flashlight."
        ),
        "android.permission.FORCE_BACK" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to force any activity that is in the foreground to close and go back. Should never be needed for common applications."
        ),
        "android.permission.FORCE_STOP_PACKAGES" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows an application to stop other applications forcibly."
        ),
        "android.permission.FRAME_STATS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to frame stats."
        ),
        "android.permission.FREEZE_SCREEN" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to freeze screen."
        ),
        "android.permission.GET_ACCOUNTS_PRIVILEGED" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.SIGNATURE,
            "Allows access to the list of accounts in the Accounts Service."
        ),
        "android.permission.GET_APP_GRANTED_URI_PERMISSIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to get app granted uri permissions."
        ),
        "android.permission.GET_APP_OPS_STATS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to get app ops stats."
        ),
        "android.permission.GET_DETAILED_TASKS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to get detailed tasks."
        ),
        "android.permission.GET_INTENT_SENDER_INTENT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to get intent sender intent."
        ),
        "android.permission.GET_PACKAGE_IMPORTANCE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to get package importance."
        ),
        "android.permission.GET_PACKAGE_SIZE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to find out the space used by any package."
        ),
        "android.permission.GET_PASSWORD" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to get password."
        ),
        "android.permission.GET_PROCESS_STATE_AND_OOM_SCORE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to get process state and oom score."
        ),
        "android.permission.GET_TASKS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows application to retrieve information about currently and recently running tasks. May allow malicious applications to discover private information about other applications."
        ),
        "android.permission.GET_TOP_ACTIVITY_INFO" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to get top activity info."
        ),
        "android.permission.GLOBAL_SEARCH" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "This permission can be used on content providers to allow the global search system to access their data. Typically it used when the provider has some permissions protecting it (which global search would not be expected to hold), and added as a read-only permission to the path in the provider where global search queries are performed. This permission can not be held by regular applications; it is used by applications to protect themselves from everyone else besides global search."
        ),
        "android.permission.GLOBAL_SEARCH_CONTROL" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Permission without a specified role or function."
        ),
        "android.permission.GRANT_RUNTIME_PERMISSIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to grant runtime permissions."
        ),
        "android.permission.HARDWARE_TEST" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows the application to control various peripherals for the purpose of hardware testing."
        ),
        "android.permission.HDMI_CEC" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to hdmi cec."
        ),
        "android.permission.INJECT_EVENTS" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows an application to deliver its own input events (key presses, etc.) to other applications. Malicious applications can use this to take over the phone."
        ),
        "android.permission.INSTALL_GRANT_RUNTIME_PERMISSIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to install grant runtime permissions."
        ),
        "android.permission.INSTALL_LOCATION_PROVIDER" to PermissionInfo(
            PermissionCategory.LOCATION,
            ProtectionLevel.SIGNATURE,
            "Create mock location sources for testing. Malicious applications can use this to override the location and/or status returned by real-location sources such as GPS or Network providers, or monitor and report your location to an external source."
        ),
        "android.permission.INSTALL_PACKAGES" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows an application to install new or updated Android packages. Malicious applications can use this to add new applications with arbitrarily powerful permissions."
        ),
        "android.permission.INTENT_FILTER_VERIFICATION_AGENT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to intent filter verification agent."
        ),
        "android.permission.INTERACT_ACROSS_USERS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to interact across users."
        ),
        "android.permission.INTERACT_ACROSS_USERS_FULL" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to interact across users full."
        ),
        "android.permission.INTERNAL_SYSTEM_WINDOW" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows the creation of windows that are intended to be used by the internal system user interface. Not for use by common applications."
        ),
        "android.permission.INTERNET" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to create network sockets."
        ),
        "android.permission.INVOKE_CARRIER_SETUP" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.NORMAL,
            "Allows application to invoke carrier setup."
        ),
        "android.permission.KILL_BACKGROUND_PROCESSES" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.NORMAL,
            "Allows an application to kill background processes of other applications, even if memory is not low."
        ),
        "android.permission.KILL_UID" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to kill uid."
        ),
        "android.permission.LAUNCH_TRUST_AGENT_SETTINGS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to launch trust agent settings."
        ),
        "android.permission.LOCAL_MAC_ADDRESS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to local mac address."
        ),
        "android.permission.LOCATION_HARDWARE" to PermissionInfo(
            PermissionCategory.LOCATION,
            ProtectionLevel.DANGEROUS,
            "Allows an application to use location features in hardware, such as the geofencing api."
        ),
        "android.permission.LOOP_RADIO" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to loop radio."
        ),
        "android.permission.MANAGE_ACCOUNTS" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.DANGEROUS,
            "Allows an application to perform operations like adding and removing accounts and deleting their password."
        ),
        "android.permission.MANAGE_ACTIVITY_STACKS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to manage activity stacks."
        ),
        "android.permission.MANAGE_APP_OPS_RESTRICTIONS" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows application to manage app ops restrictions."
        ),
        "android.permission.MANAGE_APP_TOKENS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows applications to create and manage their own tokens, bypassing their common Z-ordering. Should never be needed for common applications."
        ),
        "android.permission.MANAGE_CA_CERTIFICATES" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to manage ca certificates."
        ),
        "android.permission.MANAGE_DEVICE_ADMINS" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows application to manage device admins."
        ),
        "android.permission.MANAGE_DOCUMENTS" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.SIGNATURE,
            "Allows an application to manage access to documents, usually as part of a document picker."
        ),
        "android.permission.MANAGE_FINGERPRINT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to manage fingerprint."
        ),
        "android.permission.MANAGE_MEDIA_PROJECTION" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to manage media projection."
        ),
        "android.permission.MANAGE_NETWORK_POLICY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to manage network policy."
        ),
        "android.permission.MANAGE_NOTIFICATIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to manage notifications."
        ),
        "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to manage profile and device owners."
        ),
        "android.permission.MANAGE_SOUND_TRIGGER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to manage sound trigger."
        ),
        "android.permission.MANAGE_USB" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to manage usb."
        ),
        "android.permission.MANAGE_USERS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to manage users."
        ),
        "android.permission.MANAGE_VOICE_KEYPHRASES" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to manage voice keyphrases."
        ),
        "android.permission.MASTER_CLEAR" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows an application to completely reset the system to its factory settings, erasing all data, configuration and installed applications."
        ),
        "android.permission.MEDIA_CONTENT_CONTROL" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows an application to know what content is playing and control its playback."
        ),
        "android.permission.MODIFY_APPWIDGET_BIND_PERMISSIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to modify appwidget bind permissions."
        ),
        "android.permission.MODIFY_AUDIO_ROUTING" to PermissionInfo(
            PermissionCategory.CAMERA_MICROPHONE,
            ProtectionLevel.DANGEROUS,
            "Allows application to modify audio routing."
        ),
        "android.permission.MODIFY_AUDIO_SETTINGS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows application to modify global audio settings, such as volume and routing."
        ),
        "android.permission.MODIFY_CELL_BROADCASTS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to modify cell broadcasts."
        ),
        "android.permission.MODIFY_DAY_NIGHT_MODE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to modify day night mode."
        ),
        "android.permission.MODIFY_NETWORK_ACCOUNTING" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to modify network accounting."
        ),
        "android.permission.MODIFY_PARENTAL_CONTROLS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to modify parental controls."
        ),
        "android.permission.MODIFY_PHONE_STATE" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.SIGNATURE,
            "Allows the application to control the phone features of the device. An application with this permission can switch networks, turn the phone radio on and off and the like, without ever notifying you."
        ),
        "android.permission.MOUNT_FORMAT_FILESYSTEMS" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.DANGEROUS,
            "Allows the application to format removable storage."
        ),
        "android.permission.MOUNT_UNMOUNT_FILESYSTEMS" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.DANGEROUS,
            "Allows the application to mount and unmount file systems for removable storage."
        ),
        "android.permission.MOVE_PACKAGE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to move application resources from internal to external media and vice versa."
        ),
        "android.permission.NET_ADMIN" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to net admin."
        ),
        "android.permission.NET_TUNNELING" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to net tunneling."
        ),
        "android.permission.NFC" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to communicate with Near-Field Communication (NFC) tags, cards and readers."
        ),
        "android.permission.NFC_HANDOVER_STATUS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to nfc handover status."
        ),
        "android.permission.NOTIFY_PENDING_SYSTEM_UPDATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to notify pending system update."
        ),
        "android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to observe grant revoke permissions."
        ),
        "android.permission.OEM_UNLOCK_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to oem unlock state."
        ),
        "android.permission.OVERRIDE_WIFI_CONFIG" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to modify any wifi configuration, even if created by another application. Once reconfigured the original creator cannot make any further modifications. Not for use by third-party applications."
        ),
        "android.permission.PACKAGE_USAGE_STATS" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows the modification of collected component usage statistics. Not for use by common applications."
        ),
        "android.permission.PACKAGE_VERIFICATION_AGENT" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows application to package verification agent."
        ),
        "android.permission.PACKET_KEEPALIVE_OFFLOAD" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to packet keepalive offload."
        ),
        "android.permission.PEERS_MAC_ADDRESS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to peers mac address."
        ),
        "android.permission.PERFORM_CDMA_PROVISIONING" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows the application to start CDMA provisioning. Malicious applications may start CDMA provisioning unnecessarily."
        ),
        "android.permission.PERFORM_SIM_ACTIVATION" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.NORMAL,
            "Allows application to perform sim activation."
        ),
        "android.permission.PERSISTENT_ACTIVITY" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.DANGEROUS,
            "Allows an application to make parts of itself persistent, so that the system can't use it for other applications."
        ),
        "android.permission.PROCESS_CALLLOG_INFO" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.NORMAL,
            "Allows application to process calllog info."
        ),
        "android.permission.PROCESS_PHONE_ACCOUNT_REGISTRATION" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.NORMAL,
            "Allows application to process phone account registration."
        ),
        "android.permission.PROVIDE_TRUST_AGENT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to provide trust agent."
        ),
        "android.permission.QUERY_DO_NOT_ASK_CREDENTIALS_ON_BOOT" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.NORMAL,
            "Allows application to query do not ask credentials on boot."
        ),
        "android.permission.READ_BLOCKED_NUMBERS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to read blocked numbers."
        ),
        "android.permission.READ_DREAM_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to read dream state."
        ),
        "android.permission.READ_FRAME_BUFFER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows application to read the content of the frame buffer."
        ),
        "android.permission.READ_INPUT_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows applications to watch the keys that you press even when interacting with another application (such as entering a password). Should never be needed for common applications."
        ),
        "android.permission.READ_INSTALL_SESSIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to read install sessions."
        ),
        "android.permission.READ_LOGS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to read from the system's various log files. This allows it to discover general information about what you are doing with the phone, potentially including personal or private information."
        ),
        "android.permission.READ_NETWORK_USAGE_HISTORY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to read network usage history."
        ),
        "android.permission.READ_OEM_UNLOCK_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to read oem unlock state."
        ),
        "android.permission.READ_PRECISE_PHONE_STATE" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.DANGEROUS,
            "Allows read only access to precise phone state. Allows reading of detailed information about phone state for special-use applications such as dialers, carrier applications, or ims applications."
        ),
        "android.permission.READ_PRIVILEGED_PHONE_STATE" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.NORMAL,
            "Allows application to read privileged phone state."
        ),
        "android.permission.READ_PROFILE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to read the user's personal profile data."
        ),
        "android.permission.READ_SEARCH_INDEXABLES" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to read search indexables."
        ),
        "android.permission.READ_SOCIAL_STREAM" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.DANGEROUS,
            "Allows an application to read from the user's social stream."
        ),
        "android.permission.READ_SYNC_SETTINGS" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.NORMAL,
            "Allows an application to read the sync settings, such as whether sync is enabled for Contacts."
        ),
        "android.permission.READ_SYNC_STATS" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.NORMAL,
            "Allows an application to read the sync stats; e.g. the history of syncs that have occurred."
        ),
        "android.permission.READ_USER_DICTIONARY" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.DANGEROUS,
            "Allows an application to read any private words, names and phrases that the user may have stored in the user dictionary."
        ),
        "android.permission.READ_WIFI_CREDENTIAL" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to read wifi credential."
        ),
        "android.permission.REAL_GET_TASKS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to real get tasks."
        ),
        "android.permission.REBOOT" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows the application to force the phone to reboot."
        ),
        "android.permission.RECEIVE_BLUETOOTH_MAP" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to receive bluetooth map."
        ),
        "android.permission.RECEIVE_BOOT_COMPLETED" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.NORMAL,
            "Allows an application to start itself as soon as the system has finished booting. This can make it take longer to start the phone and allow the application to slow down the overall phone by always running."
        ),
        "android.permission.RECEIVE_DATA_ACTIVITY_CHANGE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to receive data activity change."
        ),
        "android.permission.RECEIVE_EMERGENCY_BROADCAST" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to receive emergency broadcast."
        ),
        "android.permission.RECEIVE_MEDIA_RESOURCE_USAGE" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to receive media resource usage."
        ),
        "android.permission.RECEIVE_STK_COMMANDS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to receive stk commands."
        ),
        "android.permission.RECEIVE_WIFI_CREDENTIAL_CHANGE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to receive wifi credential change."
        ),
        "android.permission.RECOVERY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to recovery."
        ),
        "android.permission.REGISTER_CALL_PROVIDER" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.NORMAL,
            "Allows application to register call provider."
        ),
        "android.permission.REGISTER_CONNECTION_MANAGER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to register connection manager."
        ),
        "android.permission.REGISTER_SIM_SUBSCRIPTION" to PermissionInfo(
            PermissionCategory.PHONE_IDENTITY,
            ProtectionLevel.NORMAL,
            "Allows application to register sim subscription."
        ),
        "android.permission.REGISTER_WINDOW_MANAGER_LISTENERS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to register window manager listeners."
        ),
        "android.permission.REMOTE_AUDIO_PLAYBACK" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows application to remote audio playback."
        ),
        "android.permission.REMOVE_DRM_CERTIFICATES" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to remove drm certificates."
        ),
        "android.permission.REMOVE_TASKS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to remove tasks."
        ),
        "android.permission.REORDER_TASKS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to move tasks to the foreground and background. Malicious applications can force themselves to the front without your control."
        ),
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.NORMAL,
            "Permission an application must hold in order to use Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS."
        ),
        "android.permission.REQUEST_INSTALL_PACKAGES" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Malicious applications can use this to try and trick users into installing additional malicious packages."
        ),
        "android.permission.RESET_FINGERPRINT_LOCKOUT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to reset fingerprint lockout."
        ),
        "android.permission.RESET_SHORTCUT_MANAGER_THROTTLING" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to reset shortcut manager throttling."
        ),
        "android.permission.RESTART_PACKAGES" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.NORMAL,
            "Allows an application to kill background processes of other applications, even if memory is not low."
        ),
        "android.permission.RETRIEVE_WINDOW_CONTENT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to retrieve window content."
        ),
        "android.permission.RETRIEVE_WINDOW_TOKEN" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to retrieve window token."
        ),
        "android.permission.REVOKE_RUNTIME_PERMISSIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to revoke runtime permissions."
        ),
        "android.permission.SCORE_NETWORKS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to score networks."
        ),
        "android.permission.SEND_CALL_LOG_CHANGE" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.DANGEROUS,
            "Allows application to send call log change."
        ),
        "android.permission.SEND_DOWNLOAD_COMPLETED_INTENTS" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to send download completed intents."
        ),
        "android.permission.SEND_RESPOND_VIA_MESSAGE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application (Phone) to send a request to other applications to handle the respond-via-message action during incoming calls."
        ),
        "android.permission.SEND_SMS_NO_CONFIRMATION" to PermissionInfo(
            PermissionCategory.SMS,
            ProtectionLevel.DANGEROUS,
            "send SMS messages via the Messaging app with no user input or confirmation."
        ),
        "android.permission.SERIAL_PORT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to serial port."
        ),
        "android.permission.SET_ACTIVITY_WATCHER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to monitor and control how the system launches activities. Malicious applications may compromise the system completely. This permission is needed only for development, never for common phone usage."
        ),
        "android.permission.SET_ALWAYS_FINISH" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to control whether activities are always finished as soon as they go to the background. Never needed for common applications."
        ),
        "android.permission.SET_ANIMATION_SCALE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to change the global animation speed (faster or slower animations) at any time."
        ),
        "android.permission.SET_DEBUG_APP" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to turn on debugging for another application. Malicious applications can use this to kill other applications."
        ),
        "android.permission.SET_INPUT_CALIBRATION" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to set input calibration."
        ),
        "android.permission.SET_KEYBOARD_LAYOUT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to set keyboard layout."
        ),
        "android.permission.SET_ORIENTATION" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to change the rotation of the screen at any time. Should never be needed for common applications."
        ),
        "android.permission.SET_POINTER_SPEED" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to set pointer speed."
        ),
        "android.permission.SET_PREFERRED_APPLICATIONS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to modify your preferred applications. This can allow malicious applications to silently change the applications that are run, spoofing your existing applications to collect private data from you."
        ),
        "android.permission.SET_PROCESS_LIMIT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to control the maximum number of processes that will run. Never needed for common applications."
        ),
        "android.permission.SET_SCREEN_COMPATIBILITY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to set screen compatibility."
        ),
        "android.permission.SET_TIME" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to change the phone's clock time."
        ),
        "android.permission.SET_TIME_ZONE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to change the phone's time zone."
        ),
        "android.permission.SET_WALLPAPER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows the application to set the system wallpaper."
        ),
        "android.permission.SET_WALLPAPER_COMPONENT" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows setting of wallpaper components."
        ),
        "android.permission.SET_WALLPAPER_HINTS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows the application to set the system wallpaper size hints."
        ),
        "android.permission.SHUTDOWN" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Puts the activity manager into a shut-down state. Does not perform a complete shut down."
        ),
        "android.permission.SIGNAL_PERSISTENT_PROCESSES" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.DANGEROUS,
            "Allows application to request that the supplied signal be sent to all persistent processes."
        ),
        "android.permission.START_ANY_ACTIVITY" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows application to start any activity."
        ),
        "android.permission.START_PRINT_SERVICE_CONFIG_ACTIVITY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to start print service config activity."
        ),
        "android.permission.START_TASKS_FROM_RECENTS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to start tasks from recents."
        ),
        "android.permission.STATUS_BAR" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows application to disable the status bar or add and remove system icons."
        ),
        "android.permission.STATUS_BAR_SERVICE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows the application to be the status bar."
        ),
        "android.permission.STOP_APP_SWITCHES" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Prevents the user from switching to another application."
        ),
        "android.permission.STORAGE_INTERNAL" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to storage internal."
        ),
        "android.permission.SUBSCRIBED_FEEDS_READ" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows an application to receive details about the currently synced feeds."
        ),
        "android.permission.SUBSCRIBED_FEEDS_WRITE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to modify your currently synced feeds. This could allow a malicious application to change your synced feeds."
        ),
        "android.permission.SUBSTITUTE_NOTIFICATION_APP_NAME" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to substitute notification app name."
        ),
        "android.permission.SYSTEM_ALERT_WINDOW" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows an application to show system-alert windows. Malicious applications can take over the entire screen of the phone."
        ),
        "android.permission.TABLET_MODE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to tablet mode."
        ),
        "android.permission.TEMPORARY_ENABLE_ACCESSIBILITY" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows application to temporary enable accessibility."
        ),
        "android.permission.TETHER_PRIVILEGED" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to tether privileged."
        ),
        "android.permission.TRANSMIT_IR" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows using the device's IR transmitter, if available."
        ),
        "android.permission.TRUST_LISTENER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to trust listener."
        ),
        "android.permission.TV_INPUT_HARDWARE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to tv input hardware."
        ),
        "android.permission.TV_VIRTUAL_REMOTE_CONTROLLER" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to tv virtual remote controller."
        ),
        "android.permission.UPDATE_APP_OPS_STATS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to update app ops stats."
        ),
        "android.permission.UPDATE_CONFIG" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to update config."
        ),
        "android.permission.UPDATE_DEVICE_STATS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows the modification of collected battery statistics. Not for use by common applications."
        ),
        "android.permission.UPDATE_LOCK" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to update lock."
        ),
        "android.permission.UPDATE_LOCK_TASK_PACKAGES" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to update lock task packages."
        ),
        "android.permission.USER_ACTIVITY" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to user activity."
        ),
        "android.permission.USE_CREDENTIALS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to request authentication tokens."
        ),
        "android.permission.VIBRATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows the application to control the vibrator."
        ),
        "android.permission.WAKE_LOCK" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.NORMAL,
            "Allows an application to prevent the phone from going to sleep."
        ),
        "android.permission.WRITE_APN_SETTINGS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to modify the APN settings, such as Proxy and Port of any APN."
        ),
        "android.permission.WRITE_BLOCKED_NUMBERS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to write blocked numbers."
        ),
        "android.permission.WRITE_DREAM_STATE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.NORMAL,
            "Allows application to write dream state."
        ),
        "android.permission.WRITE_GSERVICES" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.SIGNATURE,
            "Allows an application to modify the Google services map. Not for use by common applications."
        ),
        "android.permission.WRITE_MEDIA_STORAGE" to PermissionInfo(
            PermissionCategory.STORAGE_MEDIA,
            ProtectionLevel.NORMAL,
            "Allows application to write media storage."
        ),
        "android.permission.WRITE_PROFILE" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to write (but not read) the user's personal profile data."
        ),
        "android.permission.WRITE_SECURE_SETTINGS" to PermissionInfo(
            PermissionCategory.SPECIAL_ACCESS,
            ProtectionLevel.SPECIAL,
            "Allows an application to modify the system's secure settings data. Not for use by common applications."
        ),
        "android.permission.WRITE_SETTINGS" to PermissionInfo(
            PermissionCategory.NETWORK,
            ProtectionLevel.DANGEROUS,
            "Allows an application to modify the system's settings data. Malicious applications can corrupt your system's configuration."
        ),
        "android.permission.WRITE_SMS" to PermissionInfo(
            PermissionCategory.SMS,
            ProtectionLevel.DANGEROUS,
            "Allows application to write to SMS messages stored on your phone or SIM card. Malicious applications may delete your messages."
        ),
        "android.permission.WRITE_SOCIAL_STREAM" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.DANGEROUS,
            "Allows an application to write (but not read) the user's social stream data."
        ),
        "android.permission.WRITE_SYNC_SETTINGS" to PermissionInfo(
            PermissionCategory.BACKGROUND_PERSISTENCE,
            ProtectionLevel.NORMAL,
            "Allows an application to modify the sync settings, such as whether sync is enabled for Contacts."
        ),
        "android.permission.WRITE_USER_DICTIONARY" to PermissionInfo(
            PermissionCategory.CONTACTS_CALL_LOGS,
            ProtectionLevel.NORMAL,
            "Allows an application to write new words into the user dictionary."
        ),
    )

    fun getPermissionInfo(permissionName: String): PermissionInfo? = permissionMap[permissionName]

    fun getCategory(permissionName: String): PermissionCategory =
        permissionMap[permissionName]?.category ?: guessFallbackCategory(permissionName)

    fun getProtectionLevel(permissionName: String): ProtectionLevel =
        permissionMap[permissionName]?.protectionLevel ?: ProtectionLevel.NORMAL

    fun getDescription(permissionName: String): String =
        permissionMap[permissionName]?.description ?: "Permission: ${permissionName.substringAfterLast('.')}"

    private fun guessFallbackCategory(permissionName: String): PermissionCategory {
        val name = permissionName.uppercase()
        return when {
            name.contains("SMS") || name.contains("MMS") -> PermissionCategory.SMS
            name.contains("CONTACT") || name.contains("CALL_LOG") -> PermissionCategory.CONTACTS_CALL_LOGS
            name.contains("CAMERA") || name.contains("AUDIO") -> PermissionCategory.CAMERA_MICROPHONE
            name.contains("LOCATION") -> PermissionCategory.LOCATION
            name.contains("PHONE") || name.contains("CALL") -> PermissionCategory.PHONE_IDENTITY
            name.contains("STORAGE") || name.contains("MEDIA") -> PermissionCategory.STORAGE_MEDIA
            name.contains("BOOT") || name.contains("FOREGROUND") || name.contains("WAKE") -> PermissionCategory.BACKGROUND_PERSISTENCE
            name.contains("ACCESSIBILITY") || name.contains("OVERLAY") || name.contains("ADMIN") -> PermissionCategory.SPECIAL_ACCESS
            else -> PermissionCategory.NETWORK
        }
    }
}