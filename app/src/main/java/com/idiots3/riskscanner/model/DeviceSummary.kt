package com.idiots3.riskscanner.model

data class DeviceSummary(
    val totalApps: Int,
    val criticalApps: Int,
    val highApps: Int,
    val mediumApps: Int,
    val lowApps: Int,
    val totalPermissionsGranted: Int,
    val categoryBreakdown: Map<PermissionCategory, Int>, // category -> number of apps with perms in that category
    val topRiskApps: List<AppInfo>,
    val sideloadedApps: Int
)

