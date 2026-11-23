package com.idiots3.riskscanner.scanner

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.BehaviourAnomaly
import com.idiots3.riskscanner.model.BehaviourProfile
import com.idiots3.riskscanner.model.BehaviourScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Optional runtime behavioural insights using public UsageStatsManager and NetworkStatsManager.
 * Active ONLY when the user has explicitly granted Usage Access in Android Settings;
 * silently reports `permissionGranted = false` otherwise without nagging.
 *
 * Uses batch `NetworkStatsManager.querySummary` (2 IPC calls total instead of 2 × N) and
 * tracks upload (`txBytes`) separately from download (`rxBytes`) to highlight background
 * exfiltration rather than foreground streaming or downloads.
 */
class BehaviourAnomalyScanner(private val context: Context) {

    companion object {
        const val DAYS_LOOKBACK = 7L
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun scan(apps: List<AppInfo>): BehaviourScanResult = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) {
            return@withContext BehaviourScanResult(permissionGranted = false)
        }
        try {
            val end = System.currentTimeMillis()
            val start = end - DAYS_LOOKBACK * 24L * 3600L * 1000L

            val usageMap = aggregateUsageStats(start, end)
            if (usageMap.size < BehaviourAnomalyAnalyzer.MIN_APPS_FOR_BASELINE) {
                return@withContext BehaviourScanResult(
                    permissionGranted = true,
                    profiles = emptyMap(),
                    lookbackDays = DAYS_LOOKBACK.toInt()
                )
            }

            val networkMap = getBatchNetworkStats(apps, start, end)
            val metrics = buildMap {
                for (app in apps) {
                    val u = usageMap[app.packageName]
                    val n = networkMap[app.packageName]
                    if (u == null && n == null) continue
                    put(
                        app.packageName,
                        BehaviourAnomalyAnalyzer.AppUsageMetric(
                            foregroundMs = u?.foregroundMs ?: 0L,
                            foregroundServiceMs = u?.foregroundServiceMs ?: 0L,
                            lastTimeUsedMs = u?.lastTimeUsedMs ?: 0L,
                            wifiBytes = n?.wifiTotal ?: 0L,
                            mobileBytes = n?.mobileTotal ?: 0L,
                            rxBytes = n?.rxTotal ?: 0L,
                            txBytes = n?.txTotal ?: 0L
                        )
                    )
                }
            }

            val profiles = BehaviourAnomalyAnalyzer.analyze(metrics, DAYS_LOOKBACK.toInt())
            BehaviourScanResult(
                permissionGranted = true,
                profiles = profiles,
                lookbackDays = DAYS_LOOKBACK.toInt()
            )
        } catch (_: Throwable) {
            BehaviourScanResult(permissionGranted = hasUsageAccess())
        }
    }

    private data class AggregatedUsage(
        var foregroundMs: Long = 0L,
        var foregroundServiceMs: Long = 0L,
        var lastTimeUsedMs: Long = 0L
    )

    private fun aggregateUsageStats(start: Long, end: Long): Map<String, AggregatedUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        return try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).orEmpty()
            val map = HashMap<String, AggregatedUsage>()
            for (stat in stats) {
                val acc = map.getOrPut(stat.packageName) { AggregatedUsage() }
                acc.foregroundMs += stat.totalTimeInForeground
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    acc.foregroundServiceMs += stat.totalTimeForegroundServiceUsed
                }
                if (stat.lastTimeUsed > acc.lastTimeUsedMs) {
                    acc.lastTimeUsedMs = stat.lastTimeUsed
                }
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private data class NetworkCounters(
        var wifiTotal: Long = 0L,
        var mobileTotal: Long = 0L,
        var rxTotal: Long = 0L,
        var txTotal: Long = 0L
    )

    @Suppress("DEPRECATION")
    private fun getBatchNetworkStats(apps: List<AppInfo>, start: Long, end: Long): Map<String, NetworkCounters> {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return emptyMap()

        val uidToPackages = HashMap<Int, MutableList<String>>(apps.size)
        for (app in apps) {
            val uid = runCatching { context.packageManager.getApplicationInfo(app.packageName, 0).uid }.getOrNull()
                ?: continue
            uidToPackages.getOrPut(uid) { mutableListOf() }.add(app.packageName)
        }

        val uidCounters = HashMap<Int, NetworkCounters>()
        collectSummaryForTransport(nsm, ConnectivityManager.TYPE_WIFI, start, end, isWifi = true, uidCounters)
        collectSummaryForTransport(nsm, ConnectivityManager.TYPE_MOBILE, start, end, isWifi = false, uidCounters)

        val result = HashMap<String, NetworkCounters>(uidCounters.size)
        for ((uid, counters) in uidCounters) {
            val pkgs = uidToPackages[uid] ?: continue
            // Only attribute traffic cleanly to non-shared UIDs or single-app UIDs to avoid misattribution.
            if (pkgs.size == 1) {
                result[pkgs.first()] = counters
            }
        }
        return result
    }

    private fun collectSummaryForTransport(
        nsm: NetworkStatsManager,
        networkType: Int,
        start: Long,
        end: Long,
        isWifi: Boolean,
        out: MutableMap<Int, NetworkCounters>
    ) {
        var stats: NetworkStats? = null
        try {
            val bucket = NetworkStats.Bucket()
            val opened: NetworkStats? = nsm.querySummary(networkType, null, start, end)
            stats = opened
            if (opened != null) {
                while (opened.hasNextBucket()) {
                    opened.getNextBucket(bucket)
                    val uid = bucket.uid
                    if (uid < 0) continue
                    val rx = bucket.rxBytes.coerceAtLeast(0L)
                    val tx = bucket.txBytes.coerceAtLeast(0L)
                    if (rx == 0L && tx == 0L) continue
                    val acc = out.getOrPut(uid) { NetworkCounters() }
                    if (isWifi) acc.wifiTotal += (rx + tx) else acc.mobileTotal += (rx + tx)
                    acc.rxTotal += rx
                    acc.txTotal += tx
                }
            }
        } catch (_: Throwable) {
            // Some Android 8–9 builds restrict mobile subscriber queries without READ_PHONE_STATE.
        } finally {
            runCatching { stats?.close() }
        }
    }
}

