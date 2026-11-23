package com.idiots3.riskscanner.scanner

import com.idiots3.riskscanner.model.DevicePosture
import com.idiots3.riskscanner.model.PostureAction
import com.idiots3.riskscanner.model.PostureCheck
import com.idiots3.riskscanner.model.PostureStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DevicePostureEvaluatorTest {

    @Test
    fun `security patch recency classifies recent, aging, stale, and missing dates`() {
        val today = LocalDate.of(2026, 11, 21)
        assertEquals(
            PostureStatus.PROTECTED,
            DevicePostureEvaluator.evaluateSecurityPatch("2026-10-05", today).status
        )
        assertEquals(
            PostureStatus.REVIEW,
            DevicePostureEvaluator.evaluateSecurityPatch("2026-05-01", today).status
        )
        assertEquals(
            PostureStatus.AT_RISK,
            DevicePostureEvaluator.evaluateSecurityPatch("2025-01-01", today).status
        )
        assertEquals(
            PostureStatus.UNKNOWN,
            DevicePostureEvaluator.evaluateSecurityPatch("", today).status
        )
        assertEquals(
            PostureStatus.UNKNOWN,
            DevicePostureEvaluator.evaluateSecurityPatch("not-a-date", today).status
        )
    }

    @Test
    fun `os version detects supported, aging, and end of life apis`() {
        assertEquals(PostureStatus.PROTECTED, DevicePostureEvaluator.evaluateOsVersion(35, "15").status)
        assertEquals(PostureStatus.REVIEW, DevicePostureEvaluator.evaluateOsVersion(33, "13").status)
        assertEquals(PostureStatus.AT_RISK, DevicePostureEvaluator.evaluateOsVersion(31, "12").status)
    }

    @Test
    fun `user ca certs and proxy evaluators classify cleanly`() {
        assertEquals(PostureStatus.PROTECTED, DevicePostureEvaluator.evaluateUserCaCerts(0).status)
        val flaggedCa = DevicePostureEvaluator.evaluateUserCaCerts(2)
        assertEquals(PostureStatus.AT_RISK, flaggedCa.status)
        assertEquals(PostureAction.CREDENTIAL_SETTINGS, flaggedCa.action)
        assertEquals(PostureStatus.UNKNOWN, DevicePostureEvaluator.evaluateUserCaCerts(null).status)

        assertEquals(PostureStatus.PROTECTED, DevicePostureEvaluator.evaluateHttpProxy(null).status)
        val flaggedProxy = DevicePostureEvaluator.evaluateHttpProxy("10.0.0.2:8888")
        assertEquals(PostureStatus.REVIEW, flaggedProxy.status)
        assertEquals(PostureAction.NETWORK_SETTINGS, flaggedProxy.action)
    }

    @Test
    fun `device posture protection score ignores unknown checks`() {
        val posture = DevicePosture(
            listOf(
                PostureCheck("a", "A", PostureStatus.PROTECTED, "ok", "ok"),
                PostureCheck("b", "B", PostureStatus.REVIEW, "rev", "rev"),
                PostureCheck("c", "C", PostureStatus.UNKNOWN, "unk", "unk")
            )
        )
        // Only PROTECTED (100) + REVIEW (60) count -> 160 / 2 = 80
        assertEquals(80, posture.protectionScore)
    }

    @Test
    fun `private dns, lock screen privacy, timeout, vpn, keyboards, and build hygiene classify cleanly`() {
        assertEquals(PostureStatus.REVIEW, DevicePostureEvaluator.evaluatePrivateDns("off", null).status)
        assertEquals(PostureStatus.PROTECTED, DevicePostureEvaluator.evaluatePrivateDns("opportunistic", null).status)
        assertEquals(PostureStatus.PROTECTED, DevicePostureEvaluator.evaluatePrivateDns("hostname", "dns.quad9.net").status)
        assertEquals(PostureStatus.REVIEW, DevicePostureEvaluator.evaluatePrivateDns("hostname", "dns.unfamiliar.example").status)

        assertEquals(PostureStatus.PROTECTED, DevicePostureEvaluator.evaluateLockScreenPrivacy(true, 1, 0).status)
        assertEquals(PostureStatus.REVIEW, DevicePostureEvaluator.evaluateLockScreenPrivacy(true, 1, 1).status)

        assertEquals(PostureStatus.PROTECTED, DevicePostureEvaluator.evaluateScreenTimeoutAndAwake(60_000, 0).status)
        assertEquals(PostureStatus.REVIEW, DevicePostureEvaluator.evaluateScreenTimeoutAndAwake(60_000, 1).status)
        assertEquals(PostureStatus.AT_RISK, DevicePostureEvaluator.evaluateScreenTimeoutAndAwake(30 * 60_000, 0).status)

        assertEquals(PostureStatus.REVIEW, DevicePostureEvaluator.evaluateNetworkTransport(vpnActive = true, captivePortal = false, vpnAppNames = listOf("MyVPN")).status)
        assertEquals(PostureStatus.REVIEW, DevicePostureEvaluator.evaluateNetworkTransport(vpnActive = false, captivePortal = true, vpnAppNames = emptyList()).status)
        assertEquals(PostureStatus.PROTECTED, DevicePostureEvaluator.evaluateNetworkTransport(vpnActive = false, captivePortal = false, vpnAppNames = emptyList()).status)

        assertEquals(
            PostureStatus.AT_RISK,
            DevicePostureEvaluator.evaluateInputAndAutofill(
                sideloadedNetworkKeyboards = listOf("ShadyKey"),
                storeNetworkKeyboards = emptyList(),
                sideloadedAutofillApps = emptyList(),
                sideloadedAssistantApps = emptyList()
            ).status
        )
        assertEquals(
            PostureStatus.PROTECTED,
            DevicePostureEvaluator.evaluateInputAndAutofill(emptyList(), emptyList(), emptyList(), emptyList()).status
        )

        assertEquals(
            PostureStatus.AT_RISK,
            DevicePostureEvaluator.evaluateOsBuildHygiene("test-keys", "user", 1, 1, null).status
        )
        assertEquals(
            PostureStatus.PROTECTED,
            DevicePostureEvaluator.evaluateOsBuildHygiene("release-keys", "user", 1, 1, null).status
        )
    }
}
