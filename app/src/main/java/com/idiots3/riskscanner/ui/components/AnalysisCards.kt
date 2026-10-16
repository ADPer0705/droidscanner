package com.idiots3.riskscanner.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idiots3.riskscanner.model.*
import com.idiots3.riskscanner.report.PdfExporter
import com.idiots3.riskscanner.ui.screens.getCategoryIcon
import com.idiots3.riskscanner.ui.screens.getRiskColor
import com.idiots3.riskscanner.ui.theme.RiskColors
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// On-device AI status
// ---------------------------------------------------------------------------

@Composable
fun AiProgressCard(
    active: Boolean,
    processed: Int,
    total: Int,
    available: Boolean,
    modifier: Modifier = Modifier
) {
    if (!active && (total == 0 || !available)) {
        if (!available) {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "On-device purpose model unavailable on this build — rules-only scoring is active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (active) "On-device AI purpose check running…" else "On-device AI purpose check complete",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (total > 0 && active) {
                LinearProgressIndicator(
                    progress = { (processed.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "$processed of $total apps analysed (offline, embedding comparison)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Purpose fit ("does this app need these permissions?")
// ---------------------------------------------------------------------------

@Composable
fun PurposeFitCard(
    assessment: AiAssessment?,
    aiActive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Does this app need these permissions?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            when {
                assessment == null && aiActive -> Text(
                    text = "Analysing the app's purpose on-device…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                assessment == null -> Text(
                    text = "Purpose check not run for this app (no sensitive access to compare).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                !assessment.modelUsed -> Text(
                    text = assessment.note ?: "Purpose check unavailable for this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Looks like: ${assessment.purposeLabel ?: "unclear purpose"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "similarity ${formatSimilarity(assessment.purposeSimilarity)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (assessment.permissionFit.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            assessment.permissionFit.entries
                                .sortedBy { fitOrder(it.value.status) }
                                .forEach { (category, fit) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = getCategoryIcon(category),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = category.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = fit.evidence,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        FitStatusChip(status = fit.status, similarity = fit.similarity)
                                    }
                                }
                        }
                    }

                    if (assessment.unexplainedCategories.isNotEmpty()) {
                        Surface(
                            color = RiskColors.MediumBackground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Not explained by the app's apparent purpose: " +
                                    assessment.unexplainedCategories.joinToString(", ") { it.label } +
                                    ". This is a weak signal, not proof — the rule score above stays authoritative.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Text(
                        text = "Similarity is a text-overlap score between the app's own metadata and a curated " +
                            "permission-use description. It is never a probability and never marks an app as malicious.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

private fun fitOrder(status: FitStatus): Int = when (status) {
    FitStatus.UNEXPLAINED -> 0
    FitStatus.UNCLEAR -> 1
    FitStatus.FITS -> 2
    FitStatus.UNKNOWN -> 3
}

@Composable
fun FitStatusChip(
    status: FitStatus,
    similarity: Float,
    modifier: Modifier = Modifier
) {
    val (background, foreground) = when (status) {
        FitStatus.FITS -> Pair(RiskColors.LowBackground, Color(0xFF1B7F3B))
        FitStatus.UNCLEAR -> Pair(RiskColors.MediumBackground, Color(0xFF8A6D00))
        FitStatus.UNEXPLAINED -> Pair(RiskColors.HighBackground, RiskColors.High)
        FitStatus.UNKNOWN -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(color = background, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = if (status == FitStatus.UNKNOWN) status.label else "${status.label} ${formatSimilarity(similarity)}",
            color = foreground,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatSimilarity(value: Float): String = "%.2f".format(value)

// ---------------------------------------------------------------------------
// Score breakdown (every point traceable)
// ---------------------------------------------------------------------------

@Composable
fun ScoreBreakdownCard(app: AppInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Rule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Score breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Rules ${app.ruleScore} + AI ${app.aiDelta} = ${app.riskScore}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (app.scoreBreakdown.isEmpty()) {
                Text(
                    text = "No risk drivers: this app holds no scored permissions or special access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                app.scoreBreakdown.forEach { line ->
                    if (line.points == 0 && line.source == ScoreSource.AI) {
                        Text(
                            text = line.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        return@forEach
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = if (line.source == ScoreSource.AI) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (line.source == ScoreSource.AI) "AI" else "RULES",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = line.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "+${line.points}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = getRiskColor(pointsColorLevel(line.points))
                        )
                    }
                }
            }
        }
    }
}

private fun pointsColorLevel(points: Int): RiskLevel = when {
    points >= 15 -> RiskLevel.CRITICAL
    points >= 8 -> RiskLevel.HIGH
    points >= 4 -> RiskLevel.MEDIUM
    else -> RiskLevel.LOW
}

// ---------------------------------------------------------------------------
// Static APK checks
// ---------------------------------------------------------------------------

@Composable
fun StaticChecksCard(
    checks: StaticChecks?,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
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
                Icon(
                    Icons.Default.FindInPage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Static APK checks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (loading || checks == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Inspecting the installed APK…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            if (checks.hashError != null) {
                Text(
                    text = checks.hashError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            CheckRow(
                label = "Exported components",
                value = "${checks.exportedComponentCount} (activities ${checks.exportedActivities}, services ${checks.exportedServices}, receivers ${checks.exportedReceivers}, providers ${checks.exportedProviders})",
                warn = checks.exportedComponentCount > 0
            )
            CheckRow(
                label = "Cleartext traffic",
                value = when (checks.cleartextTraffic) {
                    true -> "Allowed (HTTP permitted)"
                    false -> "Blocked"
                    null -> "Not specified"
                },
                warn = checks.cleartextTraffic == true
            )
            CheckRow(
                label = "Debuggable build",
                value = if (checks.debuggable) "Yes — debug builds leak data" else "No",
                warn = checks.debuggable
            )
            CheckRow(
                label = "SDK range",
                value = "target ${checks.targetSdk} · min ${checks.minSdk}",
                warn = false
            )

            if (checks.sha256 != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Base APK SHA-256",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { copyToClipboard(context, "APK SHA-256", checks.sha256) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy hash",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = checks.sha256,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Hash computed locally and never sent anywhere. Hash lookup against threat intelligence is intentionally disabled to keep the app fully offline.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun CheckRow(label: String, value: String, warn: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (warn) Icons.Default.Warning else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (warn) RiskColors.High else Color(0xFF2E7D32),
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    } catch (_: Throwable) {
    }
}

// ---------------------------------------------------------------------------
// Recently changed apps (install monitoring + permission diff)
// ---------------------------------------------------------------------------

@Composable
fun RecentChangesCard(
    changes: List<PermissionChange>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (changes.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, RiskColors.High.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = RiskColors.High,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Changed since last scan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${changes.size} app(s) were installed, updated or changed permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            changes.take(8).forEach { change ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAppClick(change.packageName) }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = when (change.type) {
                            ChangeType.NEW_APP -> Icons.Default.NewReleases
                            ChangeType.PERMISSIONS_ADDED -> Icons.Default.AddAlert
                            ChangeType.PERMISSIONS_REMOVED -> Icons.Default.RemoveCircleOutline
                            ChangeType.SCORE_INCREASED -> Icons.Default.TrendingUp
                            ChangeType.UPDATED -> Icons.Default.SystemUpdate
                        },
                        contentDescription = null,
                        tint = when (change.type) {
                            ChangeType.PERMISSIONS_ADDED, ChangeType.SCORE_INCREASED -> RiskColors.Critical
                            ChangeType.NEW_APP -> RiskColors.High
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${change.type.label}: ${change.appName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (change.addedPermissions.isNotEmpty()) {
                            Text(
                                text = "Added: ${change.addedPermissions.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RiskColors.Critical
                            )
                        }
                        if (change.removedPermissions.isNotEmpty()) {
                            Text(
                                text = "Removed: ${change.removedPermissions.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (change.newScore != change.oldScore) {
                            Text(
                                text = "Score ${change.oldScore} → ${change.newScore}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    if (change.newScore > 0) {
                        Text(
                            text = "${change.newScore}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = getRiskColor(RiskLevel.entries.first { change.newScore >= it.minScore && change.newScore <= it.maxScore })
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Report + rubric actions
// ---------------------------------------------------------------------------

@Composable
fun ReportActionsCard(
    summary: DeviceSummary,
    apps: List<AppInfo>,
    onNavigateToRubric: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Report & methodology",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigateToRubric() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scoring rules")
                }
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        status = null
                        scope.launch {
                            val file = PdfExporter.export(context, summary, apps)
                            busy = false
                            if (file != null) {
                                status = "Report saved to the app cache and ready to share."
                                PdfExporter.share(context, file)
                            } else {
                                status = "Could not generate the PDF report."
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (busy) "Building…" else "PDF report")
                }
            }
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "The report is generated on-device and only leaves the phone if you choose to share it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
