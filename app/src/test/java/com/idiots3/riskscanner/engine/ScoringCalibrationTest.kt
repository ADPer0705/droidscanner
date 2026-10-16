package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PermissionDetail
import com.idiots3.riskscanner.model.ProtectionLevel
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.SpecialAccessInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Calibration guard: realistic app profiles must land in sensible bands.
 * If a weight is ever tuned, these tests say loudly when the distribution breaks.
 */
class ScoringCalibrationTest {

    private fun perm(name: String, category: PermissionCategory, granted: Boolean = true) =
        PermissionDetail(
            name = "android.permission.$name",
            shortName = name,
            isDeclared = true,
            isGranted = granted,
            protectionLevel = ProtectionLevel.DANGEROUS,
            category = category,
            description = name
        )

    private fun normal(name: String, category: PermissionCategory) = PermissionDetail(
        name = "android.permission.$name",
        shortName = name,
        isDeclared = true,
        isGranted = true,
        protectionLevel = ProtectionLevel.NORMAL,
        category = category,
        description = name
    )

    private fun app(
        name: String,
        pkg: String,
        permissions: List<PermissionDetail>,
        installer: String = "Google Play Store",
        special: SpecialAccessInfo = SpecialAccessInfo(),
        hasLauncher: Boolean = true
    ) = AppInfo(
        appName = name,
        packageName = pkg,
        versionName = "1.0",
        versionCode = 1,
        installDate = 0,
        updateDate = 0,
        installerSource = installer,
        targetSdk = 34,
        minSdk = 26,
        isSystemApp = false,
        hasLauncherActivity = hasLauncher,
        androidCategory = null,
        apkPath = "",
        permissions = permissions,
        specialAccess = special
    )

    private fun score(app: AppInfo) = RiskScorer.score(app, null)

    @Test
    fun `typical play store messaging app lands in medium`() {
        val app = app(
            "ChatApp", "com.example.chatapp",
            listOf(
                perm("READ_CONTACTS", PermissionCategory.CONTACTS_CALL_LOGS),
                perm("WRITE_CONTACTS", PermissionCategory.CONTACTS_CALL_LOGS),
                perm("CAMERA", PermissionCategory.CAMERA_MICROPHONE),
                perm("RECORD_AUDIO", PermissionCategory.CAMERA_MICROPHONE),
                perm("ACCESS_FINE_LOCATION", PermissionCategory.LOCATION),
                perm("READ_MEDIA_IMAGES", PermissionCategory.STORAGE_MEDIA),
                perm("READ_PHONE_STATE", PermissionCategory.PHONE_IDENTITY),
                normal("RECEIVE_BOOT_COMPLETED", PermissionCategory.BACKGROUND_PERSISTENCE),
                normal("FOREGROUND_SERVICE", PermissionCategory.BACKGROUND_PERSISTENCE),
                normal("INTERNET", PermissionCategory.NETWORK),
                normal("ACCESS_NETWORK_STATE", PermissionCategory.NETWORK)
            )
        )
        val result = score(app)
        assertTrue("chat app score=${result.score}", result.score in 26..50)
        assertEquals(RiskLevel.MEDIUM, result.level)
    }

    @Test
    fun `camera app stays low or medium`() {
        val app = app(
            "Camera", "com.example.camera",
            listOf(
                perm("CAMERA", PermissionCategory.CAMERA_MICROPHONE),
                perm("RECORD_AUDIO", PermissionCategory.CAMERA_MICROPHONE),
                perm("READ_MEDIA_IMAGES", PermissionCategory.STORAGE_MEDIA),
                perm("ACCESS_FINE_LOCATION", PermissionCategory.LOCATION),
                normal("INTERNET", PermissionCategory.NETWORK)
            )
        )
        val result = score(app)
        assertTrue("camera score=${result.score}", result.score <= 50)
    }

