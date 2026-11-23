package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.AiAssessment
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.FitStatus
import com.idiots3.riskscanner.model.InstallProvenance
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PermissionDetail
import com.idiots3.riskscanner.model.PermissionFit
import com.idiots3.riskscanner.model.ProtectionLevel
import com.idiots3.riskscanner.model.ReasonKind
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.ScoreSource
import com.idiots3.riskscanner.model.SpecialAccessGrantState
import com.idiots3.riskscanner.model.SpecialAccessInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskScorerTest {

    private fun permission(
        name: String,
        category: PermissionCategory,
        level: ProtectionLevel = ProtectionLevel.DANGEROUS,
        granted: Boolean = true
    ) = PermissionDetail(
        name = "android.permission.$name",
        shortName = name,
        isDeclared = true,
        isGranted = granted,
        protectionLevel = level,
        category = category,
        description = name
    )

    private fun app(
        name: String = "Example",
        pkg: String = "com.example.app",
        permissions: List<PermissionDetail> = emptyList(),
        specialAccess: SpecialAccessInfo = SpecialAccessInfo(),
        installerSource: String = "Google Play Store",
        hasLauncher: Boolean = true,
        isSystemApp: Boolean = false,
        isDefaultSmsApp: Boolean = false,
        isEnabledInputMethod: Boolean = false,
        installProvenance: InstallProvenance = AppInfo.inferProvenance(installerSource, isSystemApp)
    ) = AppInfo(
        appName = name,
        packageName = pkg,
        versionName = "1.0",
        versionCode = 1,
        installDate = 0,
        updateDate = 0,
        installerSource = installerSource,
        targetSdk = 34,
        minSdk = 26,
        isSystemApp = isSystemApp,
        hasLauncherActivity = hasLauncher,
        androidCategory = null,
        apkPath = "",
        permissions = permissions,
        specialAccess = specialAccess,
        installProvenance = installProvenance,
        isDefaultSmsApp = isDefaultSmsApp,
        isEnabledInputMethod = isEnabledInputMethod
    )

    @Test
    fun `plain app without sensitive permissions stays low`() {
        val result = RiskScorer.score(
            app(
                permissions = listOf(
                    permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL),
                    permission("ACCESS_NETWORK_STATE", PermissionCategory.NETWORK, ProtectionLevel.NORMAL)
                )
            )
        )
        assertTrue("score=${result.score}", result.score < 26)
        assertEquals(RiskLevel.LOW, result.level)
    }

    @Test
    fun `declared but not granted permission scores less than granted`() {
        val granted = RiskScorer.score(
            app(permissions = listOf(permission("READ_SMS", PermissionCategory.SMS)))
        )
        val declaredOnly = RiskScorer.score(
            app(permissions = listOf(permission("READ_SMS", PermissionCategory.SMS, granted = false)))
        )
        assertTrue(
            "granted=${granted.ruleScore} declaredOnly=${declaredOnly.ruleScore}",
            granted.ruleScore > declaredOnly.ruleScore
        )
    }

    @Test
    fun `accessibility plus overlay triggers banking trojan pattern`() {
        val result = RiskScorer.score(
            app(
                permissions = listOf(permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL)),
                specialAccess = SpecialAccessInfo(
                    accessibilityEnabled = true,
                    overlayEnabled = true,
                    overlayDeclared = true
                )
            )
        )
        assertTrue(result.reasons.any { it.title.contains("Banking Trojan") && it.kind == ReasonKind.COMBO_RULE })
        assertTrue("score=${result.score}", result.score >= 51)
    }

    @Test
    fun `accessibility plus unverified overlay triggers potential banking trojan warning`() {
        val result = RiskScorer.score(
            app(
                permissions = listOf(permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL)),
                specialAccess = SpecialAccessInfo(
                    accessibilityEnabled = true,
                    overlayEnabled = false,
                    overlayDeclared = true,
                    overlayState = SpecialAccessGrantState.DECLARED_UNVERIFIED
                )
            )
        )
        assertTrue(result.reasons.any { it.title.contains("Accessibility + Overlay") && it.kind == ReasonKind.COMBO_RULE })
        assertTrue("score=${result.score}", result.score >= 40)
    }

    @Test
    fun `full banking trojan profile reaches critical`() {
        val result = RiskScorer.score(
            app(
                installerSource = "Sideloaded",
                permissions = listOf(
                    permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL),
                    permission("READ_SMS", PermissionCategory.SMS),
                    permission("RECEIVE_BOOT_COMPLETED", PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL)
                ),
                specialAccess = SpecialAccessInfo(
                    accessibilityEnabled = true,
                    overlayEnabled = true,
                    overlayDeclared = true
                )
            )
        )
        assertEquals(RiskLevel.CRITICAL, result.level)
        assertTrue("reasons=${result.reasons.size}", result.reasons.size >= 3)
    }

    @Test
    fun `sideloaded sms app with internet and boot is at least high`() {
        val result = RiskScorer.score(
            app(
                installerSource = "Sideloaded",
                permissions = listOf(
                    permission("READ_SMS", PermissionCategory.SMS),
                    permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL),
                    permission("RECEIVE_BOOT_COMPLETED", PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL)
                )
            )
        )
        assertTrue("score=${result.score}", result.score >= 51)
        assertTrue(result.reasons.any { it.title.contains("SMS") })
    }

    @Test
    fun `default sms app from play store is exempt from sms stealer combo`() {
        val result = RiskScorer.score(
            app(
                name = "Messages",
                installerSource = "Google Play Store",
                isDefaultSmsApp = true,
                permissions = listOf(
                    permission("READ_SMS", PermissionCategory.SMS),
                    permission("RECEIVE_SMS", PermissionCategory.SMS),
                    permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL),
                    permission("RECEIVE_BOOT_COMPLETED", PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL)
                )
            )
        )
        assertFalse(result.reasons.any { it.title.contains("SMS Data Stealer") })
    }

    @Test
    fun `enabled keyboard with no launcher icon is exempt from hidden app penalty`() {
        val result = RiskScorer.score(
            app(
                name = "BoardKey",
                hasLauncher = false,
                isEnabledInputMethod = true,
                permissions = listOf(
                    permission("RECORD_AUDIO", PermissionCategory.CAMERA_MICROPHONE)
                )
            )
        )
        assertFalse(result.reasons.any { it.title.contains("Invisible app") })
    }

    @Test
    fun `system app with unknown installer is never penalized as sideloaded`() {
        val result = RiskScorer.score(
            app(
                name = "System Carrier",
                installerSource = "Unknown",
                isSystemApp = true,
                permissions = listOf(
                    permission("READ_PHONE_STATE", PermissionCategory.PHONE_IDENTITY)
                )
            )
        )
        assertFalse(result.reasons.any { it.kind == ReasonKind.PROVENANCE })
        assertTrue(result.score < 26)
    }

    @Test
    fun `browser sideloaded app requesting install packages triggers dropper combo`() {
        val result = RiskScorer.score(
            app(
                name = "Free Update",
                installerSource = "Sideloaded via Chrome",
                installProvenance = InstallProvenance.BROWSER_OR_APP_SIDELOAD,
                specialAccess = SpecialAccessInfo(
                    installPackagesDeclared = true,
                    installPackagesState = SpecialAccessGrantState.DECLARED_UNVERIFIED
                )
            )
        )
        assertTrue(result.reasons.any { it.title.contains("Dropper") && it.kind == ReasonKind.COMBO_RULE })
    }

    @Test
    fun `nfc card emulation plus accessibility triggers relay combo`() {
        val result = RiskScorer.score(
            app(
                name = "TapPay Helper",
                specialAccess = SpecialAccessInfo(
                    accessibilityEnabled = true,
                    nfcHceDeclared = true
                )
            )
        )
        assertTrue(result.reasons.any { it.title.contains("NFC Relay") && it.kind == ReasonKind.COMBO_RULE })
    }

    @Test
    fun `utility app with sms access is flagged for context mismatch`() {
        val result = RiskScorer.score(
            app(
                name = "Torch Pro",
                pkg = "com.torch.pro",
                permissions = listOf(permission("READ_SMS", PermissionCategory.SMS))
            )
        )
        assertTrue(result.reasons.any { it.title.contains("Context Mismatch") })
    }

    @Test
    fun `ai signal is bounded and cannot create a critical verdict alone`() {
        val ai = AiAssessment(
            purposeLabel = "Flashlight",
            purposeSimilarity = 0.55f,
            permissionFit = emptyMap(),
            unexplainedCategories = listOf(
                PermissionCategory.SMS,
                PermissionCategory.CONTACTS_CALL_LOGS,
                PermissionCategory.CAMERA_MICROPHONE,
                PermissionCategory.LOCATION
            ),
            modelUsed = true
        )
        val result = RiskScorer.score(
            app(
                permissions = listOf(
                    permission("READ_SMS", PermissionCategory.SMS),
                    permission("READ_CONTACTS", PermissionCategory.CONTACTS_CALL_LOGS),
                    permission("CAMERA", PermissionCategory.CAMERA_MICROPHONE),
                    permission("ACCESS_FINE_LOCATION", PermissionCategory.LOCATION),
                    permission("READ_MEDIA_IMAGES", PermissionCategory.STORAGE_MEDIA)
                )
            ),
            ai = ai
        )
        assertTrue("aiDelta=${result.aiDelta}", result.aiDelta <= ScoringConfig.AI_MAX_DELTA)
        assertTrue("score=${result.score}", result.score <= 75)
        assertEquals("aiDelta=${result.aiDelta}", 8, result.aiDelta)
    }

    @Test
    fun `ai signal adds nothing to a low rules score`() {
        val ai = AiAssessment(
            purposeLabel = "Flashlight",
            purposeSimilarity = 0.55f,
            permissionFit = emptyMap(),
            unexplainedCategories = listOf(PermissionCategory.CAMERA_MICROPHONE),
            modelUsed = true
        )
        val result = RiskScorer.score(
            app(permissions = listOf(permission("CAMERA", PermissionCategory.CAMERA_MICROPHONE, granted = false))),
            ai = ai
        )
        assertEquals(0, result.aiDelta)
    }

    @Test
    fun `high and critical apps always provide at least three reasons`() {
        val result = RiskScorer.score(
            app(
                name = "Torch Pro",
                installerSource = "Sideloaded",
                permissions = listOf(
                    permission("READ_SMS", PermissionCategory.SMS),
                    permission("READ_CONTACTS", PermissionCategory.CONTACTS_CALL_LOGS),
                    permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL)
                )
            )
        )
        assertTrue(result.score >= 51)
        assertTrue("reasons=${result.reasons.size}", result.reasons.size >= 3)
    }

    @Test
    fun `every scored point is traceable in the breakdown`() {
        val result = RiskScorer.score(
            app(
                permissions = listOf(
                    permission("READ_SMS", PermissionCategory.SMS),
                    permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL)
                ),
                specialAccess = SpecialAccessInfo(notificationListenerEnabled = true)
            )
        )
        val rulesPoints = result.breakdown
            .filter { it.source == ScoreSource.RULES }
            .sumOf { it.points }
        assertEquals(result.ruleScore, rulesPoints)
    }

    @Test
    fun `hidden spyware combination needs no launcher or sideloaded source`() {
        val visiblePlayApp = RiskScorer.score(
            app(
                permissions = listOf(
                    permission("CAMERA", PermissionCategory.CAMERA_MICROPHONE),
                    permission("RECORD_AUDIO", PermissionCategory.CAMERA_MICROPHONE),
                    permission("ACCESS_FINE_LOCATION", PermissionCategory.LOCATION),
                    permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL)
                )
            )
        )
        assertTrue(visiblePlayApp.reasons.none { it.title.contains("Hidden Surveillance") })

        val hiddenApp = RiskScorer.score(
            app(
                hasLauncher = false,
                permissions = listOf(
                    permission("CAMERA", PermissionCategory.CAMERA_MICROPHONE),
                    permission("RECORD_AUDIO", PermissionCategory.CAMERA_MICROPHONE),
                    permission("ACCESS_FINE_LOCATION", PermissionCategory.LOCATION),
                    permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL)
                )
            )
        )
        assertTrue(hiddenApp.reasons.any { it.title.contains("Hidden Surveillance") })
    }

    @Test
    fun `permission fit statuses are not interpreted as probability`() {
        val fit = PermissionFit(FitStatus.UNEXPLAINED, 0.12f, "test")
        assertEquals(FitStatus.UNEXPLAINED, fit.status)
        assertTrue(fit.similarity < ScoringConfig.AI_UNCLEAR_THRESHOLD)
    }

    @Test
    fun `qr scanner with camera does not trigger utility mismatch, but sms still does`() {
        val qrWithCam = RiskScorer.score(
            app(
                name = "QR Code Scanner",
                pkg = "com.example.qrscanner",
                permissions = listOf(permission("CAMERA", PermissionCategory.CAMERA_MICROPHONE))
            )
        )
        assertFalse(qrWithCam.reasons.any { it.title.contains("Context Mismatch") })

        val qrWithSms = RiskScorer.score(
            app(
                name = "QR Code Scanner",
                pkg = "com.example.qrscanner",
                permissions = listOf(permission("READ_SMS", PermissionCategory.SMS))
            )
        )
        assertTrue(qrWithSms.reasons.any { it.title.contains("Context Mismatch") })
    }

    @Test
    fun `device migration provenance is never penalized as sideloaded`() {
        val migrated = RiskScorer.score(
            app(
                name = "ChatApp",
                installerSource = "Transferred via Smart Switch",
                installProvenance = InstallProvenance.DEVICE_MIGRATION,
                permissions = listOf(permission("CAMERA", PermissionCategory.CAMERA_MICROPHONE))
            )
        )
        assertFalse(migrated.reasons.any { it.kind == ReasonKind.PROVENANCE })
    }

    @Test
    fun `impersonation, debug cert, sideloaded keyboard, and uninstall blocker are scored`() {
        val imp = RiskScorer.score(
            app(name = "Google Play Services", pkg = "com.evil.gms", installerSource = "Sideloaded")
                .copy(impersonationTarget = "Google Play Services", isDebugSigned = true, isDebuggable = true)
        )
        assertTrue(imp.reasons.any { it.kind == ReasonKind.IMPERSONATION })
        assertTrue(imp.reasons.any { it.title.contains("Repackaged") })
        assertTrue("impersonation score=${imp.score}", imp.score >= 51)

        val keylogger = RiskScorer.score(
            app(
                name = "FastBoard",
                installerSource = "Sideloaded",
                isEnabledInputMethod = true,
                permissions = listOf(permission("INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL))
            )
        )
        assertTrue(keylogger.reasons.any { it.title.contains("Sideloaded Networked Keyboard") })

        val blocker = RiskScorer.score(
            app(
                name = "Cleaner",
                specialAccess = SpecialAccessInfo(accessibilityEnabled = true, deviceAdminActive = true)
            )
        )
        assertTrue(blocker.reasons.any { it.title.contains("Uninstall-Blocking") })
        assertTrue(blocker.score >= 51)
    }

    @Test
    fun `self app always scores zero low`() {
        val self = RiskScorer.score(app(name = "DroidScanner").copy(isSelfApp = true))
        assertEquals(0, self.score)
        assertEquals(RiskLevel.LOW, self.level)
    }
}