/**
 * Pure-JVM statistical z-score, upload-heavy exfiltration, and background-activity analyzer.
 */
object BehaviourAnomalyAnalyzer {

    const val MIN_APPS_FOR_BASELINE = 5
    const val ZSCORE_THRESHOLD = 2.0
    const val MIN_NETWORK_OUTLIER_BYTES = 5_000_000L // 5 MB
    const val MIN_UPLOAD_OUTLIER_BYTES = 2_000_000L // 2 MB upload
    const val MIN_FG_SERVICE_OUTLIER_MS = 30L * 60_000L // 30 minutes
    const val SILENT_OPEN_THRESHOLD_MS = 5_000L // <5s foreground
    const val SILENT_BG_SERVICE_MS = 10L * 60_000L // 10 minutes
    const val SILENT_NETWORK_BYTES = 2_000_000L // 2 MB
    const val ACTIVE_STREAMING_FG_MS = 30L * 60_000L // 30m foreground

    data class AppUsageMetric(
        val foregroundMs: Long,
        val foregroundServiceMs: Long,
        val lastTimeUsedMs: Long,
        val wifiBytes: Long,
        val mobileBytes: Long,
        val rxBytes: Long = wifiBytes + mobileBytes,
        val txBytes: Long = 0L
    ) {
        val totalBytes: Long get() = wifiBytes + mobileBytes
    }

