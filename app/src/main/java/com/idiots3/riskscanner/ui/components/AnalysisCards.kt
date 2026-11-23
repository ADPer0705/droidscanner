package com.idiots3.riskscanner.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.idiots3.riskscanner.model.AiAssessment
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.BehaviourProfile
import com.idiots3.riskscanner.model.ChangeType
import com.idiots3.riskscanner.model.DevicePosture
import com.idiots3.riskscanner.model.DeviceSummary
import com.idiots3.riskscanner.model.FitStatus
import com.idiots3.riskscanner.model.PermissionChange
import com.idiots3.riskscanner.model.PermissionFit
import com.idiots3.riskscanner.model.PostureAction
import com.idiots3.riskscanner.model.PostureCheck
import com.idiots3.riskscanner.model.PostureStatus
import com.idiots3.riskscanner.model.RecommendationAction
import com.idiots3.riskscanner.model.RecommendationPriority
import com.idiots3.riskscanner.model.RecommendationSummary
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.ScoreSource
import com.idiots3.riskscanner.model.SecurityRecommendation
import com.idiots3.riskscanner.model.StaticChecks
import com.idiots3.riskscanner.report.PdfExporter
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Rootless device-security posture (compact overview card + detailed checks)
// ---------------------------------------------------------------------------

@Composable
fun DevicePostureSummaryCard(
    posture: DevicePosture,
    onOpenDeviceTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val headline = when {
        posture.needsAttention > 0 -> "${posture.needsAttention} protection issue(s) need attention"
        posture.needsReview > 0 -> "${posture.needsReview} protection setting(s) to review"
        else -> "All confirmed device checks look good"
    }
    val headlineColor = when {
        posture.needsAttention > 0 -> riskColor(RiskLevel.CRITICAL)
        posture.needsReview > 0 -> riskColor(RiskLevel.HIGH)
        else -> riskColor(RiskLevel.LOW)
    }

    Card(
        onClick = onOpenDeviceTab,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, headlineColor.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = headlineColor,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Device protection posture",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodySmall,
                    color = headlineColor
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = headlineColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = "${posture.protectionScore}/100",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = headlineColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DevicePostureCard(
    posture: DevicePosture,
    onReviewApps: () -> Unit,
    onExternalSettingsOpened: () -> Unit,
    onDismissCheck: ((PostureCheck) -> Unit)? = null,
    onRestoreCheck: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val headline = when {
        posture.needsAttention > 0 -> "${posture.needsAttention} protection issue(s) need attention"
        posture.needsReview > 0 -> "${posture.needsReview} protection setting(s) to review"
        else -> "Rootless checks found no immediate device-protection gaps"
    }
    val headlineColor = when {
        posture.needsAttention > 0 -> riskColor(RiskLevel.CRITICAL)
        posture.needsReview > 0 -> riskColor(RiskLevel.HIGH)
        else -> riskColor(RiskLevel.LOW)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = headlineColor,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Device protection checks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(headline, style = MaterialTheme.typography.bodySmall, color = headlineColor)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = headlineColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        "${posture.protectionScore}/100",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = headlineColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            posture.checks.forEachIndexed { idx, check ->
                val color = postureStatusColor(check.status)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = when (check.status) {
                            PostureStatus.PROTECTED -> Icons.Default.CheckCircle
                            PostureStatus.REVIEW -> Icons.Default.Info
                            PostureStatus.AT_RISK -> Icons.Default.WarningAmber
                            PostureStatus.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
                        },
                        contentDescription = check.status.label,
                        tint = color,
                        modifier = Modifier.padding(top = 2.dp).size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                check.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                check.status.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = color
                            )
                        }
                        Text(
                            check.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (check.details.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                check.details.forEach { line ->
                                    Text(
                                        text = "• $line",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        if (check.isDismissed) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Acknowledged",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (onRestoreCheck != null) {
                                    TextButton(onClick = { onRestoreCheck(check.id) }) {
                                        Text("Restore", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        } else if (check.status != PostureStatus.PROTECTED) {
                            Text(
                                check.recommendation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (check.action != null) {
                                    TextButton(
                                        onClick = {
                                            if (check.action == PostureAction.APP_REVIEW) {
                                                onReviewApps()
                                            } else {
                                                onExternalSettingsOpened()
                                                SettingsIntents.openPostureAction(context, check.action)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            when (check.action) {
                                                PostureAction.APP_REVIEW -> "Review apps"
                                                PostureAction.SYSTEM_UPDATE -> "System updates"
                                                PostureAction.DEVELOPER_SETTINGS -> "Developer options"
                                                PostureAction.CREDENTIAL_SETTINGS -> "Trusted credentials"
                                                PostureAction.NETWORK_SETTINGS -> "Network settings"
                                                PostureAction.PRIVATE_DNS_SETTINGS -> "Private DNS"
                                                PostureAction.VPN_SETTINGS -> "VPN settings"
                                                PostureAction.LOCK_SCREEN_SETTINGS -> "Lock screen"
                                                PostureAction.DISPLAY_SETTINGS -> "Display timeout"
                                                PostureAction.DEFAULT_APPS_SETTINGS -> "Default apps"
                                                PostureAction.INPUT_METHOD_SETTINGS -> "Keyboards"
                                                PostureAction.AUTOFILL_SETTINGS -> "Autofill"
                                                PostureAction.DEVICE_ADMIN_SETTINGS -> "Device admins"
                                                else -> "Open settings"
                                            }
                                        )
                                    }
                                }
                                if (onDismissCheck != null &&
                                    (check.status == PostureStatus.REVIEW || check.status == PostureStatus.AT_RISK)
                                ) {
                                    TextButton(onClick = { onDismissCheck(check) }) {
                                        Text("Acknowledge", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
                if (idx < posture.checks.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }

            Text(
                "Scope: Android public APIs only. Root, bootloader state, Play Protect, and OS compromise cannot be verified reliably without privileged access.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Actionable recommendations ("Fix Now")
// ---------------------------------------------------------------------------

@Composable
fun RecommendationsCard(
    recommendations: RecommendationSummary,
    onNavigateToAppDetail: (String) -> Unit,
    onExternalSettingsOpened: () -> Unit,
    onOpenApkScanner: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val actionable = recommendations.recommendations.filter { it.action != RecommendationAction.NONE }
    if (actionable.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.TaskAlt,
                    contentDescription = null,
                    tint = riskColor(RiskLevel.LOW),
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        "No urgent security actions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "No critical apps or high-priority posture gaps require immediate action.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val context = LocalContext.current
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll) actionable else actionable.take(4)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                    Text(
                        text = "Recommended actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${actionable.size} pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            visible.forEach { rec ->
                RecommendationRow(
                    rec = rec,
                    onClick = {
                        SettingsIntents.openRecommendation(
                            context = context,
                            rec = rec,
                            onNavigateToAppDetail = onNavigateToAppDetail,
                            onExternalLaunched = onExternalSettingsOpened,
                            onOpenApkScanner = onOpenApkScanner
                        )
                    }
                )
            }

            if (actionable.size > 4) {
                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (showAll) "Show fewer" else "Show all ${actionable.size} actions")
                }
            }
        }
    }
}

@Composable
private fun RecommendationRow(
    rec: SecurityRecommendation,
    onClick: () -> Unit
) {
    val level = when (rec.priority) {
        RecommendationPriority.CRITICAL -> RiskLevel.CRITICAL
        RecommendationPriority.HIGH -> RiskLevel.HIGH
        RecommendationPriority.MEDIUM -> RiskLevel.MEDIUM
        RecommendationPriority.INFO -> RiskLevel.LOW
    }
    val accent = riskColor(level)
    val bg = riskBgColor(level)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rec.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = rec.priority.label.uppercase(),
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = rec.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!rec.actionLabel.isNullOrBlank() && rec.action != RecommendationAction.NONE) {
                OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(rec.actionLabel, fontSize = 12.sp)
                }
            }
        }
    }
}

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
    plainLanguageMode: Boolean = true,
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
                    text = "Purpose check not run for this app (no sensitive access granted).",
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
                        if (!plainLanguageMode) {
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
                                            if (!plainLanguageMode) {
                                                Text(
                                                    text = fit.evidence,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        FitStatusChip(
                                            status = fit.status,
                                            similarity = fit.similarity,
                                            showNumeric = !plainLanguageMode
                                        )
                                    }
                                }
                        }
                    }

                    if (assessment.unexplainedCategories.isNotEmpty()) {
                        Surface(
                            color = riskBgColor(RiskLevel.MEDIUM),
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
    showNumeric: Boolean = true,
    modifier: Modifier = Modifier
) {
    val (background, foreground) = when (status) {
        FitStatus.FITS -> Pair(riskBgColor(RiskLevel.LOW), riskColor(RiskLevel.LOW))
        FitStatus.UNCLEAR -> Pair(riskBgColor(RiskLevel.MEDIUM), riskColor(RiskLevel.MEDIUM))
        FitStatus.UNEXPLAINED -> Pair(riskBgColor(RiskLevel.HIGH), riskColor(RiskLevel.HIGH))
        FitStatus.UNKNOWN -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(color = background, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = if (status == FitStatus.UNKNOWN || !showNumeric) status.label else "${status.label} ${formatSimilarity(similarity)}",
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
                        Icons.AutoMirrored.Filled.Rule,
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
                            color = riskColor(pointsColorLevel(line.points))
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
// Per-app runtime behaviour profile (when Usage Access is granted)
// ---------------------------------------------------------------------------

@Composable
fun BehaviourProfileCard(
    profile: BehaviourProfile,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                Text(
                    text = "Behavioural metrics (${profile.lookbackDays}d)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill("Foreground", profile.formattedForegroundTime(), Modifier.weight(1f))
                MetricPill("Bg Service", profile.formattedForegroundServiceTime(), Modifier.weight(1f))
                MetricPill("Total Data", profile.formattedTotalBytes(), Modifier.weight(1f))
            }
            if (profile.rxBytes > 0L || profile.txBytes > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill("Downloaded", profile.formattedRxBytes(), Modifier.weight(1f))
                    MetricPill("Uploaded", profile.formattedTxBytes(), Modifier.weight(1f))
                }
            }

            if (profile.anomalies.isNotEmpty()) {
                profile.anomalies.forEach { anomaly ->
                    Surface(
                        color = riskBgColor(RiskLevel.HIGH),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = anomaly.type,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = riskColor(RiskLevel.HIGH)
                            )
                            Text(
                                text = anomaly.detail,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = anomaly.recommendation,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Static APK & signing certificate checks
// ---------------------------------------------------------------------------

@Composable
fun StaticChecksCard(
    checks: StaticChecks?,
    loading: Boolean,
    plainLanguageMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showTechnical by remember(plainLanguageMode) { mutableStateOf(!plainLanguageMode) }

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
                    text = "Deep APK, trackers & signing",
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
                        text = "Inspecting APK package, trackers, hidden payloads & signing certificate…",
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
            if (checks.limitedAnalysisNote != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = checks.limitedAnalysisNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Plain-language highlights: hidden payloads, packers, trackers, sensitive code APIs, signing
            if (checks.hiddenPayloads.isNotEmpty()) {
                CheckRow(
                    label = "Hidden executable payloads",
                    value = checks.hiddenPayloads.joinToString(", ") { "${it.kind} (${it.entryPath})" },
                    warn = true
                )
            } else if (checks.limitedAnalysisNote == null) {
                CheckRow(
                    label = "Hidden payload check",
                    value = "No disguised APK, DEX, or ELF binaries found in assets/res",
                    warn = false
                )
            }

            if (checks.packerLibs.isNotEmpty()) {
                CheckRow(
                    label = "Packer / obfuscator detected",
                    value = checks.packerLibs.joinToString(", "),
                    warn = true
                )
            }

            if (checks.zipAnomalies.isNotEmpty()) {
                CheckRow(
                    label = "APK archive anomalies",
                    value = checks.zipAnomalies.joinToString("; "),
                    warn = true
                )
            }

            checks.dexAnalysis?.let { dex ->
                if (dex.financeTargetMatches.size >= 5) {
                    CheckRow(
                        label = "Banking / crypto target list in code",
                        value = "References ${dex.financeTargetMatches.size} banking/crypto apps (common overlay-trojan signal)",
                        warn = true
                    )
                }
                CheckRow(
                    label = "Embedded ad & analytics trackers",
                    value = if (dex.detectedTrackers.isEmpty()) {
                        "None matched in offline tracker list (${dex.dexFileCount} DEX scanned)"
                    } else {
                        val cats = dex.detectedTrackers.map { it.category }.distinct().joinToString(", ")
                        "${dex.detectedTrackers.size} SDK(s) found ($cats): " +
                            dex.detectedTrackers.take(5).joinToString(", ") { it.name } +
                            if (dex.detectedTrackers.size > 5) " +${dex.detectedTrackers.size - 5} more" else ""
                    },
                    warn = dex.detectedTrackers.any { it.category == "Location Broker" || it.category == "Session Replay" }
                )
                if (dex.highRiskApis.isNotEmpty()) {
                    CheckRow(
                        label = "Sensitive code capabilities",
                        value = dex.highRiskApis.take(4).joinToString(", ") { it.name },
                        warn = dex.highRiskApis.any {
                            it.id in setOf("dynamic_dex_load", "in_memory_dex_load", "device_wipe_lock", "sms_send")
                        }
                    )
                }
                if (dex.rawIpEndpoints.isNotEmpty()) {
                    CheckRow(
                        label = "Hardcoded public IP endpoints",
                        value = dex.rawIpEndpoints.joinToString(", "),
                        warn = true
                    )
                }
                if (dex.parseAnomaly) {
                    CheckRow(
                        label = "Code structure note",
                        value = "Partial DEX parse anomaly encountered (could indicate obfuscation or non-standard packaging)",
                        warn = true
                    )
                }
            }

            checks.signerInfo?.let { signer ->
                val primary = signer.primaryCert
                val signerWarn = signer.hasDebugCert || signer.hasWeakKey || signer.hasExpiredCert || signer.isV1Only
                val signerSummary = when {
                    primary == null -> "Signing certificate could not be read"
                    signer.hasDebugCert -> "WARNING: Signed with an Android debug certificate"
                    signer.isV1Only -> "Legacy v1-only JAR signature (no v2/v3 signing block)"
                    signer.hasExpiredCert -> "Signing certificate is expired"
                    signer.hasWeakKey -> "Weak signing key (${primary.keyAlgorithm} ${primary.keySize}-bit)"
                    else -> "Production signed (${signer.schemeSummary})"
                }
                CheckRow(
                    label = "Developer signature",
                    value = signerSummary,
                    warn = signerWarn
                )
            }

            TextButton(
                onClick = { showTechnical = !showTechnical },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (showTechnical) "Hide technical details" else "Show technical details")
            }

            if (showTechnical) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                checks.signerInfo?.primaryCert?.let { primary ->
                    CheckRow(
                        label = "Scheme & key",
                        value = "${checks.signerInfo.schemeSummary} · ${primary.keyAlgorithm} ${primary.keySize}-bit" +
                            if (checks.signerInfo.hasPastSigners) " · key rotation present" else "",
                        warn = checks.signerInfo.hasWeakKey || checks.signerInfo.hasDebugCert
                    )
                    CheckRow(
                        label = "Signer subject",
                        value = primary.subject,
                        warn = checks.signerInfo.hasDebugCert
                    )
                    CheckRow(
                        label = "Certificate validity",
                        value = "${formatDate(primary.validFrom)} → ${formatDate(primary.validUntil)}" +
                            if (primary.isExpired) " (EXPIRED)" else "",
                        warn = primary.isExpired
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cert SHA-256",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { copyToClipboard(context, "Signing Cert SHA-256", primary.sha256Fingerprint) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy cert SHA-256", modifier = Modifier.size(15.dp))
                        }
                    }
                    Text(
                        text = primary.sha256Fingerprint,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }

                CheckRow(
                    label = "Unprotected exported components",
                    value = if (checks.unprotectedExportedCount > 0) {
                        "${checks.unprotectedExportedCount} unprotected (${checks.exportedComponentCount} total exported)"
                    } else {
                        "0 unprotected (${checks.exportedComponentCount} exported)"
                    },
                    warn = checks.unprotectedExportedCount > 0
                )
                if (checks.unprotectedComponents.isNotEmpty()) {
                    Text(
                        text = checks.unprotectedComponents.take(5).joinToString(", ") { "${it.kind}:${it.shortName}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    label = "Debuggable / Test-only",
                    value = buildList {
                        add(if (checks.debuggable) "Debuggable" else "Release build")
                        if (checks.testOnly) add("Test-only")
                    }.joinToString(" · "),
                    warn = checks.debuggable || checks.testOnly
                )
                CheckRow(
                    label = "Backup allowance (allowBackup)",
                    value = if (checks.allowBackup) "Enabled (app data included in backups)" else "Disabled",
                    warn = false
                )
                CheckRow(
                    label = "SDK range",
                    value = "target ${checks.targetSdk} · min ${checks.minSdk}" +
                        if (checks.targetSdk in 1..28) " (older target SDK)" else "",
                    warn = checks.targetSdk in 1..28
                )
                if (checks.apkSizeBytes > 0L || checks.nativeLibraries.isNotEmpty()) {
                    val libsInfo = if (checks.nativeLibraries.isNotEmpty()) {
                        " · ${checks.nativeLibraries.size} native lib(s)"
                    } else ""
                    val splitInfo = if (checks.splitApkCount > 0) " · ${checks.splitApkCount} split(s)" else ""
                    CheckRow(
                        label = "APK footprint",
                        value = "${formatBytes(checks.apkSizeBytes)}$splitInfo$libsInfo",
                        warn = false
                    )
                }

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
            }

            Text(
                text = "All APK, DEX, tracker, and certificate checks run locally and never leave this device.",
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
            tint = if (warn) riskColor(RiskLevel.HIGH) else riskColor(RiskLevel.LOW),
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
    onViewTimeline: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (changes.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, riskColor(RiskLevel.HIGH).copy(alpha = 0.4f)),
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = riskColor(RiskLevel.HIGH),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Changed since last scan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (onViewTimeline != null) {
                    TextButton(onClick = onViewTimeline) {
                        Text("Timeline", style = MaterialTheme.typography.labelMedium)
                    }
                }
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
                            ChangeType.SIGNER_CHANGED, ChangeType.TRUST_BROKEN -> Icons.Default.Warning
                            ChangeType.SPECIAL_ACCESS_GRANTED -> Icons.Default.AdminPanelSettings
                            ChangeType.NEW_APP -> Icons.Default.NewReleases
                            ChangeType.PERMISSIONS_ADDED -> Icons.Default.AddAlert
                            ChangeType.PERMISSIONS_REMOVED, ChangeType.APP_REMOVED -> Icons.Default.RemoveCircleOutline
                            ChangeType.SCORE_INCREASED -> Icons.AutoMirrored.Filled.TrendingUp
                            ChangeType.POSTURE_REGRESSED -> Icons.Default.Security
                            ChangeType.UPDATED -> Icons.Default.SystemUpdate
                        },
                        contentDescription = null,
                        tint = when (change.type) {
                            ChangeType.SIGNER_CHANGED,
                            ChangeType.SPECIAL_ACCESS_GRANTED,
                            ChangeType.TRUST_BROKEN,
                            ChangeType.PERMISSIONS_ADDED,
                            ChangeType.SCORE_INCREASED -> riskColor(RiskLevel.CRITICAL)
                            ChangeType.NEW_APP,
                            ChangeType.POSTURE_REGRESSED -> riskColor(RiskLevel.HIGH)
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
                        change.detail?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (change.addedSpecialAccess.isNotEmpty()) {
                            Text(
                                text = "Special access: ${change.addedSpecialAccess.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = riskColor(RiskLevel.CRITICAL)
                            )
                        }
                        if (change.addedPermissions.isNotEmpty()) {
                            Text(
                                text = "Added: ${change.addedPermissions.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = riskColor(RiskLevel.CRITICAL)
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
                            color = riskColor(riskLevelForScore(change.newScore))
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
    posture: DevicePosture?,
    recommendations: RecommendationSummary?,
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
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scoring rules")
                }
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        status = null
                        scope.launch {
                            val file = PdfExporter.export(context, summary, apps, posture, recommendations)
                            busy = false
                            if (file != null) {
                                status = "Report saved locally and ready to share."
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
