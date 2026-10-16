package com.idiots3.riskscanner.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.idiots3.riskscanner.model.*
import com.idiots3.riskscanner.scanner.AppScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val deviceSummary: DeviceSummary? = null,
    val includeSystemApps: Boolean = false,
    val searchQuery: String = "",
    val selectedFilter: RiskLevel? = null, // null = all
    val selectedCategory: PermissionCategory? = null,
    val error: String? = null
)

class MainViewModel(
    private val appScanner: AppScanner
) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(AppScanner(context.applicationContext)) as T
        }
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        scanApps()
    }

    fun scanApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val apps = appScanner.scanAllApps(_uiState.value.includeSystemApps)
                val summary = buildDeviceSummary(apps)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    apps = apps,
                    filteredApps = applyFilters(apps, _uiState.value.searchQuery, _uiState.value.selectedFilter, _uiState.value.selectedCategory),
                    deviceSummary = summary
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to scan apps: ${e.message}"
                )
            }
        }
    }

    fun toggleSystemApps(include: Boolean) {
        _uiState.value = _uiState.value.copy(includeSystemApps = include)
        scanApps()
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredApps = applyFilters(_uiState.value.apps, query, _uiState.value.selectedFilter, _uiState.value.selectedCategory)
        )
    }

    fun filterByRisk(riskLevel: RiskLevel?) {
        _uiState.value = _uiState.value.copy(
            selectedFilter = riskLevel,
            filteredApps = applyFilters(_uiState.value.apps, _uiState.value.searchQuery, riskLevel, _uiState.value.selectedCategory)
        )
    }

    fun filterByCategory(category: PermissionCategory?) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredApps = applyFilters(_uiState.value.apps, _uiState.value.searchQuery, _uiState.value.selectedFilter, category)
        )
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
        if (riskLevel != null) {
            filtered = filtered.filter { it.riskLevel == riskLevel }
        }
        if (category != null) {
            filtered = filtered.filter { it.categories.containsKey(category) }
        }
        return filtered
    }

    private fun buildDeviceSummary(apps: List<AppInfo>): DeviceSummary {
        val categoryBreakdown = PermissionCategory.entries.associateWith { cat ->
            apps.count { app -> app.categories.containsKey(cat) }
        }
        return DeviceSummary(
            totalApps = apps.size,
            criticalApps = apps.count { it.riskLevel == RiskLevel.CRITICAL },
            highApps = apps.count { it.riskLevel == RiskLevel.HIGH },
            mediumApps = apps.count { it.riskLevel == RiskLevel.MEDIUM },
            lowApps = apps.count { it.riskLevel == RiskLevel.LOW },
            totalPermissionsGranted = apps.sumOf { app -> app.permissions.count { it.isGranted } },
            categoryBreakdown = categoryBreakdown,
            topRiskApps = apps.take(5),
            sideloadedApps = apps.count { it.installerSource == "Sideloaded" || it.installerSource == "Unknown" }
        )
    }
}

