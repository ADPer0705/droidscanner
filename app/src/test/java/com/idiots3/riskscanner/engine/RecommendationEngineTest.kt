package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.BehaviourAnomaly
import com.idiots3.riskscanner.model.BehaviourProfile
import com.idiots3.riskscanner.model.BehaviourScanResult
import com.idiots3.riskscanner.model.DevicePosture
import com.idiots3.riskscanner.model.PostureAction
import com.idiots3.riskscanner.model.PostureCheck
import com.idiots3.riskscanner.model.PostureStatus
import com.idiots3.riskscanner.model.RecommendationAction
import com.idiots3.riskscanner.model.RecommendationPriority
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.SpecialAccessInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    private fun app(
        name: String,
        pkg: String,
        score: Int = 10,
        level: RiskLevel = RiskLevel.LOW,
        installer: String = "Google Play Store",
        specialAccess: SpecialAccessInfo = SpecialAccessInfo()
    ) = AppInfo(
        appName = name,
        packageName = pkg,
        versionName = "1.0",
        versionCode = 1L,
        installDate = 0L,
        updateDate = 0L,
        installerSource = installer,
        targetSdk = 34,
        minSdk = 26,
        isSystemApp = false,
        hasLauncherActivity = true,
        androidCategory = null,
        apkPath = "",
        permissions = emptyList(),
        specialAccess = specialAccess,
        riskScore = score,
        riskLevel = level
    )

    @Test
    fun `omitting behaviour permission does not penalize unified device score`() {
        val apps = listOf(app("Safe App", "com.safe.app", score = 5, level = RiskLevel.LOW))
        val posture = DevicePosture(
            listOf(
                PostureCheck("lock", "Screen lock", PostureStatus.PROTECTED, "ok", "ok")
            )
        )
        val summary = RecommendationEngine.generate(
            apps = apps,
            posture = posture,
            behaviourResult = BehaviourScanResult(permissionGranted = false)
        )
        assertFalse(summary.behaviourIncluded)
        assertEquals(100, summary.deviceScore)
        assertFalse(summary.scoreBreakdown.containsKey("Behaviour"))
    }

    @Test
    fun `critical apps, unknown device admins, and posture issues generate prioritized actions`() {
        val apps = listOf(
            app("Evil App", "com.evil.app", score = 85, level = RiskLevel.CRITICAL),
            app(
                "Weird Admin",
                "com.weird.admin",
                score = 40,
                level = RiskLevel.MEDIUM,
                specialAccess = SpecialAccessInfo(deviceAdminActive = true)
            ),
            app(
                "Trusted Antivirus",
                "com.trusted.antivirus",
                score = 20,
                level = RiskLevel.LOW,
                specialAccess = SpecialAccessInfo(deviceAdminActive = true)
            )
        )
        val posture = DevicePosture(
            listOf(
                PostureCheck(
                    "user_ca_certs",
                    "User-installed CA certificates",
                    PostureStatus.AT_RISK,
                    "1 user CA found",
                    "Remove unfamiliar CAs",
                    PostureAction.CREDENTIAL_SETTINGS
                )
            )
        )
        val behaviour = BehaviourScanResult(
            permissionGranted = true,
            profiles = mapOf(
                "com.evil.app" to BehaviourProfile(
                    packageName = "com.evil.app",
                    foregroundMs = 0L,
                    foregroundServiceMs = 1_200_000L,
                    totalNetworkBytes = 10_000_000L,
                    wifiBytes = 10_000_000L,
                    mobileBytes = 0L,
                    anomalies = listOf(BehaviourAnomaly("High network outlier", "10 MB", 3.1f, "Inspect")),
                    lookbackDays = 7
                )
            )
        )

        val summary = RecommendationEngine.generate(apps, posture, behaviour)
        assertTrue(summary.behaviourIncluded)
        assertTrue(summary.recommendations.first().priority == RecommendationPriority.CRITICAL)
        assertTrue(summary.recommendations.any { it.id == "critical_app_com.evil.app" && it.action == RecommendationAction.OPEN_APP_DETAIL })
        assertTrue(summary.recommendations.any { it.id == "device_admin_com.weird.admin" && it.action == RecommendationAction.OPEN_DEVICE_ADMIN_SETTINGS })
        assertFalse(summary.recommendations.any { it.id == "device_admin_com.trusted.antivirus" })
        assertTrue(summary.recommendations.any { it.id == "posture_user_ca_certs" && it.action == RecommendationAction.OPEN_CREDENTIAL_SETTINGS })
    }
}
