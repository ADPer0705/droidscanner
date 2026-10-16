package com.idiots3.riskscanner.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.idiots3.riskscanner.data.SnapshotStore
import com.idiots3.riskscanner.engine.PermissionDiff
import com.idiots3.riskscanner.engine.RiskScorer
import com.idiots3.riskscanner.ml.EmbeddingStore
import com.idiots3.riskscanner.ml.PurposeInferenceEngine
import com.idiots3.riskscanner.ml.TextEmbedderEngine
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.ChangeType
import com.idiots3.riskscanner.model.DeviceSummary
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PermissionChange
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.StaticChecks
import com.idiots3.riskscanner.receiver.InstallAlertNotifier
import com.idiots3.riskscanner.scanner.AppScanner
import com.idiots3.riskscanner.scanner.StaticInspector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val deviceSummary: DeviceSummary? = null,
    val includeSystemApps: Boolean = false,
    val searchQuery: String = "",
    val selectedFilter: RiskLevel? = null,
    val selectedCategory: PermissionCategory? = null,
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

    init {
        scanApps()
    }

    // ------------------------------------------------------------------ scanning

    fun scanApps() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            aiJob?.cancel()
            _uiState.update {
                it.copy(isLoading = true, error = null, aiActive = false, aiProcessed = 0, aiTotal = 0)
            }
            try {
                // Deterministic pass first: instant, explainable, offline.
                val previous = snapshotStore.load()
                val apps = scanner.scanAllApps(_uiState.value.includeSystemApps)
                val now = System.currentTimeMillis()
                val changes = PermissionDiff.compute(previous, apps, now)
                snapshotStore.save(PermissionDiff.snapshotOf(apps))
                snapshotStore.setLastScanTime(now)

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        apps = apps,
                        filteredApps = applyFilters(apps, state.searchQuery, state.selectedFilter, state.selectedCategory),
                        deviceSummary = buildDeviceSummary(apps),
                        recentChanges = changes,
                        lastScanTime = now
                    )
                }
                notifyNewRisks(changes)

                // On-device AI refinement streams in afterwards.
                launchAiPass()
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Scan failed: ${t.message ?: t.javaClass.simpleName}")
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
            // Release the native model memory; it is lazily re-created on the next scan.
            purposeEngine.releaseModel()

            _uiState.update { state ->
                state.copy(
                    aiActive = false,
                    aiProcessed = processed,
                    deviceSummary = buildDeviceSummary(state.apps)
                )
            }
        }
    }

    private fun replaceApp(updated: AppInfo) {
        _uiState.update { state ->
            val apps = state.apps.map { if (it.packageName == updated.packageName) updated else it }
            state.copy(
                apps = apps,
                filteredApps = applyFilters(apps, state.searchQuery, state.selectedFilter, state.selectedCategory)
            )
        }
    }

    private fun notifyNewRisks(changes: List<PermissionChange>) {
        if (changes.isEmpty()) return
        val apps = _uiState.value.apps
        changes
            .filter { it.type == ChangeType.NEW_APP && it.newScore >= RiskLevel.HIGH.minScore }
            .forEach { change ->
                apps.firstOrNull { it.packageName == change.packageName }
                    ?.let { InstallAlertNotifier.notifyHighRisk(appContext, it) }
            }
    }

    // ------------------------------------------------------------------ install monitoring

    /** Called (debounced) when the system reports a package install/update/removal. */
    fun onPackageChanged(packageName: String) {
        viewModelScope.launch {
            delay(SETTLE_DELAY_MS)
            scanApps()
        }
    }

    /** Cheap check used on resume: rescan if the last scan is stale. */
    fun checkForExternalChanges() {
        if (_uiState.value.isLoading) return
        val age = System.currentTimeMillis() - snapshotStore.lastScanTime()
        if (age > RESCAN_AGE_MS) scanApps()
    }

    // ------------------------------------------------------------------ filtering

    fun toggleSystemApps(include: Boolean) {
        _uiState.update { it.copy(includeSystemApps = include) }
        scanApps()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredApps = applyFilters(it.apps, query, it.selectedFilter, it.selectedCategory)
            )
        }
    }

    fun filterByRisk(riskLevel: RiskLevel?) {
        _uiState.update {
            it.copy(
                selectedFilter = riskLevel,
                filteredApps = applyFilters(it.apps, it.searchQuery, riskLevel, it.selectedCategory)
            )
        }
    }

    fun filterByCategory(category: PermissionCategory?) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                filteredApps = applyFilters(it.apps, it.searchQuery, it.selectedFilter, category)
            )
        }
    }

    private fun applyFilters(
        apps: List<AppInfo>,
        query: String,
        riskLevel: RiskLevel?,
        category: PermissionCategory?
    ): List<AppInfo> {
        var filtered = apps
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        if (riskLevel != null) filtered = filtered.filter { it.riskLevel == riskLevel }
        if (category != null) filtered = filtered.filter { app -> app.permissions.any { it.category == category } }
        return filtered
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

    fun appFor(packageName: String): AppInfo? =
        _uiState.value.apps.firstOrNull { it.packageName == packageName }

    // ------------------------------------------------------------------ summary

    private fun buildDeviceSummary(apps: List<AppInfo>): DeviceSummary {
        val categoryBreakdown = PermissionCategory.entries.associateWith { category ->
            apps.count { app -> app.permissions.any { it.category == category } }
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
        const val SETTLE_DELAY_MS = 2_000L
        const val RESCAN_AGE_MS = 60_000L
    }
}
