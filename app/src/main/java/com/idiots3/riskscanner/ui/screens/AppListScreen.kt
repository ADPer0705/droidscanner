package com.idiots3.riskscanner.ui.screens

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.idiots3.riskscanner.model.*
import com.idiots3.riskscanner.ui.theme.RiskColors
import com.idiots3.riskscanner.viewmodel.MainViewModel
import com.idiots3.riskscanner.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: MainViewModel,
    onNavigateToAppDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "All Apps",
                        fontWeight = FontWeight.Bold
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
                actions = {
                    IconButton(onClick = { viewModel.scanApps() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan apps"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Risk Level Filter Chips
            FilterChipsRow(
                selectedFilter = uiState.selectedFilter,
                onFilterChange = { viewModel.filterByRisk(it) },
                modifier = Modifier.fillMaxWidth()
            )

            // Category Filter Dropdown + Reset Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryFilterDropdown(
                    selectedCategory = uiState.selectedCategory,
                    onCategoryChange = { viewModel.filterByCategory(it) },
                    modifier = Modifier.weight(1f)
                )

                if (uiState.selectedCategory != null || uiState.selectedFilter != null || uiState.searchQuery.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = {
                            viewModel.updateSearchQuery("")
                            viewModel.filterByRisk(null)
                            viewModel.filterByCategory(null)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear all filters",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Results count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val countText = if (uiState.filteredApps.size == uiState.apps.size) {
                    "${uiState.apps.size} apps found"
                } else {
                    "Showing ${uiState.filteredApps.size} of ${uiState.apps.size} apps"
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                if (uiState.selectedFilter != null) {
                    Text(
                        text = "Severity: ${uiState.selectedFilter?.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = getRiskColor(uiState.selectedFilter!!),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // App List / State View
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Scanning installed applications...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (uiState.filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "No applications match your filter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Try adjusting your search query, risk severity, or permission category filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                viewModel.updateSearchQuery("")
                                viewModel.filterByRisk(null)
                                viewModel.filterByCategory(null)
                            }
                        ) {
                            Text("Reset Filters")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.filteredApps,
                        key = { it.packageName }
                    ) { appInfo ->
                        AppCard(
                            appInfo = appInfo,
                            onClick = { onNavigateToAppDetail(appInfo.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "Search by app or package name...",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun FilterChipsRow(
    selectedFilter: RiskLevel?,
    onFilterChange: (RiskLevel?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterChange(null) },
            label = {
                Text(
                    text = "All",
                    fontWeight = if (selectedFilter == null) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        RiskLevel.entries.forEach { level ->
            val isSelected = selectedFilter == level
            val levelColor = getRiskColor(level)
            val levelBg = getRiskBackgroundColor(level)

            FilterChip(
                selected = isSelected,
                onClick = { onFilterChange(if (isSelected) null else level) },
                label = {
                    Text(
                        text = level.label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(levelColor)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = levelBg,
                    selectedLabelColor = levelColor,
                    selectedLeadingIconColor = levelColor
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (isSelected) levelColor else MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = levelColor
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterDropdown(
    selectedCategory: PermissionCategory?,
    onCategoryChange: (PermissionCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCategory?.label ?: "All Categories",
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            leadingIcon = {
                Icon(
                    imageVector = if (selectedCategory != null) getCategoryIcon(selectedCategory) else Icons.Default.Info,
                    contentDescription = "Category filter",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "All Categories",
                        fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onCategoryChange(null)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            HorizontalDivider()
            PermissionCategory.entries.forEach { category ->
                val isSelected = selectedCategory == category
                DropdownMenuItem(
                    text = {
                        Text(
                            text = category.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onCategoryChange(category)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = getCategoryIcon(category),
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun AppCard(
    appInfo: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                packageName = appInfo.packageName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "v${appInfo.versionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    InstallerTag(source = appInfo.installerSource)
                    if (appInfo.isSystemApp) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "System",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                appInfo.aiAssessment?.purposeLabel?.let { purpose ->
                    Text(
                        text = "Looks like: $purpose",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!appInfo.aiAssessment?.unexplainedCategories.isNullOrEmpty()) {
                    Surface(
                        color = RiskColors.HighBackground,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "AI: unexplained access",
                            style = MaterialTheme.typography.labelSmall,
                            color = RiskColors.High,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmallRiskCircle(
                    score = appInfo.riskScore,
                    riskLevel = appInfo.riskLevel
                )
                RiskBadge(riskLevel = appInfo.riskLevel)
            }
        }
    }
}

@Composable
fun SmallRiskCircle(
    score: Int,
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    val color = getRiskColor(riskLevel)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(42.dp)
    ) {
        CircularProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            strokeWidth = 3.5.dp
        )
        Text(
            text = "$score",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun InstallerTag(
    source: String,
    modifier: Modifier = Modifier
) {
    val isPlayStore = source.contains("Play Store", ignoreCase = true)
    val isSideloaded = source.equals("Sideloaded", ignoreCase = true) || source.equals("Unknown", ignoreCase = true)

    val (bgColor, textColor, label) = when {
        isPlayStore -> Triple(
            RiskColors.LowBackground,
            RiskColors.Low,
            "Play Store"
        )
        isSideloaded -> Triple(
            RiskColors.CriticalBackground,
            RiskColors.Critical,
            "Sideloaded"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            source
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RiskBadge(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    Surface(
        color = getRiskBackgroundColor(riskLevel),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = riskLevel.label.uppercase(),
            color = getRiskColor(riskLevel),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

/**
 * Icons are loaded lazily and kept in a small LRU cache (64 entries) instead of being
 * stored on every AppInfo — this keeps memory flat while scrolling hundreds of apps.
 */
object AppIconCache {
    private val cache = LruCache<String, Drawable>(64)

    fun get(context: Context, packageName: String): Drawable? {
        cache.get(packageName)?.let { return it }
        return try {
            context.packageManager.getApplicationIcon(packageName).also { cache.put(packageName, it) }
        } catch (t: Throwable) {
            null
        }
    }
}

@Composable
fun AppIconImage(packageName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(initialValue = null, key1 = packageName) {
        value = packageName?.let { withContext(Dispatchers.IO) { AppIconCache.get(context, it) } }
    }
    val current = drawable
    if (current != null) {
        val bitmap = remember(current) { current.toBitmap(96, 96).asImageBitmap() }
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = "App icon",
            modifier = modifier
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Android,
                contentDescription = "App icon",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun getRiskColor(level: RiskLevel): Color = when (level) {
    RiskLevel.CRITICAL -> RiskColors.Critical
    RiskLevel.HIGH -> RiskColors.High
    RiskLevel.MEDIUM -> RiskColors.Medium
    RiskLevel.LOW -> RiskColors.Low
}

fun getRiskBackgroundColor(level: RiskLevel): Color = when (level) {
    RiskLevel.CRITICAL -> RiskColors.CriticalBackground
    RiskLevel.HIGH -> RiskColors.HighBackground
    RiskLevel.MEDIUM -> RiskColors.MediumBackground
    RiskLevel.LOW -> RiskColors.LowBackground
}

fun getCategoryIcon(category: PermissionCategory): ImageVector = when (category) {
    PermissionCategory.SPECIAL_ACCESS -> Icons.Filled.Security
    PermissionCategory.SMS -> Icons.Filled.Sms
    PermissionCategory.CONTACTS_CALL_LOGS -> Icons.Filled.Contacts
    PermissionCategory.CAMERA_MICROPHONE -> Icons.Filled.Videocam
    PermissionCategory.LOCATION -> Icons.Filled.LocationOn
    PermissionCategory.PHONE_IDENTITY -> Icons.Filled.PhoneAndroid
    PermissionCategory.STORAGE_MEDIA -> Icons.Filled.Folder
    PermissionCategory.BACKGROUND_PERSISTENCE -> Icons.Filled.Sync
    PermissionCategory.NETWORK -> Icons.Filled.Wifi
}

