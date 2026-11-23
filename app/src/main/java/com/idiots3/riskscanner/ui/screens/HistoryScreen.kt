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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.idiots3.riskscanner.data.ScoreHistoryPoint
import com.idiots3.riskscanner.model.ChangeType
import com.idiots3.riskscanner.model.PermissionChange
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.ui.components.formatDate
import com.idiots3.riskscanner.ui.components.healthScoreColor
import com.idiots3.riskscanner.ui.components.riskColor
import com.idiots3.riskscanner.ui.components.riskLevelForScore
import com.idiots3.riskscanner.viewmodel.MainViewModel

private enum class TimelineFilter(val label: String) {
    ALL("All"),
    ALERTS("Alerts"),
    APPS("App changes"),
    DEVICE("Device configs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    onNavigateToDeviceHub: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf(TimelineFilter.ALL) }

    val events = remember(uiState.auditHistory.events, filter) {
        uiState.auditHistory.events.filter { ev ->
            when (filter) {
                TimelineFilter.ALL -> true
                TimelineFilter.ALERTS -> ev.type in setOf(
                    ChangeType.SIGNER_CHANGED,
                    ChangeType.SPECIAL_ACCESS_GRANTED,
                    ChangeType.TRUST_BROKEN,
                    ChangeType.PERMISSIONS_ADDED,
                    ChangeType.SCORE_INCREASED,
                    ChangeType.POSTURE_REGRESSED
                )
                TimelineFilter.APPS -> ev.type != ChangeType.POSTURE_REGRESSED
                TimelineFilter.DEVICE -> ev.type == ChangeType.POSTURE_REGRESSED
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Timeline", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.auditHistory.events.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAuditTimeline() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear timeline")
                        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.auditHistory.scoreHistory.size >= 2) {
                item {
                    ScoreTrendCard(points = uiState.auditHistory.scoreHistory)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimelineFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.label, maxLines = 1) }
                        )
                    }
                }
            }

            if (events.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No security changes recorded yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "New app installs, silent permission/special-access grants, certificate changes, and device-setting changes will appear here over time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = events,
                    key = { "${it.type.name}_${it.packageName}_${it.timestamp}" }
                ) { ev ->
                    TimelineEventCard(
                        event = ev,
                        onClick = {
                            if (ev.type == ChangeType.POSTURE_REGRESSED) {
                                onNavigateToDeviceHub()
                            } else if (ev.type != ChangeType.APP_REMOVED && ev.packageName.isNotBlank()) {
                                onNavigateToAppDetail(ev.packageName)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreTrendCard(points: List<ScoreHistoryPoint>) {
    val recent = points.takeLast(10)
    val latest = recent.last()
    val first = recent.first()
    val delta = latest.deviceScore - first.deviceScore

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Device Score Trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val deltaText = when {
                    delta > 0 -> "+$delta pts"
                    delta < 0 -> "$delta pts"
                    else -> "Steady"
                }
                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = healthScoreColor(latest.deviceScore)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                recent.forEach { pt ->
                    val frac = (pt.deviceScore / 100f).coerceIn(0.08f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((48 * frac).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(healthScoreColor(pt.deviceScore).copy(alpha = 0.8f))
                        )
                    }
                }
            }
            Text(
                text = "Last ${recent.size} recorded check(s) · Latest: ${latest.deviceScore}/100",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimelineEventCard(
    event: PermissionChange,
    onClick: () -> Unit
) {
    val icon = when (event.type) {
        ChangeType.SIGNER_CHANGED, ChangeType.TRUST_BROKEN -> Icons.Default.Warning
        ChangeType.SPECIAL_ACCESS_GRANTED -> Icons.Default.AdminPanelSettings
        ChangeType.NEW_APP -> Icons.Default.NewReleases
        ChangeType.PERMISSIONS_ADDED -> Icons.Default.AddAlert
        ChangeType.PERMISSIONS_REMOVED, ChangeType.APP_REMOVED -> Icons.Default.RemoveCircleOutline
        ChangeType.SCORE_INCREASED -> Icons.AutoMirrored.Filled.TrendingUp
        ChangeType.POSTURE_REGRESSED -> Icons.Default.Security
        ChangeType.UPDATED -> Icons.Default.SystemUpdate
    }
    val tint = when (event.type) {
        ChangeType.SIGNER_CHANGED,
        ChangeType.SPECIAL_ACCESS_GRANTED,
        ChangeType.TRUST_BROKEN,
        ChangeType.PERMISSIONS_ADDED,
        ChangeType.SCORE_INCREASED -> riskColor(RiskLevel.CRITICAL)
        ChangeType.NEW_APP,
        ChangeType.POSTURE_REGRESSED -> riskColor(RiskLevel.HIGH)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${event.type.label}: ${event.appName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                event.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (event.addedSpecialAccess.isNotEmpty()) {
                    Text(
                        text = "Special access: ${event.addedSpecialAccess.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = riskColor(RiskLevel.CRITICAL)
                    )
                }
                if (event.addedPermissions.isNotEmpty()) {
                    Text(
                        text = "Added: ${event.addedPermissions.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = riskColor(RiskLevel.CRITICAL)
                    )
                }
                if (event.removedPermissions.isNotEmpty()) {
                    Text(
                        text = "Removed: ${event.removedPermissions.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (event.timestamp > 0L) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatDate(event.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (event.newScore > 0) {
                Text(
                    text = "${event.newScore}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = riskColor(riskLevelForScore(event.newScore))
                )
            }
        }
    }
}