    @Test
    fun `banking app with notification access stays below high`() {
        val app = app(
            "MyBank", "com.example.bank",
            listOf(
                perm("CAMERA", PermissionCategory.CAMERA_MICROPHONE),
                perm("READ_CONTACTS", PermissionCategory.CONTACTS_CALL_LOGS),
                perm("READ_PHONE_STATE", PermissionCategory.PHONE_IDENTITY),
                perm("ACCESS_FINE_LOCATION", PermissionCategory.LOCATION),
                normal("INTERNET", PermissionCategory.NETWORK),
                normal("RECEIVE_BOOT_COMPLETED", PermissionCategory.BACKGROUND_PERSISTENCE)
            ),
            special = SpecialAccessInfo(notificationListenerEnabled = true)
        )
        val result = score(app)
        assertTrue("bank score=${result.score}", result.score < 51)
    }

    @Test
    fun `benign test kit decoy is critical`() {
        val app = app(
            "Torch Pro", "com.torch.pro",
            listOf(
                perm("READ_SMS", PermissionCategory.SMS),
                perm("RECEIVE_SMS", PermissionCategory.SMS),
                perm("SEND_SMS", PermissionCategory.SMS),
                perm("READ_CONTACTS", PermissionCategory.CONTACTS_CALL_LOGS),
                perm("READ_CALL_LOG", PermissionCategory.CONTACTS_CALL_LOGS),
                perm("CAMERA", PermissionCategory.CAMERA_MICROPHONE),
                perm("RECORD_AUDIO", PermissionCategory.CAMERA_MICROPHONE),
                perm("ACCESS_FINE_LOCATION", PermissionCategory.LOCATION),
                perm("ACCESS_BACKGROUND_LOCATION", PermissionCategory.LOCATION),
                perm("READ_EXTERNAL_STORAGE", PermissionCategory.STORAGE_MEDIA),
                normal("INTERNET", PermissionCategory.NETWORK),
                normal("RECEIVE_BOOT_COMPLETED", PermissionCategory.BACKGROUND_PERSISTENCE)
            ),
            installer = "Sideloaded",
            special = SpecialAccessInfo(
                accessibilityDeclared = true,
                overlayDeclared = true,
                installPackagesDeclared = true,
                usageStatsDeclared = true
            )
        )
        val result = score(app)
        assertEquals(RiskLevel.CRITICAL, result.level)
        assertTrue("decoy reasons=${result.reasons.size}", result.reasons.size >= 3)
    }

    @Test
    fun `invisible recorder is high`() {
        val app = app(
            "System Service", "com.example.sysservice",
            listOf(
                perm("CAMERA", PermissionCategory.CAMERA_MICROPHONE),
                perm("RECORD_AUDIO", PermissionCategory.CAMERA_MICROPHONE),
                perm("ACCESS_FINE_LOCATION", PermissionCategory.LOCATION),
                normal("INTERNET", PermissionCategory.NETWORK)
            ),
            hasLauncher = false
        )
        val result = score(app)
        assertTrue("hidden recorder score=${result.score}", result.score >= 51)
    }

    @Test
    fun `sideloaded camera mod is not critical`() {
        val app = app(
            "GCam Mod", "com.example.gcam",
            listOf(
                perm("CAMERA", PermissionCategory.CAMERA_MICROPHONE),
                perm("RECORD_AUDIO", PermissionCategory.CAMERA_MICROPHONE),
                perm("READ_MEDIA_IMAGES", PermissionCategory.STORAGE_MEDIA),
                normal("INTERNET", PermissionCategory.NETWORK)
            ),
            installer = "Sideloaded"
        )
        val result = score(app)
        assertTrue("gcam mod score=${result.score}", result.score < 76)
    }

    @Test
    fun `plain network-only app stays low`() {
        val app = app(
            "Radio", "com.example.radio",
            listOf(
                normal("INTERNET", PermissionCategory.NETWORK),
                normal("ACCESS_NETWORK_STATE", PermissionCategory.NETWORK)
            )
        )
        assertEquals(RiskLevel.LOW, score(app).level)
    }
}
