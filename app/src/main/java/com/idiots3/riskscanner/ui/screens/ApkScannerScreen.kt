package com.idiots3.riskscanner.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idiots3.riskscanner.model.PreInstallScanResult
import com.idiots3.riskscanner.model.PreInstallVerdict
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.ui.components.StaticChecksCard
import com.idiots3.riskscanner.ui.components.formatBytes
import com.idiots3.riskscanner.ui.components.riskBgColor
import com.idiots3.riskscanner.ui.components.riskColor
import com.idiots3.riskscanner.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkScannerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.scanApkUri(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pre-Install APK Scanner", fontWeight = FontWeight.Bold) },
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Inspect an APK before installing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Pick any .apk file from Downloads, Chrome, WhatsApp, or Files. DroidScanner checks its permissions, signing certificate (including mismatch against already-installed apps), impersonation tricks, hidden payloads, and embedded trackers — 100% offline with zero storage permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                apkPicker.launch(
                                    arrayOf(
                                        "application/vnd.android.package-archive",
                                        "application/octet-stream"
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !uiState.apkScanLoading
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (uiState.apkScanResult == null) "Choose APK file" else "Scan another APK file")
                        }
                    }
                }
            }

            if (uiState.apkScanLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Column {
                                Text("Inspecting APK safely on-device…", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Checking manifest, signing block, embedded payloads, trackers, and code APIs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            uiState.apkScanResult?.let { result ->
                item { PreInstallVerdictCard(result = result) }

                if (result.highlights.isNotEmpty()) {
                    item { PreInstallHighlightsCard(highlights = result.highlights) }
                }

                result.appInfo?.let { app ->
                    if (app.riskReasons.isNotEmpty()) {
                        item { WhyFlaggedPanel(reasons = app.riskReasons) }
                    }
                }

                item {
                    StaticChecksCard(
                        checks = result.staticChecks,
                        loading = false
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { viewModel.clearApkScanResult() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Clear inspection result")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreInstallVerdictCard(result: PreInstallScanResult) {
    val level = when (result.verdict) {
        PreInstallVerdict.LOOKS_SAFE -> RiskLevel.LOW
        PreInstallVerdict.CAUTION -> RiskLevel.MEDIUM
        PreInstallVerdict.HIGH_RISK -> RiskLevel.HIGH
        PreInstallVerdict.DO_NOT_INSTALL -> RiskLevel.CRITICAL
    }
    val color = riskColor(level)
    val bg = riskBgColor(level)
    val icon = when (result.verdict) {
        PreInstallVerdict.LOOKS_SAFE -> Icons.Default.CheckCircle
        PreInstallVerdict.CAUTION -> Icons.Default.Warning
        PreInstallVerdict.HIGH_RISK -> Icons.Default.Warning
        PreInstallVerdict.DO_NOT_INSTALL -> Icons.Default.Dangerous
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.verdict.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = "${result.fileName} · ${formatBytes(result.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                result.appInfo?.let { app ->
                    Surface(color = color, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "${app.riskScore}/100",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            result.appInfo?.let { app ->
                Text(
                    text = "${app.appName} (${app.packageName}) · v${app.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = result.summaryMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PreInstallHighlightsCard(highlights: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Key findings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            highlights.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.startsWith("CRITICAL:")) riskColor(RiskLevel.CRITICAL)
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
