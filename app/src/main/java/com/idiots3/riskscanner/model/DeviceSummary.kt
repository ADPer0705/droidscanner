package com.idiots3.riskscanner.model

data class DeviceSummary(
    val totalApps: Int,
    val criticalApps: Int,
    val highApps: Int,
    val mediumApps: Int,
    val lowApps: Int,
    val totalPermissionsGranted: Int,
    val categoryBreakdown: Map<PermissionCategory, Int>, // category -> number of apps holding permissions in it
    val topRiskApps: List<AppInfo>,
    val sideloadedApps: Int,
    val specialAccessApps: Int = 0,
    val hiddenApps: Int = 0,
    val aiAnalyzedApps: Int = 0,
    val aiUnexplainedApps: Int = 0
) {
    val highOrCritical: Int get() = criticalApps + highApps
}
