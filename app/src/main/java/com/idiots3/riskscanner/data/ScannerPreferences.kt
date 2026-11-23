package com.idiots3.riskscanner.data

import android.content.Context

enum class BackgroundScanSchedule(val label: String, val intervalHours: Long) {
    OFF("Off (manual scans only)", 0L),
    EVERY_12_HOURS("Every 12 hours (recommended)", 12L),
    DAILY("Once a day", 24L)
}

/**
 * Local user preferences for DroidScanner. Stored only on-device and excluded from backup.
 */
class ScannerPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var includeSystemApps: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_SYSTEM, false)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_SYSTEM, value).apply()

    var backgroundSchedule: BackgroundScanSchedule
        get() {
            val raw = prefs.getString(KEY_BG_SCHEDULE, BackgroundScanSchedule.EVERY_12_HOURS.name)
            return BackgroundScanSchedule.entries.firstOrNull { it.name == raw }
                ?: BackgroundScanSchedule.EVERY_12_HOURS
        }
        set(value) = prefs.edit().putString(KEY_BG_SCHEDULE, value.name).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var plainLanguageMode: Boolean
        get() = prefs.getBoolean(KEY_PLAIN_LANGUAGE, true)
        set(value) = prefs.edit().putBoolean(KEY_PLAIN_LANGUAGE, value).apply()

    var lastBackgroundCheckTime: Long
        get() = prefs.getLong(KEY_LAST_BG_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BG_CHECK, value).apply()

    private companion object {
        const val PREFS_NAME = "scanner_prefs"
        const val KEY_INCLUDE_SYSTEM = "include_system_apps"
        const val KEY_BG_SCHEDULE = "bg_schedule"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_PLAIN_LANGUAGE = "plain_language_mode"
        const val KEY_LAST_BG_CHECK = "last_bg_check"
    }
}
