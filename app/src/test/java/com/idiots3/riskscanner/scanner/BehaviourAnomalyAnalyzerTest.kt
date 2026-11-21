package com.idiots3.riskscanner.scanner

import com.idiots3.riskscanner.scanner.BehaviourAnomalyAnalyzer.AppUsageMetric
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviourAnomalyAnalyzerTest {

    @Test
    fun `insufficient baseline returns empty map`() {
        val metrics = (1..4).associate { i ->
            "com.app.$i" to AppUsageMetric(10_000L, 0L, 1_000L, 1_000L, 0L)
        }
        assertTrue(BehaviourAnomalyAnalyzer.analyze(metrics).isEmpty())
    }

    @Test
    fun `flags high network outlier, heavy background service, and active without open`() {
        val metrics = mutableMapOf<String, AppUsageMetric>()
        for (i in 1..8) {
            metrics["com.normal.$i"] = AppUsageMetric(
                foregroundMs = 300_000L,
                foregroundServiceMs = 30_000L,
                lastTimeUsedMs = 1_000L,
                wifiBytes = 50_000L,
                mobileBytes = 10_000L
            )
        }
        metrics["com.net.hog"] = AppUsageMetric(
            foregroundMs = 600_000L,
            foregroundServiceMs = 30_000L,
            lastTimeUsedMs = 2_000L,
            wifiBytes = 40_000_000L,
            mobileBytes = 10_000_000L
        )
        metrics["com.silent.spy"] = AppUsageMetric(
            foregroundMs = 0L,
            foregroundServiceMs = 150L * 60_000L,
            lastTimeUsedMs = 0L,
            wifiBytes = 3_000_000L,
            mobileBytes = 0L
        )

        val profiles = BehaviourAnomalyAnalyzer.analyze(metrics, lookbackDays = 7)

        val normalAnomalies = profiles["com.normal.1"]?.anomalies.orEmpty()
        assertTrue("normal app had anomalies: $normalAnomalies", normalAnomalies.isEmpty())

        val netHog = profiles["com.net.hog"]!!
        assertTrue(netHog.anomalies.any { it.type == "High network outlier" })
        assertFalse(netHog.neverOpenedInWindow)

        val silentSpy = profiles["com.silent.spy"]!!
        assertTrue(silentSpy.neverOpenedInWindow)
        assertTrue(silentSpy.anomalies.any { it.type == "Heavy background service" })
        assertTrue(silentSpy.anomalies.any { it.type == "Active without being opened" })
    }
}
