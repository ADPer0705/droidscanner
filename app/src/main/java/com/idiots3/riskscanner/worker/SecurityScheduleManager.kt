package com.idiots3.riskscanner.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.idiots3.riskscanner.data.BackgroundScanSchedule
import java.util.concurrent.TimeUnit

/**
 * Configures WorkManager periodic security monitoring according to user preferences.
 * Designed to stay gentle on battery and RAM on consumer devices.
 */
object SecurityScheduleManager {

    private const val UNIQUE_PERIODIC_WORK = "droidscanner_bg_guard"
    private const val UNIQUE_IMMEDIATE_WORK = "droidscanner_bg_guard_now"

    fun applySchedule(context: Context, schedule: BackgroundScanSchedule) {
        val wm = runCatching { WorkManager.getInstance(context.applicationContext) }.getOrNull() ?: return
        if (schedule == BackgroundScanSchedule.OFF || schedule.intervalHours <= 0L) {
            wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            return
        }

        val flexHours = if (schedule.intervalHours >= 24L) 3L else 2L
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<BackgroundSecurityWorker>(
            schedule.intervalHours,
            TimeUnit.HOURS,
            flexHours,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        wm.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runImmediateCheck(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context.applicationContext) }.getOrNull() ?: return
        val request = OneTimeWorkRequestBuilder<BackgroundSecurityWorker>().build()
        wm.enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
