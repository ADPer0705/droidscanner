package com.idiots3.riskscanner.model

/**
 * Anomalous resource usage detected for an installed app using cross-app z-score statistics
 * and UsageStats / NetworkStats public Android APIs (requires optional user-granted Usage Access).
 */
data class BehaviourAnomaly(
    val type: String,
    val detail: String,
    val zScore: Float,
    val recommendation: String
)

data class BehaviourProfile(
    val packageName: String,
    val foregroundMs: Long,
    val foregroundServiceMs: Long,
    val lastTimeUsedMs: Long = 0L,
    val neverOpenedInWindow: Boolean = false,
    val totalNetworkBytes: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val anomalies: List<BehaviourAnomaly>,
    val lookbackDays: Int,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L
) {
    val hasAnomalies: Boolean get() = anomalies.isNotEmpty()

    fun formattedTotalBytes(): String = formatByteCount(totalNetworkBytes)
    fun formattedRxBytes(): String = formatByteCount(rxBytes)
    fun formattedTxBytes(): String = formatByteCount(txBytes)

    fun formattedForegroundTime(): String = formatDurationMs(foregroundMs)
    fun formattedForegroundServiceTime(): String = formatDurationMs(foregroundServiceMs)

    private fun formatByteCount(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "${"%.2f".format(bytes / 1_000_000_000.0)} GB"
        bytes >= 1_000_000L -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
        bytes >= 1_000L -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }

    private fun formatDurationMs(ms: Long): String {
        val mins = ms / 60_000L
        return when {
            mins >= 60 -> "${mins / 60}h ${mins % 60}m"
            mins > 0 -> "${mins}m"
            ms > 0 -> "<1m"
            else -> "0m"
        }
    }
}

data class BehaviourScanResult(
    val permissionGranted: Boolean,
    val profiles: Map<String, BehaviourProfile> = emptyMap(),
    val lookbackDays: Int = 7
) {
    val anomalyCount: Int get() = profiles.values.count { it.hasAnomalies }
}