    fun analyze(
        metrics: Map<String, AppUsageMetric>,
        lookbackDays: Int = 7
    ): Map<String, BehaviourProfile> {
        if (metrics.size < MIN_APPS_FOR_BASELINE) return emptyMap()

        val allBytes = metrics.values.map { it.totalBytes.toDouble() }
        val netMean = allBytes.average()
        val netStd = stdDev(allBytes, netMean)

        val allTx = metrics.values.map { it.txBytes.toDouble() }
        val txMean = allTx.average()
        val txStd = stdDev(allTx, txMean)

        val allFgSvc = metrics.values.map { it.foregroundServiceMs.toDouble() }
        val fgSvcMean = allFgSvc.average()
        val fgSvcStd = stdDev(allFgSvc, fgSvcMean)

        val result = HashMap<String, BehaviourProfile>(metrics.size)
        for ((pkg, m) in metrics) {
            val anomalies = mutableListOf<BehaviourAnomaly>()
            val neverOpened = m.foregroundMs < SILENT_OPEN_THRESHOLD_MS
            val totalForRatio = (m.rxBytes + m.txBytes).coerceAtLeast(1L)
            val isMostlyDownloadStreaming = m.foregroundMs >= ACTIVE_STREAMING_FG_MS &&
                m.rxBytes.toDouble() / totalForRatio >= 0.85

            val netZ = if (netStd > 0.0) (m.totalBytes - netMean) / netStd else 0.0
            if (netZ >= ZSCORE_THRESHOLD && m.totalBytes >= MIN_NETWORK_OUTLIER_BYTES && !isMostlyDownloadStreaming) {
                anomalies += BehaviourAnomaly(
                    type = "High network outlier",
                    detail = "Transferred ${formatBytes(m.totalBytes)} in ${lookbackDays}d (z=${"%.1f".format(netZ)})",
                    zScore = netZ.toFloat(),
                    recommendation = "Check whether you expect this app to move large amounts of data."
                )
            }

            val txZ = if (txStd > 0.0) (m.txBytes - txMean) / txStd else 0.0
            val suspiciousUpload = m.txBytes >= MIN_UPLOAD_OUTLIER_BYTES &&
                (neverOpened || (txZ >= ZSCORE_THRESHOLD && m.txBytes >= m.rxBytes / 2))
            if (suspiciousUpload) {
                anomalies += BehaviourAnomaly(
                    type = "Unusual upload activity",
                    detail = "Uploaded ${formatBytes(m.txBytes)} out of ${formatBytes(m.totalBytes)} total in ${lookbackDays}d",
                    zScore = maxOf(txZ, 2.2).toFloat(),
                    recommendation = "High outbound upload (especially without opening the app) warrants reviewing its permissions."
                )
            }

            val fgSvcZ = if (fgSvcStd > 0.0) (m.foregroundServiceMs - fgSvcMean) / fgSvcStd else 0.0
            if (fgSvcZ >= ZSCORE_THRESHOLD && m.foregroundServiceMs >= MIN_FG_SERVICE_OUTLIER_MS) {
                anomalies += BehaviourAnomaly(
                    type = "Heavy background service",
                    detail = "Ran foreground services for ${m.foregroundServiceMs / 60_000L}m in ${lookbackDays}d (z=${"%.1f".format(fgSvcZ)})",
                    zScore = fgSvcZ.toFloat(),
                    recommendation = "Restrict background battery use if you do not rely on persistent sync or playback."
                )
            }

            if (neverOpened && (m.foregroundServiceMs >= SILENT_BG_SERVICE_MS || m.totalBytes >= SILENT_NETWORK_BYTES)) {
                anomalies += BehaviourAnomaly(
                    type = "Active without being opened",
                    detail = buildString {
                        append("Not opened in ${lookbackDays}d, yet ")
                        if (m.foregroundServiceMs >= SILENT_BG_SERVICE_MS) {
                            append("ran ${m.foregroundServiceMs / 60_000L}m of background services")
                        }
                        if (m.totalBytes >= SILENT_NETWORK_BYTES) {
                            if (m.foregroundServiceMs >= SILENT_BG_SERVICE_MS) append(" and ")
                            append("transferred ${formatBytes(m.totalBytes)}")
                        }
                    },
                    zScore = maxOf(netZ, txZ, fgSvcZ, 2.0).toFloat(),
                    recommendation = "Review why an app you haven't opened is active in the background."
                )
            }

            result[pkg] = BehaviourProfile(
                packageName = pkg,
                foregroundMs = m.foregroundMs,
                foregroundServiceMs = m.foregroundServiceMs,
                lastTimeUsedMs = m.lastTimeUsedMs,
                neverOpenedInWindow = neverOpened,
                totalNetworkBytes = m.totalBytes,
                wifiBytes = m.wifiBytes,
                mobileBytes = m.mobileBytes,
                anomalies = anomalies,
                lookbackDays = lookbackDays,
                rxBytes = m.rxBytes,
                txBytes = m.txBytes
            )
        }
        return result
    }

    private fun stdDev(values: List<Double>, mean: Double): Double {
        if (values.isEmpty()) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "${"%.2f".format(bytes / 1_000_000_000.0)} GB"
        bytes >= 1_000_000L -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
        bytes >= 1_000L -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }
}
