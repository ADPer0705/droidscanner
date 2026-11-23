package com.idiots3.riskscanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idiots3.riskscanner.model.AiAssessment
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PermissionDetail
import com.idiots3.riskscanner.model.PermissionFit
import com.idiots3.riskscanner.model.ProtectionLevel
import com.idiots3.riskscanner.model.ReasonKind
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.RiskReason
import com.idiots3.riskscanner.model.SpecialAccessGrantState
import com.idiots3.riskscanner.model.SpecialAccessInfo
import com.idiots3.riskscanner.ui.components.AppIconImage
import com.idiots3.riskscanner.ui.components.BehaviourProfileCard
import com.idiots3.riskscanner.ui.components.FitStatusChip
import com.idiots3.riskscanner.ui.components.InstallerTag
import com.idiots3.riskscanner.ui.components.PurposeFitCard
import com.idiots3.riskscanner.ui.components.ScoreBreakdownCard
import com.idiots3.riskscanner.ui.components.SettingsIntents
import com.idiots3.riskscanner.ui.components.StaticChecksCard
import com.idiots3.riskscanner.ui.components.formatDate
import com.idiots3.riskscanner.ui.components.getCategoryIcon
import com.idiots3.riskscanner.ui.components.riskBgColor
import com.idiots3.riskscanner.ui.components.riskColor
import com.idiots3.riskscanner.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val app = remember(uiState.apps, packageName) {
        uiState.apps.find { it.packageName == packageName }
    }
    LaunchedEffect(packageName) {
        viewModel.loadStaticChecks(packageName)
    }
    val staticChecks = uiState.staticChecks[packageName]
    val staticChecksLoading = uiState.staticChecksLoading.contains(packageName)
    val behaviourProfile = uiState.behaviourResult.profiles[packageName]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = app?.appName ?: "App Details",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (app != null) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    ActionButtons(
                        app = app,
                        onExternalLaunched = { viewModel.markExternalSettingsOpened() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading && app == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (app == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Not found",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Application Not Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Package: $packageName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { AppHeader(app = app) }

                if (!app.isSelfApp) {
                    item {
                        TrustControlCard(
                            app = app,
                            onTrust = { viewModel.trustApp(app) },
                            onUntrust = { viewModel.untrustApp(app.packageName) }
                        )
                    }
                }

                item { RiskScoreSection(app = app) }

                item { ScoreBreakdownCard(app = app) }

                item {
                    PurposeFitCard(
                        assessment = app.aiAssessment,
                        aiActive = uiState.aiActive,
                        plainLanguageMode = uiState.plainLanguageMode
                    )
                }

                val comboReasons = app.riskReasons.filter { it.kind == ReasonKind.COMBO_RULE || it.kind == ReasonKind.IMPERSONATION }
                if (comboReasons.isNotEmpty()) {
                    item { CombinationAlertsCard(reasons = comboReasons) }
                }

                item { WhyFlaggedPanel(reasons = app.riskReasons) }

                item {
                    SpecialAccessSection(
                        packageName = app.packageName,
                        specialAccess = app.specialAccess,
                        onExternalLaunched = { viewModel.markExternalSettingsOpened() }
                    )
                }

                if (behaviourProfile != null) {
                    item { BehaviourProfileCard(profile = behaviourProfile) }
                }

                item {
                    PermissionCategoriesSection(
                        categories = app.categories,
                        assessment = app.aiAssessment
                    )
                }

                item {
                    StaticChecksCard(
                        checks = staticChecks,
                        loading = staticChecksLoading,
                        plainLanguageMode = uiState.plainLanguageMode
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun AppHeader(
    app: AppInfo,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(
                    packageName = app.packageName,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "v${app.versionName} (${app.versionCode})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetadataRow(Icons.Default.DateRange, "Installed", formatDate(app.installDate))
                MetadataRow(Icons.Default.Refresh, "Last Updated", formatDate(app.updateDate))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Installer Source",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    InstallerTag(source = app.installerSource, provenance = app.installProvenance)
                }

                if (!app.initiatingPackage.isNullOrBlank() && app.isSideloaded) {
                    MetadataRow(Icons.Default.Info, "Initiated by", app.initiatingPackage)
                }

                MetadataRow(Icons.Default.Build, "Target SDK", "${app.targetSdk} (Min: ${app.minSdk})")

                if (!app.hasLauncherActivity) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = riskColor(RiskLevel.HIGH),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Launcher icon",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (app.isEnabledInputMethod) "No launcher (Keyboard)" else "No launcher icon",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (app.isEnabledInputMethod) MaterialTheme.colorScheme.onSurfaceVariant else riskColor(RiskLevel.HIGH)
                        )
                    }
                }

                val badges = buildList {
                    if (app.isSelfApp) add("This app")
                    if (app.isUserTrusted) add("Reviewed & Trusted")
                    if (app.verifiedKnownSafeName != null) add("Recognized Tool")
                    if (app.isSystemApp) add(if (app.isUpdatedSystemApp) "System (Updated)" else "System App")
                    if (app.isDefaultSmsApp) add("Default SMS")
                    if (app.isDefaultDialer) add("Default Phone")
                    if (app.isDefaultBrowser) add("Default Browser")
                    if (app.isDefaultLauncher) add("Default Home")
                    if (app.isAutofillService) add("Autofill")
                    if (app.isEnabledInputMethod) add("Keyboard")
                    if (app.isDebugSigned) add("Debug Cert")
                    if (app.isDebuggable) add("Debuggable")
                }
                if (badges.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        badges.forEach { badge ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
private fun MetadataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RiskScoreSection(
    app: AppInfo,
    modifier: Modifier = Modifier
) {
    val color = riskColor(app.riskLevel)
    val bg = riskBgColor(app.riskLevel)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Security Risk Score",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                CircularProgressIndicator(
                    progress = { (app.riskScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = color.copy(alpha = 0.15f),
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${app.riskScore}",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                    Text(
                        text = "/ 100",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = "${app.riskLevel.label.uppercase()} RISK",
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            val riskDescription = when (app.riskLevel) {
                RiskLevel.CRITICAL -> "Critical risk level. This app exhibits permission patterns or special device access commonly linked with high-risk malware, spyware, or banking trojans."
                RiskLevel.HIGH -> "High risk level. This app requests sensitive permissions or combinations that pose notable security and data privacy threats."
                RiskLevel.MEDIUM -> "Moderate risk level. This app accesses sensitive device resources. Confirm that these permissions align with its expected capabilities."
                RiskLevel.LOW -> "Low risk level. Permissions requested are standard and within safe device baselines with no dangerous combinations."
            }

            Text(
                text = riskDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun CombinationAlertsCard(
    reasons: List<RiskReason>,
    modifier: Modifier = Modifier
) {
    val criticalColor = riskColor(RiskLevel.CRITICAL)
    val criticalBg = riskBgColor(RiskLevel.CRITICAL)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = criticalBg),
        border = BorderStroke(1.5.dp, criticalColor),
        modifier = modifier.fillMaxWidth()
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
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Threat Pattern Alert",
                    tint = criticalColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "High-Risk Capability Combinations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = criticalColor
                )
            }

            Text(
                text = "Multiple permissions or privileges combined match known abuse patterns:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            reasons.forEach { reason ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = criticalColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = reason.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = criticalColor
                            )
                        }
                        Text(
                            text = reason.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhyFlaggedPanel(
    reasons: List<RiskReason>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Why Flagged",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${reasons.size} reasons",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (reasons.isEmpty()) {
                Text(
                    text = "No risk reasons flagged for this app. It operates within expected security baselines.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    reasons.forEachIndexed { index, reason ->
                        ReasonItem(reason = reason)
                        if (index < reasons.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReasonItem(
    reason: RiskReason,
    modifier: Modifier = Modifier
) {
    val severityColor = riskColor(reason.severity)
    val severityBg = riskBgColor(reason.severity)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(severityBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getCategoryIcon(reason.category),
                contentDescription = reason.category.label,
                tint = severityColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = reason.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(color = severityBg, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = reason.severity.label.uppercase(),
                        color = severityColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = reason.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Category: ${reason.category.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun SpecialAccessSection(
    packageName: String,
    specialAccess: SpecialAccessInfo,
    onExternalLaunched: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val criticalColor = riskColor(RiskLevel.CRITICAL)
    val criticalBg = riskBgColor(RiskLevel.CRITICAL)
    val lowColor = riskColor(RiskLevel.LOW)
    val lowBg = riskBgColor(RiskLevel.LOW)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (specialAccess.hasAny) criticalColor else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Special Device Access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (specialAccess.enabledLabels.isNotEmpty()) {
                    Surface(color = criticalBg, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = "${specialAccess.enabledLabels.size} active",
                            style = MaterialTheme.typography.labelSmall,
                            color = criticalColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!specialAccess.hasAny) {
                Surface(
                    color = lowBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = lowColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "No high-privilege special device access active or declared",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Text(
                    text = "Special privileges are granted through separate system settings screens:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (specialAccess.accessibilityEnabled) {
                        SpecialAccessItem(
                            title = "Accessibility Service — ENABLED",
                            description = "Can read everything on screen, capture passwords and simulate touches. Verified in Settings > Accessibility.",
                            severity = RiskLevel.CRITICAL,
                            actionLabel = "Accessibility Settings",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openAccessibilitySettings(context)
                            }
                        )
                    } else if (specialAccess.accessibilityDeclared) {
                        SpecialAccessItem(
                            title = "Accessibility Service — declared, not enabled",
                            description = "Ships an accessibility service that is currently switched off. Only enable it if you trust the app.",
                            severity = RiskLevel.MEDIUM,
                            actionLabel = "Accessibility Settings",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openAccessibilitySettings(context)
                            }
                        )
                    }

                    if (specialAccess.deviceAdminActive) {
                        SpecialAccessItem(
                            title = "Device Administrator — ACTIVE",
                            description = "Can enforce screen locks, wipe internal storage and block its own uninstallation.",
                            severity = RiskLevel.CRITICAL,
                            actionLabel = "Device Admin Settings",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openDeviceAdminSettings(context)
                            }
                        )
                    } else if (specialAccess.deviceAdminDeclared) {
                        SpecialAccessItem(
                            title = "Device Administrator — declared, not active",
                            description = "Includes a device-admin receiver (currently inactive).",
                            severity = RiskLevel.LOW,
                            actionLabel = "Device Admin Settings",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openDeviceAdminSettings(context)
                            }
                        )
                    }

                    if (specialAccess.notificationListenerEnabled) {
                        SpecialAccessItem(
                            title = "Notification Listener — ENABLED",
                            description = "Can intercept all incoming notifications, including SMS OTPs and private messages.",
                            severity = RiskLevel.CRITICAL,
                            actionLabel = "Notification Access",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openNotificationListenerSettings(context)
                            }
                        )
                    } else if (specialAccess.notificationListenerDeclared) {
                        SpecialAccessItem(
                            title = "Notification Listener — declared, not enabled",
                            description = "Requests notification access but does not currently hold it.",
                            severity = RiskLevel.MEDIUM,
                            actionLabel = "Notification Access",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openNotificationListenerSettings(context)
                            }
                        )
                    }

                    when (specialAccess.overlayState) {
                        SpecialAccessGrantState.ENABLED -> SpecialAccessItem(
                            title = "Screen Overlay — ENABLED",
                            description = "Can display floating windows over other apps, the classic phishing technique.",
                            severity = RiskLevel.HIGH,
                            actionLabel = "Overlay Settings",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openOverlaySettings(context, packageName)
                            }
                        )
                        SpecialAccessGrantState.DECLARED_UNVERIFIED -> SpecialAccessItem(
                            title = "Screen Overlay — declared (verify in Settings)",
                            description = "Declares SYSTEM_ALERT_WINDOW. Android restricts reading another app's overlay toggle directly — verify in Settings.",
                            severity = RiskLevel.MEDIUM,
                            actionLabel = "Verify in Settings",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openOverlaySettings(context, packageName)
                            }
                        )
                        SpecialAccessGrantState.DECLARED_DENIED -> SpecialAccessItem(
                            title = "Screen Overlay — declared, not granted",
                            description = "Requests \"display over other apps\" but does not hold it.",
                            severity = RiskLevel.LOW
                        )
                        SpecialAccessGrantState.NOT_DECLARED -> Unit
                    }

                    when (specialAccess.installPackagesState) {
                        SpecialAccessGrantState.ENABLED -> SpecialAccessItem(
                            title = "Install Unknown Apps — GRANTED",
                            description = "Can trigger APK installations without going through an app store.",
                            severity = RiskLevel.HIGH,
                            actionLabel = "Install Apps Settings",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openInstallUnknownAppsSettings(context, packageName)
                            }
                        )
                        SpecialAccessGrantState.DECLARED_UNVERIFIED -> SpecialAccessItem(
                            title = "Install Unknown Apps — declared (verify in Settings)",
                            description = "Declares REQUEST_INSTALL_PACKAGES. Verify \"Install unknown apps\" is set to Not allowed in Settings.",
                            severity = RiskLevel.MEDIUM,
                            actionLabel = "Verify in Settings",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openInstallUnknownAppsSettings(context, packageName)
                            }
                        )
                        SpecialAccessGrantState.DECLARED_DENIED -> SpecialAccessItem(
                            title = "Install Unknown Apps — declared, not granted",
                            description = "Requests APK installation permission but does not currently hold it.",
                            severity = RiskLevel.LOW
                        )
                        SpecialAccessGrantState.NOT_DECLARED -> Unit
                    }

                    when (specialAccess.usageStatsState) {
                        SpecialAccessGrantState.ENABLED -> SpecialAccessItem(
                            title = "Usage Statistics — GRANTED",
                            description = "Can see which apps you open and when.",
                            severity = RiskLevel.MEDIUM,
                            actionLabel = "Usage Access",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openUsageAccessSettings(context)
                            }
                        )
                        SpecialAccessGrantState.DECLARED_UNVERIFIED -> SpecialAccessItem(
                            title = "Usage Statistics — declared (verify in Settings)",
                            description = "Declares PACKAGE_USAGE_STATS. Verify in Usage Access Settings.",
                            severity = RiskLevel.MEDIUM,
                            actionLabel = "Usage Access",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openUsageAccessSettings(context)
                            }
                        )
                        SpecialAccessGrantState.DECLARED_DENIED -> SpecialAccessItem(
                            title = "Usage Statistics — declared, not granted",
                            description = "Requests app-usage history access but does not hold it.",
                            severity = RiskLevel.LOW
                        )
                        SpecialAccessGrantState.NOT_DECLARED -> Unit
                    }

                    if (specialAccess.allFilesAccessDeclared) {
                        SpecialAccessItem(
                            title = "All-Files Access — declared",
                            description = "Declares MANAGE_EXTERNAL_STORAGE (broad shared-storage read/write). Verify in All Files Access settings.",
                            severity = RiskLevel.MEDIUM,
                            actionLabel = "All Files Access",
                            onAction = {
                                onExternalLaunched()
                                SettingsIntents.openAllFilesAccessSettings(context, packageName)
                            }
                        )
                    }

                    if (specialAccess.vpnServiceDeclared) {
                        SpecialAccessItem(
                            title = "VPN Service — declared",
                            description = "Ships a VpnService capable of routing network traffic when active.",
                            severity = RiskLevel.LOW
                        )
                    }

                    if (specialAccess.nfcHceDeclared) {
                        SpecialAccessItem(
                            title = "NFC Card Emulation — declared",
                            description = "Ships a host-based NFC card-emulation service.",
                            severity = RiskLevel.LOW
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecialAccessItem(
    title: String,
    description: String,
    severity: RiskLevel,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val color = riskColor(severity)
    val bg = riskBgColor(severity)

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(color = color, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = severity.label.uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCategoriesSection(
    categories: Map<PermissionCategory, List<PermissionDetail>>,
    assessment: AiAssessment? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Permission Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Grouped by risk impact across Android permission domains:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PermissionCategory.entries.forEach { category ->
            val permissions = categories[category] ?: emptyList()
            CategoryCard(
                category = category,
                permissions = permissions,
                fit = assessment?.permissionFit?.get(category)
            )
        }
    }
}

@Composable
fun CategoryCard(
    category: PermissionCategory,
    permissions: List<PermissionDetail>,
    fit: PermissionFit? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val hasDangerous = permissions.any {
        it.protectionLevel == ProtectionLevel.DANGEROUS || it.protectionLevel == ProtectionLevel.SPECIAL
    }
    val grantedCount = permissions.count { it.isGranted }
    val highlightColor = riskColor(RiskLevel.HIGH)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (hasDangerous && permissions.isNotEmpty()) {
            BorderStroke(1.dp, highlightColor.copy(alpha = 0.5f))
        } else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (permissions.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(category),
                        contentDescription = category.label,
                        tint = if (permissions.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val countSubtitle = if (permissions.isEmpty()) {
                        "0 permissions"
                    } else {
                        "${permissions.size} permissions ($grantedCount granted)"
                    }
                    Text(
                        text = countSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasDangerous) highlightColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(if (expanded) 180f else 0f)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (permissions.isEmpty()) {
                        Text(
                            text = "No permissions requested in this category.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        permissions.forEach { perm ->
                            PermissionRow(permission = perm, fit = fit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    permission: PermissionDetail,
    fit: PermissionFit? = null,
    modifier: Modifier = Modifier
) {
    val grantedColor = riskColor(RiskLevel.LOW)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (permission.isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = grantedColor,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = 2.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Declared only (Not granted)",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = 2.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = permission.shortName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    ProtectionLevelBadge(level = permission.protectionLevel)
                }

                Text(
                    text = permission.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = if (permission.isGranted) "Status: Granted" else "Status: Declared (Not Granted)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (permission.isGranted) grantedColor else MaterialTheme.colorScheme.outline
                )

                if (fit != null && permission.isSensitive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FitStatusChip(status = fit.status, similarity = fit.similarity)
                }
            }
        }
    }
}

@Composable
fun ProtectionLevelBadge(
    level: ProtectionLevel,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (level) {
        ProtectionLevel.DANGEROUS -> Pair(riskBgColor(RiskLevel.CRITICAL), riskColor(RiskLevel.CRITICAL))
        ProtectionLevel.SPECIAL -> Pair(Color(0x339C27B0), Color(0xFFAB47BC))
        ProtectionLevel.SIGNATURE -> Pair(Color(0x332196F3), Color(0xFF42A5F5))
        ProtectionLevel.NORMAL -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(color = bgColor, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Text(
            text = level.label.uppercase(),
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TrustControlCard(
    app: AppInfo,
    onTrust: () -> Unit,
    onUntrust: () -> Unit
) {
    val lowColor = riskColor(RiskLevel.LOW)
    val lowBg = riskBgColor(RiskLevel.LOW)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isUserTrusted) lowBg else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (app.isUserTrusted) lowColor.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = if (app.isUserTrusted) lowColor else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (app.isUserTrusted) "Reviewed & Trusted" else "Recognize and trust this app?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (app.isUserTrusted) lowColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (app.isUserTrusted) {
                        "Silenced from urgent alerts. Automatically re-alerts if permissions, special access, or the developer certificate changes."
                    } else {
                        "Marks this app as reviewed so it stops triggering alerts unless its privileges or signer change."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (app.isUserTrusted) {
                TextButton(onClick = onUntrust) {
                    Text("Revoke")
                }
            } else {
                OutlinedButton(
                    onClick = onTrust,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Trust", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ActionButtons(
    app: AppInfo,
    onExternalLaunched: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeviceAdminDialog by remember { mutableStateOf(false) }

    if (showDeviceAdminDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceAdminDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = riskColor(RiskLevel.CRITICAL)) },
            title = { Text("Device Admin blocks uninstall") },
            text = {
                Text(
                    "${app.appName} is currently an active Device Administrator. Android will block uninstalling it until you deactivate it first.\n\n" +
                        "Step 1: Open Device Admin settings and switch off ${app.appName}.\n" +
                        "Step 2: Come back here and tap Uninstall."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeviceAdminDialog = false
                        onExternalLaunched()
                        SettingsIntents.openDeviceAdminSettings(context)
                    }
                ) {
                    Text("Step 1: Device Admin Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeviceAdminDialog = false
                        onExternalLaunched()
                        SettingsIntents.requestUninstall(context, app.packageName)
                    }
                ) {
                    Text("Try Uninstall Anyway")
                }
            }
        )
    }

    if (app.isSelfApp) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = riskColor(RiskLevel.LOW),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "DroidScanner — 100% offline security guardian",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = {
                onExternalLaunched()
                SettingsIntents.openAppDetails(context, app.packageName)
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("App Settings")
        }

        if (app.isSystemApp) {
            Button(
                onClick = {
                    onExternalLaunched()
                    SettingsIntents.openAppDetails(context, app.packageName)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disable / Info")
            }
        } else {
            Button(
                onClick = {
                    if (app.specialAccess.deviceAdminActive) {
                        showDeviceAdminDialog = true
                    } else {
                        onExternalLaunched()
                        SettingsIntents.requestUninstall(context, app.packageName)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Uninstall")
            }
        }
    }
}
