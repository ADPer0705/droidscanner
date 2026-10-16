package com.idiots3.riskscanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idiots3.riskscanner.engine.ScoringConfig
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.ui.theme.RiskColors

/**
 * The one-page scoring rubric, rendered directly from ScoringConfig so the in-app
 * explanation can never drift away from the engine that actually scores apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringRubricScreen(onNavigateBack: () -> Unit) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("How scoring works", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RubricSection("Scoring engine v${ScoringConfig.VERSION}") {
                    Text(
                        text = "Every app gets a score from 0 to 100 computed entirely on this device. " +
                            "The deterministic rule engine is authoritative; the on-device AI purpose check " +
                            "only ever adds a small, clearly labelled signal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                RubricSection("Risk bands") {
                    RiskLevel.entries.forEach { level ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = getRiskBackgroundColor(level),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = level.label.uppercase(),
                                        color = getRiskColor(level),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(levelMeaning(level), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = "${level.minScore}–${level.maxScore}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                RubricSection("Category weights (maximum points per category)") {
                    PermissionCategory.entries.forEach { category ->
                        val weight = ScoringConfig.categoryWeight(category)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(category.label, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = if (weight > 0) "$weight pts" else "own table",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (weight > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Divider(Modifier.padding(vertical = 6.dp))
                    Text(
                        text = "A category reaches its full weight at " +
                            "%.1f".format(ScoringConfig.CATEGORY_SATURATION_UNITS) + " risk units " +
                            "(a granted sensitive permission counts as " +
                            "${ScoringConfig.UNIT_SENSITIVE_GRANTED}, declared-only as " +
                            "${ScoringConfig.UNIT_SENSITIVE_DECLARED_ONLY}); normal permissions count far less.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                RubricSection("Special access (highest weight, granted via separate settings screens)") {
                    ScoreRow("Accessibility service — enabled", ScoringConfig.Special.ACCESSIBILITY_ENABLED)
                    ScoreRow("Accessibility service — declared only", ScoringConfig.Special.ACCESSIBILITY_DECLARED)
                    ScoreRow("Device administrator — active", ScoringConfig.Special.DEVICE_ADMIN_ACTIVE)
                    ScoreRow("Notification listener — enabled", ScoringConfig.Special.NOTIFICATION_LISTENER_ENABLED)
                    ScoreRow("Screen overlay — enabled", ScoringConfig.Special.OVERLAY_ENABLED)
                    ScoreRow("Screen overlay — declared only", ScoringConfig.Special.OVERLAY_DECLARED)
                    ScoreRow("Install other apps — granted", ScoringConfig.Special.INSTALL_PACKAGES_ENABLED)
                    ScoreRow("Install other apps — declared only", ScoringConfig.Special.INSTALL_PACKAGES_DECLARED)
                    ScoreRow("Usage statistics — granted", ScoringConfig.Special.USAGE_STATS_ENABLED)
                    ScoreRow("Usage statistics — declared only", ScoringConfig.Special.USAGE_STATS_DECLARED)
                }
            }

            item {
                RubricSection("Combination rules (added on top of categories)") {
                    ScoringConfig.comboRules.forEach { rule ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = rule.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "+${rule.points}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RiskColors.High
                                )
                            }
                            Text(
                                text = rule.condition,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = rule.rationale,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Divider(Modifier.padding(vertical = 6.dp))
                    ScoreRow("Sideloaded install + sensitive permissions", ScoringConfig.SIDELOAD_PENALTY)
                }
            }

            item {
                RubricSection("On-device AI purpose check (bounded)") {
                    Text(
                        text = "A bundled Universal Sentence Encoder (6 MB, runs offline) compares the app's own " +
                            "metadata against a curated list of app functions and permission-use descriptions. " +
                            "Unexplained access adds at most ${ScoringConfig.AI_MAX_DELTA} points, only for apps already " +
                            "scoring ${ScoringConfig.AI_MIN_RULE_SCORE}+ on the rules, and it can never make an app Critical by itself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Divider(Modifier.padding(vertical = 6.dp))
                    Text(
                        text = "The similarity value shown in the app (for example 0.42) is a text-overlap score, " +
                            "not a probability. \"Unexplained\" means the app's purpose does not explain the access — " +
                            "it is not proof of malicious behaviour.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            item {
                RubricSection("Privacy") {
                    Text(
                        text = "This app declares no internet permission. The inventory, the scoring, the ML model, " +
                            "the SHA-256 hashes and the PDF report all run completely offline. " +
                            "Nothing is uploaded, ever.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Text(
                    text = "Explainability requirement: every High or Critical app shows at least " +
                        "${ScoringConfig.MIN_REASONS_FOR_HIGH_RISK} plain-language reasons and a full point-by-point breakdown.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun RubricSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ScoreRow(label: String, points: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = "+$points",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun levelMeaning(level: RiskLevel): String = when (level) {
    RiskLevel.LOW -> "Standard access, no action needed"
    RiskLevel.MEDIUM -> "Review granted permissions when convenient"
    RiskLevel.HIGH -> "Audit special access and revoke unused grants"
    RiskLevel.CRITICAL -> "Recommend uninstalling after review"
}
