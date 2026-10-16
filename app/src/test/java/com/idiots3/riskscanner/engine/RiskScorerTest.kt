package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.AiAssessment
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.FitStatus
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PermissionDetail
import com.idiots3.riskscanner.model.PermissionFit
import com.idiots3.riskscanner.model.ProtectionLevel
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.SpecialAccessInfo
import org.junit.Assert.assertEquals
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
        hasLauncher: Boolean = true
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
        isSystemApp = false,
        hasLauncherActivity = hasLauncher,
        androidCategory = null,
        apkPath = "",
        permissions = permissions,
        specialAccess = specialAccess
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
        assertTrue(result.reasons.any { it.title.contains("Banking Trojan") })
        assertTrue("score=${result.score}", result.score >= 51)
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
        // Rules-only score at Medium or above via several granted sensitive permissions.
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
            .filter { it.source == com.idiots3.riskscanner.model.ScoreSource.RULES }
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
}
