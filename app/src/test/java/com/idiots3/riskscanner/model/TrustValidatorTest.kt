package com.idiots3.riskscanner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustValidatorTest {

    private fun sampleApp(
        cert: String = "AA:BB:CC",
        perms: List<String> = listOf("CAMERA"),
        special: SpecialAccessInfo = SpecialAccessInfo(accessibilityEnabled = true),
        impersonation: String? = null
    ) = AppInfo(
        appName = "Bitwarden",
        packageName = "com.x8bit.bitwarden",
        versionName = "2026.10",
        versionCode = 100L,
        installDate = 0L,
        updateDate = 0L,
        installerSource = "Google Play Store",
        targetSdk = 35,
        minSdk = 26,
        isSystemApp = false,
        hasLauncherActivity = true,
        androidCategory = null,
        apkPath = "",
        permissions = perms.map {
            PermissionDetail(
                name = "android.permission.$it",
                shortName = it,
                isDeclared = true,
                isGranted = true,
                protectionLevel = ProtectionLevel.DANGEROUS,
                category = PermissionCategory.CAMERA_MICROPHONE,
                description = it
            )
        },
        specialAccess = special,
        signingCertSha256 = cert,
        impersonationTarget = impersonation
    )

    @Test
    fun `trust remains valid across benign version bump with same cert and privileges`() {
        val original = sampleApp()
        val record = TrustValidator.recordFor(original, now = 1000L)
        val updated = original.copy(versionCode = 101L, versionName = "2026.11")
        assertEquals(TrustCheckResult.Valid, TrustValidator.validateAppTrust(updated, record))
    }

    @Test
    fun `trust auto-invalidates when signing cert changes`() {
        val original = sampleApp(cert = "AA:BB:CC")
        val record = TrustValidator.recordFor(original)
        val tampered = original.copy(signingCertSha256 = "DD:EE:FF")
        val result = TrustValidator.validateAppTrust(tampered, record)
        assertTrue(result is TrustCheckResult.Invalidated)
        assertTrue((result as TrustCheckResult.Invalidated).reasons.any { it.contains("certificate", ignoreCase = true) })
    }

    @Test
    fun `trust auto-invalidates when new sensitive permission or special access is added`() {
        val original = sampleApp(
            perms = listOf("CAMERA"),
            special = SpecialAccessInfo(accessibilityEnabled = true)
        )
        val record = TrustValidator.recordFor(original)

        val escalated = sampleApp(
            perms = listOf("CAMERA", "READ_SMS"),
            special = SpecialAccessInfo(accessibilityEnabled = true, deviceAdminActive = true)
        )
        val result = TrustValidator.validateAppTrust(escalated, record)
        assertTrue(result is TrustCheckResult.Invalidated)
        val reasons = (result as TrustCheckResult.Invalidated).reasons
        assertTrue(reasons.any { it.contains("READ_SMS") })
        assertTrue(reasons.any { it.contains("Device Administrator") })
    }

    @Test
    fun `posture dismissal stays valid until detail fingerprint changes or check becomes protected`() {
        val reviewCheck = PostureCheck(
            id = "developer_options",
            title = "Developer options",
            status = PostureStatus.REVIEW,
            detail = "Developer options are enabled.",
            recommendation = "Review."
        )
        val record = DismissedPostureRecord(
            checkId = reviewCheck.id,
            title = reviewCheck.title,
            detailFingerprint = reviewCheck.detailFingerprint,
            dismissedAt = 1000L
        )
        assertTrue(TrustValidator.isPostureDismissalValid(reviewCheck, record))

        val regressed = reviewCheck.copy(status = PostureStatus.AT_RISK, detail = "USB and wireless debugging enabled.")
        assertFalse(TrustValidator.isPostureDismissalValid(regressed, record))

        val fixed = reviewCheck.copy(status = PostureStatus.PROTECTED)
        assertFalse(TrustValidator.isPostureDismissalValid(fixed, record))
    }
}
