package com.idiots3.riskscanner.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.idiots3.riskscanner.data.AuditHistory
import com.idiots3.riskscanner.data.AuditHistoryStore
import com.idiots3.riskscanner.data.BackgroundScanSchedule
import com.idiots3.riskscanner.data.ScannerPreferences
import com.idiots3.riskscanner.data.SnapshotStore
import com.idiots3.riskscanner.data.TrustStore
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
import com.idiots3.riskscanner.model.DismissedPostureRecord
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PermissionChange
import com.idiots3.riskscanner.model.PostureCheck
import com.idiots3.riskscanner.model.PreInstallScanResult
import com.idiots3.riskscanner.model.RecommendationSummary
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.StaticChecks
import com.idiots3.riskscanner.model.TrustCheckResult
import com.idiots3.riskscanner.model.TrustState
import com.idiots3.riskscanner.model.TrustValidator
import com.idiots3.riskscanner.receiver.InstallAlertNotifier
import com.idiots3.riskscanner.scanner.ApkFileScanner
import com.idiots3.riskscanner.scanner.AppScanner
import com.idiots3.riskscanner.scanner.BehaviourAnomalyScanner
import com.idiots3.riskscanner.scanner.DevicePostureScanner
import com.idiots3.riskscanner.scanner.StaticInspector
import com.idiots3.riskscanner.worker.SecurityScheduleManager
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
    HIDDEN("Hidden apps"),
    TRUSTED("Trusted")
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
    val plainLanguageMode: Boolean = true,
    val backgroundSchedule: BackgroundScanSchedule = BackgroundScanSchedule.EVERY_12_HOURS,
    val onboardingCompleted: Boolean = true,
    val lastBackgroundCheckTime: Long = 0L,
    val trustState: TrustState = TrustState(),
    val auditHistory: AuditHistory = AuditHistory(),
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
    val lastScanTime: Long = 0L,
    val apkScanLoading: Boolean = false,
    val apkScanResult: PreInstallScanResult? = null
)

class MainViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val scanner = AppScanner(appContext)
    private val postureScanner = DevicePostureScanner(appContext)
    private val behaviourScanner = BehaviourAnomalyScanner(appContext)
    private val inspector = StaticInspector(appContext)
    private val apkFileScanner = ApkFileScanner(appContext)
    private val snapshotStore = SnapshotStore(appContext)
    private val trustStore = TrustStore(appContext)
    private val historyStore = AuditHistoryStore(appContext)
    private val preferences = ScannerPreferences(appContext)
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

    private val _uiState = MutableStateFlow(
        MainUiState(
            includeSystemApps = preferences.includeSystemApps,
            plainLanguageMode = preferences.plainLanguageMode,
            backgroundSchedule = preferences.backgroundSchedule,
            onboardingCompleted = preferences.onboardingCompleted,
            lastBackgroundCheckTime = preferences.lastBackgroundCheckTime
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var aiJob: Job? = null
    private var packageChangeJob: Job? = null
    private var apkScanJob: Job? = null

    @Volatile
    private var pendingExternalRefresh = false

    init {
        SecurityScheduleManager.applySchedule(appContext, preferences.backgroundSchedule)
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
                val now = System.currentTimeMillis()
                val previous = snapshotStore.load()
                val loadedTrust = trustStore.load()
                val scannedApps = scanner.scanAllApps(includeSystem)

                // Validate user-trusted apps; auto-revoke trust if privileges or signer changed!
                val updatedTrustedMap = loadedTrust.trustedApps.toMutableMap()
                val trustBrokenEvents = mutableListOf<PermissionChange>()
                val apps = scannedApps.map { app ->
                    val record = loadedTrust.trustedApps[app.packageName]
                    if (record == null) {
                        app
                    } else {
                        when (val result = TrustValidator.validateAppTrust(app, record)) {
                            TrustCheckResult.Valid -> app.copy(isUserTrusted = true)
                            is TrustCheckResult.Invalidated -> {
                                updatedTrustedMap.remove(app.packageName)
                                trustBrokenEvents += PermissionChange(
                                    packageName = app.packageName,
                                    appName = app.appName,
                                    type = ChangeType.TRUST_BROKEN,
                                    detail = result.reasons.joinToString("; "),
                                    oldScore = app.riskScore,
                                    newScore = app.riskScore,
                                    timestamp = now
                                )
                                app.copy(isUserTrusted = false)
                            }
                        }
                    }
                }
                val finalTrustState = if (trustBrokenEvents.isNotEmpty()) {
                    loadedTrust.copy(trustedApps = updatedTrustedMap).also { trustStore.save(it) }
                } else {
                    loadedTrust
                }

                // Always diff & snapshot user apps consistently so toggling system apps
                // never floods the timeline or alerts with pre-installed system packages.
                val appChanges = PermissionDiff.compute(previous, apps, now, userAppsOnly = true)
                snapshotStore.save(PermissionDiff.snapshotOf(apps, userAppsOnly = true))
                snapshotStore.setLastScanTime(now)

                val posture = postureScanner.scan(apps, finalTrustState.dismissedPosture)
                val historyBefore = historyStore.load()
                val postureRegressions = AuditHistoryStore.detectPostureRegressions(
                    historyBefore.lastPostureStates,
                    posture,
                    now
                )

                val behaviour = behaviourScanner.scan(apps)
                val summary = buildDeviceSummary(apps)
                val recommendations = RecommendationEngine.generate(apps, posture, behaviour)

                val combinedChanges = (trustBrokenEvents + appChanges + postureRegressions)
                    .distinctBy { "${it.type.name}:${it.packageName}" }

                val updatedHistory = historyStore.recordScan(
                    newEvents = combinedChanges,
                    posture = posture,
                    deviceScore = recommendations.deviceScore,
                    appScore = recommendations.scoreBreakdown["App Safety"],
                    postureScore = posture.protectionScore,
                    now = now
                )

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
                        trustState = finalTrustState,
                        auditHistory = updatedHistory,
                        recentChanges = combinedChanges,
                        lastScanTime = now,
                        lastBackgroundCheckTime = preferences.lastBackgroundCheckTime
                    )
                }
                notifyNewRisks(combinedChanges, apps)

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
        changes.take(4).forEach { change ->
            when (change.type) {
                ChangeType.SIGNER_CHANGED -> InstallAlertNotifier.notifySecurityEvent(
                    context = appContext,
                    notificationId = ("signer:" + change.packageName).hashCode(),
                    title = "Signing certificate changed: ${change.appName}",
                    body = "The developer signature of ${change.appName} changed since your last scan.",
                    targetPackage = change.packageName
                )
                ChangeType.TRUST_BROKEN -> InstallAlertNotifier.notifySecurityEvent(
                    context = appContext,
                    notificationId = ("trust:" + change.packageName).hashCode(),
                    title = "Reviewed app changed: ${change.appName}",
                    body = "${change.detail ?: "Access changed"}. Trust was automatically cleared.",
                    targetPackage = change.packageName
                )
                ChangeType.SPECIAL_ACCESS_GRANTED -> {
                    val app = appMap[change.packageName]
                    if (app?.isUserTrusted == true) return@forEach
                    InstallAlertNotifier.notifySecurityEvent(
                        context = appContext,
                        notificationId = ("special:" + change.packageName).hashCode(),
                        title = "Special access enabled: ${change.appName}",
                        body = change.detail ?: "High-privilege special access was enabled.",
                        targetPackage = change.packageName
                    )
                }
                ChangeType.NEW_APP, ChangeType.PERMISSIONS_ADDED -> {
                    if (change.newScore < RiskLevel.HIGH.minScore) return@forEach
                    val app = appMap[change.packageName] ?: return@forEach
                    if (app.isUserTrusted || app.isSelfApp) return@forEach
                    val prefix = if (change.type == ChangeType.PERMISSIONS_ADDED && change.addedPermissions.isNotEmpty()) {
                        "Added ${change.addedPermissions.take(2).joinToString(", ")}."
                    } else null
                    InstallAlertNotifier.notifyHighRisk(appContext, app, prefix)
                }
                else -> Unit
            }
        }
    }

    // ------------------------------------------------------------------ trust & preferences

    fun trustApp(app: AppInfo) {
        viewModelScope.launch {
            val currentTrust = _uiState.value.trustState
            val record = TrustValidator.recordFor(app)
            val updatedTrust = currentTrust.copy(
                trustedApps = currentTrust.trustedApps + (app.packageName to record)
            )
            trustStore.save(updatedTrust)
            applyTrustAndRecompute(updatedTrust)
        }
    }

    fun untrustApp(packageName: String) {
        viewModelScope.launch {
            val currentTrust = _uiState.value.trustState
            val updatedTrust = currentTrust.copy(
                trustedApps = currentTrust.trustedApps - packageName
            )
            trustStore.save(updatedTrust)
            applyTrustAndRecompute(updatedTrust)
        }
    }

    fun dismissPostureCheck(check: PostureCheck) {
        viewModelScope.launch {
            val currentTrust = _uiState.value.trustState
            val record = DismissedPostureRecord(
                checkId = check.id,
                title = check.title,
                detailFingerprint = check.detailFingerprint,
                dismissedAt = System.currentTimeMillis()
            )
            val updatedTrust = currentTrust.copy(
                dismissedPosture = currentTrust.dismissedPosture + (check.id to record)
            )
            trustStore.save(updatedTrust)
            applyTrustAndRecompute(updatedTrust)
        }
    }

    fun restorePostureCheck(checkId: String) {
        viewModelScope.launch {
            val currentTrust = _uiState.value.trustState
            val updatedTrust = currentTrust.copy(
                dismissedPosture = currentTrust.dismissedPosture - checkId
            )
            trustStore.save(updatedTrust)
            applyTrustAndRecompute(updatedTrust)
        }
    }

    private suspend fun applyTrustAndRecompute(trustState: TrustState) {
        val currentState = _uiState.value
        val updatedApps = currentState.apps.map { app ->
            val rec = trustState.trustedApps[app.packageName]
            val valid = rec != null && TrustValidator.validateAppTrust(app, rec) is TrustCheckResult.Valid
            app.copy(isUserTrusted = valid)
        }
        val updatedPosture = postureScanner.scan(updatedApps, trustState.dismissedPosture)
        val summary = buildDeviceSummary(updatedApps)
        val recs = RecommendationEngine.generate(updatedApps, updatedPosture, currentState.behaviourResult)
        _uiState.update { state ->
            state.copy(
                trustState = trustState,
                apps = updatedApps,
                filteredApps = applyFiltersAndSort(
                    apps = updatedApps,
                    query = state.searchQuery,
                    riskLevel = state.selectedFilter,
                    category = state.selectedCategory,
                    quickFilter = state.quickFilter,
                    sortOrder = state.sortOrder
                ),
                deviceSummary = summary,
                devicePosture = updatedPosture,
                recommendations = recs
            )
        }
    }

    fun setPlainLanguageMode(enabled: Boolean) {
        preferences.plainLanguageMode = enabled
        _uiState.update { it.copy(plainLanguageMode = enabled) }
    }

    fun setBackgroundSchedule(schedule: BackgroundScanSchedule) {
        preferences.backgroundSchedule = schedule
        SecurityScheduleManager.applySchedule(appContext, schedule)
        _uiState.update { it.copy(backgroundSchedule = schedule) }
    }

    fun triggerBackgroundCheckNow() {
        SecurityScheduleManager.runImmediateCheck(appContext)
    }

    fun completeOnboarding() {
        preferences.onboardingCompleted = true
        _uiState.update { it.copy(onboardingCompleted = true) }
    }

    fun clearAuditTimeline() {
        viewModelScope.launch {
            historyStore.clearEvents()
            val refreshed = historyStore.load()
            _uiState.update { it.copy(auditHistory = refreshed, recentChanges = emptyList()) }
        }
    }

    // ------------------------------------------------------------------ pre-install APK inspection

    fun scanApkUri(uri: Uri) {
        apkScanJob?.cancel()
        apkScanJob = viewModelScope.launch {
            _uiState.update { it.copy(apkScanLoading = true, apkScanResult = null) }
            val result = apkFileScanner.scanUri(uri)
            _uiState.update { it.copy(apkScanLoading = false, apkScanResult = result) }
        }
    }

    fun clearApkScanResult() {
        apkScanJob?.cancel()
        _uiState.update { it.copy(apkScanLoading = false, apkScanResult = null) }
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
        preferences.includeSystemApps = include
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
                QuickAppFilter.TRUSTED -> filtered.filter { it.isUserTrusted }
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
            criticalApps = apps.count { it.riskLevel == RiskLevel.CRITICAL && !it.isUserTrusted },
            highApps = apps.count { it.riskLevel == RiskLevel.HIGH && !it.isUserTrusted },
            mediumApps = apps.count { it.riskLevel == RiskLevel.MEDIUM },
            lowApps = apps.count { it.riskLevel == RiskLevel.LOW },
            totalPermissionsGranted = apps.sumOf { app -> app.permissions.count { it.isGranted } },
            categoryBreakdown = categoryBreakdown,
            topRiskApps = apps.filter { !it.isSelfApp && !it.isUserTrusted }.sortedByDescending { it.riskScore }.take(5),
            sideloadedApps = apps.count { it.isSideloaded },
            specialAccessApps = apps.count { it.specialAccess.enabledLabels.isNotEmpty() },
            hiddenApps = apps.count { !it.hasLauncherActivity },
            aiAnalyzedApps = apps.count { it.aiAssessment?.modelUsed == true },
            aiUnexplainedApps = apps.count { !it.aiAssessment?.unexplainedCategories.isNullOrEmpty() },
            trustedApps = apps.count { it.isUserTrusted },
            impersonationAlerts = apps.count { !it.impersonationTarget.isNullOrBlank() && !it.isUserTrusted }
        )
    }

    private companion object {
        const val SETTLE_DELAY_MS = 1_500L
        const val RESCAN_AGE_MS = 60_000L
    }
}
