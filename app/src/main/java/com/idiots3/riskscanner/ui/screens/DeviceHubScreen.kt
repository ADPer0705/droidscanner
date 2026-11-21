package com.idiots3.riskscanner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.BehaviourProfile
import com.idiots3.riskscanner.model.BehaviourScanResult
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.SpecialAccessGrantState
import com.idiots3.riskscanner.ui.components.AppIconImage
import com.idiots3.riskscanner.ui.components.DevicePostureCard
import com.idiots3.riskscanner.ui.components.RiskBadge
import com.idiots3.riskscanner.ui.components.SettingsIntents
import com.idiots3.riskscanner.ui.components.riskBgColor
import com.idiots3.riskscanner.ui.components.riskColor
import com.idiots3.riskscanner.viewmodel.MainViewModel
import com.idiots3.riskscanner.viewmodel.QuickAppFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceHubScreen(
    viewModel: MainViewModel,
    onNavigateToAppsWithSpecialAccess: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Device & Access",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.scanApps() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh checks")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.devicePosture?.let { posture ->
                item {
                    DevicePostureCard(
                        posture = posture,
                        onReviewApps = {
                            viewModel.applyDashboardJumpFilter(quickFilter = QuickAppFilter.SPECIAL_ACCESS)
                            onNavigateToAppsWithSpecialAccess()
                        },
                        onExternalSettingsOpened = { viewModel.markExternalSettingsOpened() }
                    )
                }
            }

            item {
                SpecialAccessHubCard(
                    apps = uiState.apps,
                    onAppClick = onNavigateToAppDetail,
                    onOpenAccessibility = {
                        viewModel.markExternalSettingsOpened()
                        SettingsIntents.openAccessibilitySettings(context)
                    },
                    onOpenDeviceAdmin = {
                        viewModel.markExternalSettingsOpened()
                        SettingsIntents.openDeviceAdminSettings(context)
                    },
                    onOpenNotificationListeners = {
                        viewModel.markExternalSettingsOpened()
                        SettingsIntents.openNotificationListenerSettings(context)
                    },
                    onOpenOverlay = {
                        viewModel.markExternalSettingsOpened()
                        SettingsIntents.openOverlaySettings(context)
                    },
                    onOpenInstallApps = {
                        viewModel.markExternalSettingsOpened()
                        SettingsIntents.openInstallUnknownAppsSettings(context)
                    },
                    onOpenAllFiles = {
                        viewModel.markExternalSettingsOpened()
                        SettingsIntents.openAllFilesAccessSettings(context)
                    }
                )
            }

            item {
                BehaviourInsightsHubCard(
                    result = uiState.behaviourResult,
                    apps = uiState.apps,
                    onGrantUsageAccess = {
                        viewModel.markExternalSettingsOpened()
                        SettingsIntents.openUsageAccessSettings(context)
                    },
                    onAppClick = onNavigateToAppDetail
                )
            }
        }
    }
}

