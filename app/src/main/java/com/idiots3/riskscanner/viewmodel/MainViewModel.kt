package com.idiots3.riskscanner.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.idiots3.riskscanner.data.SnapshotStore
import com.idiots3.riskscanner.engine.PermissionDiff
import com.idiots3.riskscanner.engine.RecommendationEngine
import com.idiots3.riskscanner.engine.RiskScorer
import com.idiots3.riskscanner.ml.EmbeddingStore
import com.idiots3.riskscanner.ml.PurposeInferenceEngine
import com.idiots3.riskscanner.ml.TextEmbedderEngine
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.BehaviourScanResult
import com.idiots3.riskscanner.model.ChangeType
import com.idiots3.riskscanner.model.DevicePosture
import com.idiots3.riskscanner.model.DeviceSummary
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PermissionChange
import com.idiots3.riskscanner.model.RecommendationSummary
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.StaticChecks
import com.idiots3.riskscanner.receiver.InstallAlertNotifier
import com.idiots3.riskscanner.scanner.AppScanner
import com.idiots3.riskscanner.scanner.BehaviourAnomalyScanner
import com.idiots3.riskscanner.scanner.DevicePostureScanner
import com.idiots3.riskscanner.scanner.StaticInspector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppSortOrder(val label: String) {
    RISK_DESC("Highest risk"),
    NAME_ASC("Name (A–Z)"),
    RECENTLY_UPDATED("Recently updated"),
    PERMISSIONS_DESC("Most permissions")
}

enum class QuickAppFilter(val label: String) {
    SIDELOADED("Sideloaded"),
    SPECIAL_ACCESS("Special access"),
    HIDDEN("Hidden apps")
}

data class MainUiState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val deviceSummary: DeviceSummary? = null,
    val devicePosture: DevicePosture? = null,
    val behaviourResult: BehaviourScanResult = BehaviourScanResult(permissionGranted = false),
    val recommendations: RecommendationSummary? = null,
    val includeSystemApps: Boolean = false,
    val searchQuery: String = "",
    val selectedFilter: RiskLevel? = null,
    val selectedCategory: PermissionCategory? = null,
    val quickFilter: QuickAppFilter? = null,
    val sortOrder: AppSortOrder = AppSortOrder.RISK_DESC,
    val error: String? = null,
    val aiActive: Boolean = false,
    val aiProcessed: Int = 0,
    val aiTotal: Int = 0,
    val aiAvailable: Boolean = true,
    val recentChanges: List<PermissionChange> = emptyList(),
    val staticChecks: Map<String, StaticChecks> = emptyMap(),
    val staticChecksLoading: Set<String> = emptySet(),
    val lastScanTime: Long = 0L
)

class MainViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val scanner = AppScanner(appContext)
    private val postureScanner = DevicePostureScanner(appContext)
    private val behaviourScanner = BehaviourAnomalyScanner(appContext)
    private val inspector = StaticInspector(appContext)
    private val snapshotStore = SnapshotStore(appContext)
    private val purposeEngine = PurposeInferenceEngine(
        appContext,
        TextEmbedderEngine(appContext),
        EmbeddingStore(appContext)
    )

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(context.applicationContext) as T
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var aiJob: Job? = null
    private var packageChangeJob: Job? = null

    @Volatile
    private var pendingExternalRefresh = false

    init {
        scanApps()
    }

    // ------------------------------------------------------------------ scanning

    fun scanApps() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            aiJob?.cancel()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    aiActive = false,
                    aiProcessed = 0,
                    aiTotal = 0,
                    staticChecks = emptyMap(),
                    staticChecksLoading = emptySet()
                )
            }
            try {
                val includeSystem = _uiState.value.includeSystemApps
                val previous = snapshotStore.load()
                val apps = scanner.scanAllApps(includeSystem)
                val now = System.currentTimeMillis()
                val changes = PermissionDiff.compute(previous, apps, now)
                snapshotStore.save(PermissionDiff.snapshotOf(apps))
                snapshotStore.setLastScanTime(now)

                val posture = postureScanner.scan(apps)
                val behaviour = behaviourScanner.scan(apps)
                val summary = buildDeviceSummary(apps)
                val recommendations = RecommendationEngine.generate(apps, posture, behaviour)

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        apps = apps,
                        filteredApps = applyFiltersAndSort(
                            apps = apps,
                            query = state.searchQuery,
                            riskLevel = state.selectedFilter,
                            category = state.selectedCategory,
                            quickFilter = state.quickFilter,
                            sortOrder = state.sortOrder
                        ),
                        deviceSummary = summary,
                        devicePosture = posture,
                        behaviourResult = behaviour,
                        recommendations = recommendations,
                        recentChanges = changes,
                        lastScanTime = now
                    )
                }
                notifyNewRisks(changes, apps)

                launchAiPass()
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Scan failed: ${t.message ?: t.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    private fun launchAiPass() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val targets = _uiState.value.apps.filter { it.needsAiAnalysis() }
            if (targets.isEmpty()) {
                _uiState.update { it.copy(aiActive = false, aiAvailable = true, aiTotal = 0, aiProcessed = 0) }
                return@launch
            }

            _uiState.update { it.copy(aiActive = true, aiTotal = targets.size, aiProcessed = 0) }

            val available = purposeEngine.prepare()
            if (!available) {
                _uiState.update { it.copy(aiActive = false, aiAvailable = false, aiTotal = 0, aiProcessed = 0) }
                return@launch
            }

            var processed = 0
            for (app in targets) {
                val assessment = purposeEngine.assess(app)
                val withAi = app.copy(aiAssessment = assessment)
                val result = RiskScorer.score(withAi, assessment)
                val scored = withAi.copy(
                    ruleScore = result.ruleScore,
                    aiDelta = result.aiDelta,
                    riskScore = result.score,
                    riskLevel = result.level,
                    riskReasons = result.reasons,
                    scoreBreakdown = result.breakdown
                )
                replaceApp(scored)
                processed++
                if (processed % 5 == 0 || processed == targets.size) {
                    _uiState.update { it.copy(aiProcessed = processed) }
                }
            }

            purposeEngine.flush()
            // Release native TFLite runtime memory until the next scan; prepare() will re-open safely.
            purposeEngine.releaseModel()

            _uiState.update { state ->
                val sortedApps = state.apps.sortedByDescending { it.riskScore }
                state.copy(
                    aiActive = false,
                    aiProcessed = processed,
                    apps = sortedApps,
                    filteredApps = applyFiltersAndSort(
                        apps = sortedApps,
                        query = state.searchQuery,
                        riskLevel = state.selectedFilter,
                        category = state.selectedCategory,
                        quickFilter = state.quickFilter,
                        sortOrder = state.sortOrder
                    ),
                    deviceSummary = buildDeviceSummary(sortedApps),
                    recommendations = RecommendationEngine.generate(
                        sortedApps,
                        state.devicePosture,
                        state.behaviourResult
                    )
                )
            }
        }
    }

    private fun replaceApp(updated: AppInfo) {
        _uiState.update { state ->
            val apps = state.apps
                .map { if (it.packageName == updated.packageName) updated else it }
                .sortedByDescending { it.riskScore }
            state.copy(
                apps = apps,
                filteredApps = applyFiltersAndSort(
                    apps = apps,
                    query = state.searchQuery,
                    riskLevel = state.selectedFilter,
                    category = state.selectedCategory,
                    quickFilter = state.quickFilter,
                    sortOrder = state.sortOrder
                )
            )
        }
    }

    private fun notifyNewRisks(changes: List<PermissionChange>, apps: List<AppInfo>) {
        if (changes.isEmpty()) return
        val appMap = apps.associateBy { it.packageName }
        changes
            .filter {
                (it.type == ChangeType.NEW_APP || it.type == ChangeType.PERMISSIONS_ADDED) &&
                    it.newScore >= RiskLevel.HIGH.minScore
            }
            .forEach { change ->
                val app = appMap[change.packageName] ?: return@forEach
                val prefix = if (change.type == ChangeType.PERMISSIONS_ADDED && change.addedPermissions.isNotEmpty()) {
                    "Added ${change.addedPermissions.take(2).joinToString(", ")}."
                } else {
                    null
                }
                InstallAlertNotifier.notifyHighRisk(appContext, app, prefix)
            }
    }

    // ------------------------------------------------------------------ install & external monitoring

    /** Called (debounced) when the system reports a package install, update, or removal. */
    fun onPackageChanged(@Suppress("UNUSED_PARAMETER") packageName: String) {
        packageChangeJob?.cancel()
        packageChangeJob = viewModelScope.launch {
            delay(SETTLE_DELAY_MS)
            scanApps()
        }
    }

    /** Marks that the user navigated to Android Settings or Uninstall so returning refreshes immediately. */
    fun markExternalSettingsOpened() {
        pendingExternalRefresh = true
    }

    /** Refreshes on resume if the user just returned from system settings/uninstall, or if the scan is stale. */
    fun checkForExternalChanges() {
        if (_uiState.value.isLoading) return
        if (pendingExternalRefresh) {
            pendingExternalRefresh = false
            scanApps()
            return
        }
        val age = System.currentTimeMillis() - snapshotStore.lastScanTime()
        if (age > RESCAN_AGE_MS) scanApps()
    }

    // ------------------------------------------------------------------ filtering & sorting

    fun toggleSystemApps(include: Boolean) {
        if (_uiState.value.includeSystemApps == include) return
        _uiState.update { it.copy(includeSystemApps = include) }
        scanApps()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredApps = applyFiltersAndSort(
                    apps = it.apps,
                    query = query,
                    riskLevel = it.selectedFilter,
                    category = it.selectedCategory,
                    quickFilter = it.quickFilter,
                    sortOrder = it.sortOrder
                )
            )
        }
    }

    fun filterByRisk(riskLevel: RiskLevel?) {
        _uiState.update {
            it.copy(
                selectedFilter = riskLevel,
                filteredApps = applyFiltersAndSort(
                    apps = it.apps,
                    query = it.searchQuery,
                    riskLevel = riskLevel,
                    category = it.selectedCategory,
                    quickFilter = it.quickFilter,
                    sortOrder = it.sortOrder
                )
            )
        }
    }

    fun filterByCategory(category: PermissionCategory?) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                filteredApps = applyFiltersAndSort(
                    apps = it.apps,
                    query = it.searchQuery,
                    riskLevel = it.selectedFilter,
                    category = category,
                    quickFilter = it.quickFilter,
                    sortOrder = it.sortOrder
                )
            )
        }
    }

    fun filterByQuick(quickFilter: QuickAppFilter?) {
        _uiState.update {
            it.copy(
                quickFilter = quickFilter,
                filteredApps = applyFiltersAndSort(
                    apps = it.apps,
                    query = it.searchQuery,
                    riskLevel = it.selectedFilter,
                    category = it.selectedCategory,
                    quickFilter = quickFilter,
                    sortOrder = it.sortOrder
                )
            )
        }
    }

    fun setSortOrder(sortOrder: AppSortOrder) {
        _uiState.update {
            it.copy(
                sortOrder = sortOrder,
                filteredApps = applyFiltersAndSort(
                    apps = it.apps,
                    query = it.searchQuery,
                    riskLevel = it.selectedFilter,
                    category = it.selectedCategory,
                    quickFilter = it.quickFilter,
                    sortOrder = sortOrder
                )
            )
        }
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                selectedFilter = null,
                selectedCategory = null,
                quickFilter = null,
                filteredApps = applyFiltersAndSort(
                    apps = it.apps,
                    query = "",
                    riskLevel = null,
                    category = null,
                    quickFilter = null,
                    sortOrder = it.sortOrder
                )
            )
        }
    }

    fun applyDashboardJumpFilter(
        riskLevel: RiskLevel? = null,
        category: PermissionCategory? = null,
        quickFilter: QuickAppFilter? = null
    ) {
        _uiState.update {
            it.copy(
                searchQuery = "",
                selectedFilter = riskLevel,
                selectedCategory = category,
                quickFilter = quickFilter,
                filteredApps = applyFiltersAndSort(
                    apps = it.apps,
                    query = "",
                    riskLevel = riskLevel,
                    category = category,
                    quickFilter = quickFilter,
                    sortOrder = it.sortOrder
                )
            )
        }
    }

    private fun applyFiltersAndSort(
        apps: List<AppInfo>,
        query: String,
        riskLevel: RiskLevel?,
        category: PermissionCategory?,
        quickFilter: QuickAppFilter?,
        sortOrder: AppSortOrder
    ): List<AppInfo> {
        var filtered = apps
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        if (riskLevel != null) {
            filtered = filtered.filter { it.riskLevel == riskLevel }
        }
        if (category != null) {
            filtered = filtered.filter { app ->
                app.permissions.any { it.category == category } ||
                    (category == PermissionCategory.SPECIAL_ACCESS && app.specialAccess.hasAny)
            }
        }
        if (quickFilter != null) {
            filtered = when (quickFilter) {
                QuickAppFilter.SIDELOADED -> filtered.filter { it.isSideloaded }
                QuickAppFilter.SPECIAL_ACCESS -> filtered.filter { it.specialAccess.hasAny }
                QuickAppFilter.HIDDEN -> filtered.filter { !it.hasLauncherActivity }
            }
        }
        return when (sortOrder) {
            AppSortOrder.RISK_DESC -> filtered.sortedWith(
                compareByDescending<AppInfo> { it.riskScore }.thenBy { it.appName.lowercase() }
            )
            AppSortOrder.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
            AppSortOrder.RECENTLY_UPDATED -> filtered.sortedByDescending { it.updateDate }
            AppSortOrder.PERMISSIONS_DESC -> filtered.sortedWith(
                compareByDescending<AppInfo> { it.grantedSensitive.size }
                    .thenByDescending { it.permissions.count { p -> p.isGranted } }
            )
        }
    }

    // ------------------------------------------------------------------ static checks

    fun loadStaticChecks(packageName: String) {
        val state = _uiState.value
        if (state.staticChecks.containsKey(packageName) || state.staticChecksLoading.contains(packageName)) return
        _uiState.update { it.copy(staticChecksLoading = it.staticChecksLoading + packageName) }
        viewModelScope.launch {
            val app = _uiState.value.apps.firstOrNull { it.packageName == packageName }
            val checks = if (app != null) {
                inspector.inspect(app)
            } else {
                StaticChecks(hashError = "App not found in the last scan")
            }
            _uiState.update {
                it.copy(
                    staticChecks = it.staticChecks + (packageName to checks),
                    staticChecksLoading = it.staticChecksLoading - packageName
                )
            }
        }
    }

    // ------------------------------------------------------------------ summary

    private fun buildDeviceSummary(apps: List<AppInfo>): DeviceSummary {
        val categoryBreakdown = PermissionCategory.entries.associateWith { category ->
            apps.count { app ->
                app.permissions.any { it.category == category } ||
                    (category == PermissionCategory.SPECIAL_ACCESS && app.specialAccess.hasAny)
            }
        }
        return DeviceSummary(
            totalApps = apps.size,
            criticalApps = apps.count { it.riskLevel == RiskLevel.CRITICAL },
            highApps = apps.count { it.riskLevel == RiskLevel.HIGH },
            mediumApps = apps.count { it.riskLevel == RiskLevel.MEDIUM },
            lowApps = apps.count { it.riskLevel == RiskLevel.LOW },
            totalPermissionsGranted = apps.sumOf { app -> app.permissions.count { it.isGranted } },
            categoryBreakdown = categoryBreakdown,
            topRiskApps = apps.sortedByDescending { it.riskScore }.take(5),
            sideloadedApps = apps.count { it.isSideloaded },
            specialAccessApps = apps.count { it.specialAccess.enabledLabels.isNotEmpty() },
            hiddenApps = apps.count { !it.hasLauncherActivity },
            aiAnalyzedApps = apps.count { it.aiAssessment?.modelUsed == true },
            aiUnexplainedApps = apps.count { !it.aiAssessment?.unexplainedCategories.isNullOrEmpty() }
        )
    }

    private companion object {
        const val SETTLE_DELAY_MS = 1_500L
        const val RESCAN_AGE_MS = 60_000L
    }
}
