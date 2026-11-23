package com.idiots3.riskscanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idiots3.riskscanner.data.BackgroundScanSchedule
import com.idiots3.riskscanner.report.JsonReportExporter
import com.idiots3.riskscanner.report.PdfExporter
import com.idiots3.riskscanner.ui.components.AppUpdateHelper
import com.idiots3.riskscanner.ui.components.SettingsIntents
import com.idiots3.riskscanner.ui.components.formatDate
import com.idiots3.riskscanner.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRubric: () -> Unit,
    onNavigateToApkScanner: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var bgTriggerStatus by remember { mutableStateOf<String?>(null) }
    val updateInfo = remember(context) { AppUpdateHelper.inspectLocal(context) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Tools", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Pre-install APK Inspector
            item {
                SettingsSectionCard(
                    icon = Icons.Default.FileOpen,
                    title = "Pre-install APK scanner",
                    subtitle = "Check a downloaded APK from Chrome, WhatsApp, or Files before installing it."
                ) {
                    Button(
                        onClick = onNavigateToApkScanner,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Inspect an APK file")
                    }
                }
            }

            // 2. Background Protection Schedule
            item {
                SettingsSectionCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "Background security guard",
                    subtitle = "Periodically checks installed apps and key device settings even while DroidScanner is closed. Uses low-RAM metadata checks only."
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BackgroundScanSchedule.entries.forEach { schedule ->
                                val label = when (schedule) {
                                    BackgroundScanSchedule.OFF -> "Off"
                                    BackgroundScanSchedule.EVERY_12_HOURS -> "Every 12h"
                                    BackgroundScanSchedule.DAILY -> "Daily"
                                }
                                FilterChip(
                                    selected = uiState.backgroundSchedule == schedule,
                                    onClick = { viewModel.setBackgroundSchedule(schedule) },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (uiState.lastBackgroundCheckTime > 0L) {
                            Text(
                                text = "Last background check: ${formatDate(uiState.lastBackgroundCheckTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.triggerBackgroundCheckNow()
                                bgTriggerStatus = "Background security check queued."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Run background check now")
                        }
                        bgTriggerStatus?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // 3. Audit Scope & Display Preferences
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Android,
                    title = "Scan scope & display",
                    subtitle = "Control which apps appear in your audit and how technical details are presented."
                ) {
                    SettingSwitchRow(
                        title = "Include pre-installed system apps",
                        subtitle = "Shows OEM and Android system packages in Overview & Apps. Does not affect change monitoring.",
                        checked = uiState.includeSystemApps,
                        onCheckedChange = { viewModel.toggleSystemApps(it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingSwitchRow(
                        title = "Plain-language summary mode",
                        subtitle = "Leads cards with everyday explanations and tucks hashes, z-scores, and X.509 internals into expandable Technical details.",
                        checked = uiState.plainLanguageMode,
                        onCheckedChange = { viewModel.setPlainLanguageMode(it) }
                    )
                }
            }

            // 4. Trusted & Reviewed Items
            item {
                val trustedApps = uiState.trustState.trustedApps.values.sortedBy { it.appName.lowercase() }
                val dismissedPosture = uiState.trustState.dismissedPosture.values.sortedBy { it.title }
                SettingsSectionCard(
                    icon = Icons.Default.VerifiedUser,
                    title = "Reviewed & trusted items (${trustedApps.size + dismissedPosture.size})",
                    subtitle = "Trusted apps stay quiet unless an update adds new permissions, enables special access, or changes the signing certificate."
                ) {
                    if (trustedApps.isEmpty() && dismissedPosture.isEmpty()) {
                        Text(
                            text = "No apps or device settings marked as trusted yet. You can mark any app as Reviewed & Trusted from its detail screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        trustedApps.forEach { rec ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rec.appName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${rec.packageName} · Trusted ${formatDate(rec.trustedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.untrustApp(rec.packageName) }) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Revoke")
                                }
                            }
                        }
                        dismissedPosture.forEach { rec ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rec.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "Acknowledged device setting",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.restorePostureCheck(rec.checkId) }) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Restore")
                                }
                            }
                        }
                    }
                }
            }

            // 5. Optional Usage Access (Behaviour Insights)
            item {
                SettingsSectionCard(
                    icon = Icons.Default.DataUsage,
                    title = "Optional behavioural insights",
                    subtitle = if (uiState.behaviourResult.permissionGranted) {
                        "Usage Access is enabled. DroidScanner checks 7-day background services and upload/download outliers locally."
                    } else {
                        "Usage Access is off. Enable it only if you want to spot apps running or uploading data without ever being opened."
                    }
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.markExternalSettingsOpened()
                            SettingsIntents.openUsageAccessSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.behaviourResult.permissionGranted) "Manage Usage Access" else "Open Usage Access Settings")
                    }
                }
            }

            // 6. Reports & Methodology
            item {
                SettingsSectionCard(
                    icon = Icons.Default.PictureAsPdf,
                    title = "Reports & scoring rubric",
                    subtitle = "Export your audit on-device as PDF or structured JSON. Nothing leaves your phone unless you share it."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val summary = uiState.deviceSummary ?: return@Button
                                scope.launch {
                                    exportStatus = "Building PDF…"
                                    val file = PdfExporter.export(
                                        context, summary, uiState.apps, uiState.devicePosture, uiState.recommendations
                                    )
                                    if (file != null) {
                                        exportStatus = "PDF ready to share."
                                        PdfExporter.share(context, file)
                                    } else {
                                        exportStatus = "Failed to export PDF."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                val summary = uiState.deviceSummary ?: return@OutlinedButton
                                scope.launch {
                                    exportStatus = "Building JSON…"
                                    val file = JsonReportExporter.export(
                                        context, summary, uiState.apps, uiState.devicePosture, uiState.recommendations
                                    )
                                    if (file != null) {
                                        exportStatus = "JSON audit ready to share."
                                        JsonReportExporter.share(context, file)
                                    } else {
                                        exportStatus = "Failed to export JSON."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("JSON")
                        }
                    }
                    exportStatus?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        onClick = onNavigateToRubric,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("How scoring works")
                    }
                }
            }

            // 7. App Updates (Obtainium & GitHub Releases)
            item {
                SettingsSectionCard(
                    icon = Icons.Default.SystemUpdate,
                    title = "App updates",
                    subtitle = when {
                        updateInfo.installedViaObtainium ->
                            "Automatic updates managed via Obtainium. You can trigger a manual update check anytime below."
                        updateInfo.obtainiumInstalled ->
                            "Obtainium is installed on this device. Open DroidScanner in Obtainium to enable automatic updates or check now."
                        else ->
                            "DroidScanner has zero INTERNET permission, so update checks hand off cleanly to Obtainium (recommended for automatic updates) or GitHub Releases in your browser."
                    }
                ) {
                    Text(
                        text = "Installed: v${updateInfo.versionName} (build ${updateInfo.versionCode}) · Recommended APK: ${updateInfo.recommendedAbi}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            viewModel.markExternalSettingsOpened()
                            if (updateInfo.obtainiumInstalled) {
                                val opened = AppUpdateHelper.launchObtainiumUpdate(context, updateInfo.obtainiumPackage)
                                if (opened) {
                                    updateStatus = "Opened DroidScanner in Obtainium."
                                } else {
                                    AppUpdateHelper.openGitHubReleases(context)
                                    updateStatus = "Opened GitHub Releases in your browser."
                                }
                            } else {
                                AppUpdateHelper.openGitHubReleases(context)
                                updateStatus = "Opened latest GitHub Release (pick ${updateInfo.recommendedAbi})."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (updateInfo.obtainiumInstalled) "Check for updates in Obtainium"
                            else "Check for updates on GitHub"
                        )
                    }
                    if (updateInfo.obtainiumInstalled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.markExternalSettingsOpened()
                                    AppUpdateHelper.openGitHubReleases(context)
                                    updateStatus = "Opened latest GitHub Release (pick ${updateInfo.recommendedAbi})."
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("GitHub Releases")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.markExternalSettingsOpened()
                                    AppUpdateHelper.openProjectSite(context)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Download page")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                viewModel.markExternalSettingsOpened()
                                AppUpdateHelper.openObtainiumSetup(context)
                                updateStatus = "Opened Obtainium 1-tap setup."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Set up Obtainium (auto-updates)")
                        }
                    }
                    updateStatus?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 8. Privacy & Honest Rootless Boundaries
            item {
                SettingsSectionCard(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy & rootless guarantee",
                    subtitle = "Built as a standalone offline guardian for everyday Android users."
                ) {
                    Text(
                        text = "• Zero INTERNET permission (removed in AndroidManifest.xml) — DroidScanner cannot send data anywhere even in theory.\n" +
                            "• Read-only ACCESS_NETWORK_STATE is used solely to detect active VPN tunnels and captive Wi-Fi networks locally.\n" +
                            "• Honest limits: without root or hardware attestation, no ordinary app can prove bootloader lock, OS kernel integrity, or Play Protect status. DroidScanner reports these limits rather than guessing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