@Composable
private fun SpecialAccessHubCard(
    apps: List<AppInfo>,
    onAppClick: (String) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenNotificationListeners: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenInstallApps: () -> Unit,
    onOpenAllFiles: () -> Unit
) {
    val a11yEnabled = apps.filter { it.specialAccess.accessibilityEnabled }
    val adminsActive = apps.filter { it.specialAccess.deviceAdminActive }
    val notifEnabled = apps.filter { it.specialAccess.notificationListenerEnabled }
    val overlayHolders = apps.filter {
        it.specialAccess.overlayEnabled || it.specialAccess.overlayState == SpecialAccessGrantState.DECLARED_UNVERIFIED
    }
    val installerHolders = apps.filter {
        it.specialAccess.installPackagesEnabled || it.specialAccess.installPackagesState == SpecialAccessGrantState.DECLARED_UNVERIFIED
    }
    val allFilesHolders = apps.filter { it.specialAccess.allFilesAccessDeclared }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Special Access Hub",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "High-privilege capabilities controlled outside normal permission prompts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SpecialAccessGroup(
                icon = Icons.Default.AccessibilityNew,
                title = "Accessibility Services (enabled)",
                apps = a11yEnabled,
                emptyText = "No apps have an enabled accessibility service.",
                onSettingsClick = onOpenAccessibility,
                onAppClick = onAppClick
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SpecialAccessGroup(
                icon = Icons.Default.AdminPanelSettings,
                title = "Device Administrators (active)",
                apps = adminsActive,
                emptyText = "No active device administrators detected.",
                onSettingsClick = onOpenDeviceAdmin,
                onAppClick = onAppClick
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SpecialAccessGroup(
                icon = Icons.Default.NotificationsActive,
                title = "Notification Access (enabled)",
                apps = notifEnabled,
                emptyText = "No apps are reading notifications.",
                onSettingsClick = onOpenNotificationListeners,
                onAppClick = onAppClick
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SpecialAccessGroup(
                icon = Icons.Default.Layers,
                title = "Display Over Other Apps (enabled / declared)",
                apps = overlayHolders,
                emptyText = "No apps in this scan request overlay permission.",
                onSettingsClick = onOpenOverlay,
                onAppClick = onAppClick
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SpecialAccessGroup(
                icon = Icons.Default.SystemUpdateAlt,
                title = "Install Unknown Apps (enabled / declared)",
                apps = installerHolders,
                emptyText = "No apps in this scan request APK install capability.",
                onSettingsClick = onOpenInstallApps,
                onAppClick = onAppClick
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SpecialAccessGroup(
                icon = Icons.Default.FolderSpecial,
                title = "All-Files Access (declared)",
                apps = allFilesHolders,
                emptyText = "No apps in this scan declare MANAGE_EXTERNAL_STORAGE.",
                onSettingsClick = onOpenAllFiles,
                onAppClick = onAppClick
            )
        }
    }
}

@Composable
private fun SpecialAccessGroup(
    icon: ImageVector,
    title: String,
    apps: List<AppInfo>,
    emptyText: String,
    onSettingsClick: () -> Unit,
    onAppClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "$title (${apps.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onSettingsClick) {
                Text("Settings", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }

        if (apps.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            apps.take(5).forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAppClick(app.packageName) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIconImage(
                        packageName = app.packageName,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.installerSource,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    RiskBadge(riskLevel = app.riskLevel)
                }
            }
            if (apps.size > 5) {
                Text(
                    text = "+ ${apps.size - 5} more — open Settings or filter Apps by Special access",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun BehaviourInsightsHubCard(
    result: BehaviourScanResult,
    apps: List<AppInfo>,
    onGrantUsageAccess: () -> Unit,
    onAppClick: (String) -> Unit
) {
    val appMap = apps.associateBy { it.packageName }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.DataUsage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Behavioural Insights (optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (result.permissionGranted) {
                            "Analysing ${result.lookbackDays}-day usage & network statistics on-device"
                        } else {
                            "Requires optional Usage Access to spot apps active in the background without being opened."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!result.permissionGranted) {
                OutlinedButton(
                    onClick = onGrantUsageAccess,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enable Usage Access in Settings")
                }
            } else {
                val anomalous = result.profiles.values
                    .filter { it.hasAnomalies }
                    .sortedByDescending { it.anomalies.maxOf { a -> a.zScore } }
                if (anomalous.isEmpty()) {
                    Surface(
                        color = riskBgColor(RiskLevel.LOW),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No statistical usage or network outliers detected across ${result.profiles.size} active apps over the past ${result.lookbackDays} days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = riskColor(RiskLevel.LOW),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    anomalous.take(6).forEach { profile ->
                        BehaviourOutlierRow(
                            app = appMap[profile.packageName],
                            profile = profile,
                            onClick = { onAppClick(profile.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BehaviourOutlierRow(
    app: AppInfo?,
    profile: BehaviourProfile,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIconImage(
                packageName = profile.packageName,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = app?.appName ?: profile.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                profile.anomalies.firstOrNull()?.let { anomaly ->
                    Text(
                        text = "${anomaly.type}: ${anomaly.detail}",
                        style = MaterialTheme.typography.labelSmall,
                        color = riskColor(RiskLevel.HIGH)
                    )
                }
            }
        }
    }
}
