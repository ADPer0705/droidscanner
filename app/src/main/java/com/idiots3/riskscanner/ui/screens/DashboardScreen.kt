package com.idiots3.riskscanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.DeviceSummary
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.RecommendationSummary
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.ui.components.AiProgressCard
import com.idiots3.riskscanner.ui.components.AppIconImage
import com.idiots3.riskscanner.ui.components.DevicePostureSummaryCard
import com.idiots3.riskscanner.ui.components.RecentChangesCard
import com.idiots3.riskscanner.ui.components.RecommendationsCard
import com.idiots3.riskscanner.ui.components.ReportActionsCard
import com.idiots3.riskscanner.ui.components.RiskBadge
import com.idiots3.riskscanner.ui.components.getCategoryIcon
import com.idiots3.riskscanner.ui.components.healthScoreColor
import com.idiots3.riskscanner.ui.components.riskBgColor
import com.idiots3.riskscanner.ui.components.riskColor
import com.idiots3.riskscanner.viewmodel.MainViewModel
import com.idiots3.riskscanner.viewmodel.QuickAppFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToDeviceHub: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    onNavigateToRubric: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToApkScanner: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "DroidScanner",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Security timeline"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings and tools"
                        )
                    }
                    IconButton(onClick = { viewModel.scanApps() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh scan"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "Auditing installed applications...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Scan Failed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = uiState.error ?: "An unexpected error occurred during scan.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = { viewModel.scanApps() },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry Audit")
                            }
                        }
                    }
                }
            }

            else -> {
                val summary = uiState.deviceSummary
                if (summary != null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!uiState.onboardingCompleted) {
                            item {
                                QuickStartCheckupCard(
                                    onDismiss = { viewModel.completeOnboarding() },
                                    onOpenDeviceHub = {
                                        viewModel.completeOnboarding()
                                        onNavigateToDeviceHub()
                                    },
                                    onOpenSettings = {
                                        viewModel.completeOnboarding()
                                        onNavigateToSettings()
                                    }
                                )
                            }
                        }

                        item {
                            SecurityOverviewCard(
                                summary = summary,
                                recommendations = uiState.recommendations
                            )
                        }

                        uiState.recommendations?.let { recs ->
                            item {
                                RecommendationsCard(
                                    recommendations = recs,
                                    onNavigateToAppDetail = onNavigateToAppDetail,
                                    onExternalSettingsOpened = { viewModel.markExternalSettingsOpened() },
                                    onOpenApkScanner = onNavigateToApkScanner
                                )
                            }
                        }

                        uiState.devicePosture?.let { posture ->
                            item {
                                DevicePostureSummaryCard(
                                    posture = posture,
                                    onOpenDeviceTab = onNavigateToDeviceHub
                                )
                            }
                        }

                        item {
                            PreInstallScannerBannerCard(onClick = onNavigateToApkScanner)
                        }

                        item {
                            RiskDistributionRow(
                                summary = summary,
                                onSelectRiskLevel = { level ->
                                    viewModel.applyDashboardJumpFilter(riskLevel = level)
                                    onNavigateToApps()
                                }
                            )
                        }

                        item {
                            RecentChangesCard(
                                changes = uiState.recentChanges,
                                onAppClick = onNavigateToAppDetail,
                                onViewTimeline = onNavigateToHistory
                            )
                        }

                        item {
                            SideloadedWarningCard(
                                count = summary.sideloadedApps,
                                onClick = {
                                    if (summary.sideloadedApps > 0) {
                                        viewModel.applyDashboardJumpFilter(quickFilter = QuickAppFilter.SIDELOADED)
                                        onNavigateToApps()
                                    }
                                }
                            )
                        }

                        item {
                            AiProgressCard(
                                active = uiState.aiActive,
                                processed = uiState.aiProcessed,
                                total = uiState.aiTotal,
                                available = uiState.aiAvailable
                            )
                        }

                        item {
                            CategoryBreakdownSection(
                                summary = summary,
                                onSelectCategory = { category ->
                                    viewModel.applyDashboardJumpFilter(category = category)
                                    onNavigateToApps()
                                }
                            )
                        }

                        item {
                            TopRiskyAppsSection(
                                apps = summary.topRiskApps,
                                onAppClick = onNavigateToAppDetail
                            )
                        }

                        item {
                            ReportActionsCard(
                                summary = summary,
                                apps = uiState.apps,
                                posture = uiState.devicePosture,
                                recommendations = uiState.recommendations,
                                onNavigateToRubric = onNavigateToRubric
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    viewModel.clearAllFilters()
                                    onNavigateToApps()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Browse All Apps (${summary.totalApps})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityOverviewCard(
    summary: DeviceSummary,
    recommendations: RecommendationSummary?,
    modifier: Modifier = Modifier
) {
    val healthScore = recommendations?.deviceScore ?: run {
        val top10 = summary.topRiskApps.take(10)
        val avgRisk = if (top10.isNotEmpty()) top10.map { it.riskScore }.average().toInt() else 0
        (100 - avgRisk).coerceIn(0, 100)
    }

    val healthColor = healthScoreColor(healthScore)
    val (healthLabel, healthIcon) = when {
        healthScore >= 75 -> "Good Security Health" to Icons.Filled.CheckCircle
        healthScore >= 50 -> "Moderate Risk Detected" to Icons.Filled.Warning
        healthScore >= 25 -> "High Risk Detected" to Icons.Filled.WarningAmber
        else -> "Critical Risk Detected" to Icons.Filled.GppBad
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, healthColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Device Security Score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = healthColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, healthColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = healthIcon,
                            contentDescription = null,
                            tint = healthColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = healthLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = healthColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier.size(106.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { healthScore / 100f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 10.dp,
                        color = healthColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$healthScore",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "/ 100",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OverviewStatRow(
                        icon = Icons.Filled.Apps,
                        iconTint = MaterialTheme.colorScheme.primary,
                        text = "${summary.totalApps} Audited Apps"
                    )
                    OverviewStatRow(
                        icon = Icons.Filled.Key,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        text = "${summary.totalPermissionsGranted} Permissions Granted"
                    )
                    val threatColor = if (summary.highOrCritical > 0) riskColor(RiskLevel.CRITICAL)
                    else MaterialTheme.colorScheme.onSurface
                    OverviewStatRow(
                        icon = Icons.Filled.Dangerous,
                        iconTint = threatColor,
                        text = "${summary.highOrCritical} High/Critical App(s)",
                        textColor = threatColor
                    )
                    val saColor = if (summary.specialAccessApps > 0) riskColor(RiskLevel.HIGH)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    OverviewStatRow(
                        icon = Icons.Filled.Security,
                        iconTint = saColor,
                        text = "${summary.specialAccessApps} verified special-access app(s)",
                        textColor = if (summary.specialAccessApps > 0) saColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (recommendations != null && recommendations.scoreBreakdown.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recommendations.scoreBreakdown.forEach { (label, value) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$value",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = healthScoreColor(value)
                                )
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewStatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    text: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
fun RiskDistributionRow(
    summary: DeviceSummary,
    onSelectRiskLevel: (RiskLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Risk Distribution",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RiskCountCard(
                level = RiskLevel.CRITICAL,
                count = summary.criticalApps,
                onClick = { onSelectRiskLevel(RiskLevel.CRITICAL) },
                modifier = Modifier.weight(1f)
            )
            RiskCountCard(
                level = RiskLevel.HIGH,
                count = summary.highApps,
                onClick = { onSelectRiskLevel(RiskLevel.HIGH) },
                modifier = Modifier.weight(1f)
            )
            RiskCountCard(
                level = RiskLevel.MEDIUM,
                count = summary.mediumApps,
                onClick = { onSelectRiskLevel(RiskLevel.MEDIUM) },
                modifier = Modifier.weight(1f)
            )
            RiskCountCard(
                level = RiskLevel.LOW,
                count = summary.lowApps,
                onClick = { onSelectRiskLevel(RiskLevel.LOW) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RiskCountCard(
    level: RiskLevel,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = riskColor(level)
    val bgColor = riskBgColor(level)

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$count",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = level.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}

@Composable
fun CategoryBreakdownSection(
    summary: DeviceSummary,
    onSelectCategory: (PermissionCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Permission Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tap to filter",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val categories = PermissionCategory.entries.filter { it != PermissionCategory.OTHER }
        val chunkedCategories = categories.chunked(3)

        chunkedCategories.forEach { rowCategories ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCategories.forEach { category ->
                    val appCount = summary.categoryBreakdown[category] ?: 0
                    val icon = getCategoryIcon(category)

                    Card(
                        onClick = { onSelectCategory(category) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = category.label,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = category.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "$appCount apps",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (rowCategories.size < 3) {
                    repeat(3 - rowCategories.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun TopRiskyAppsSection(
    apps: List<AppInfo>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val topApps = apps.take(5)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Top Risky Apps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Top ${topApps.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (topApps.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No applications detected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topApps.forEach { app ->
                    val levelColor = riskColor(app.riskLevel)
                    Card(
                        onClick = { onAppClick(app.packageName) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, levelColor.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIconImage(
                                packageName = app.packageName,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = app.appName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${app.permissions.count { it.isGranted }} perms granted · ${app.installerSource}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                RiskBadge(riskLevel = app.riskLevel)
                                Text(
                                    text = "Score: ${app.riskScore}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = levelColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SideloadedWarningCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highColor = riskColor(RiskLevel.HIGH)
    val highBg = riskBgColor(RiskLevel.HIGH)
    val lowColor = riskColor(RiskLevel.LOW)
    val lowBg = riskBgColor(RiskLevel.LOW)

    if (count > 0) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = highBg),
            border = BorderStroke(1.dp, highColor.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(highColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = highColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "$count Sideloaded App${if (count > 1) "s" else ""} Detected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = highColor
                    )
                    Text(
                        text = "Installed outside recognized stores. Tap to inspect sideloaded apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = lowBg),
            border = BorderStroke(1.dp, lowColor.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(lowColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = lowColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "No Sideloaded Apps Detected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = lowColor
                    )
                    Text(
                        text = "All audited user apps were installed from recognized app stores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStartCheckupCard(
    onDismiss: () -> Unit,
    onOpenDeviceHub: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Welcome to DroidScanner — 3-step checkup",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "1. Fix any red device settings (Private DNS, lock-screen OTP privacy, CA certs).\n" +
                    "2. Inspect High/Critical apps — mark legitimate tools you trust so alerts stay focused.\n" +
                    "3. Keep Background Guard enabled in Settings to catch silent permission changes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onOpenDeviceHub, shape = RoundedCornerShape(8.dp)) {
                    Text("Check device", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(8.dp)) {
                    Text("Settings", fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Got it", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PreInstallScannerBannerCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.FileOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Scan an APK before installing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Check downloaded APKs for fake updates, trackers, or hidden payloads offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
