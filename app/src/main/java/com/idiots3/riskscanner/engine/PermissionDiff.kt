package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.ChangeType
import com.idiots3.riskscanner.model.PermissionChange

/** Compact record of an app's sensitive access at the time of a scan. */
data class SnapshotEntry(
    val versionCode: Long,
    val score: Int,
    val level: String,
    val sensitiveGranted: Set<String>
)

/**
 * Compares two scans to answer "what changed since last time?" — newly installed apps,
 * updated apps, and updates that quietly added sensitive permissions.
 */
object PermissionDiff {

    private const val MAX_ITEMS = 25
    private const val SCORE_JUMP = 15

    fun snapshotOf(apps: List<AppInfo>): Map<String, SnapshotEntry> =
        apps.associate { app ->
            app.packageName to SnapshotEntry(
                versionCode = app.versionCode,
                score = app.riskScore,
                level = app.riskLevel.label,
                sensitiveGranted = app.grantedSensitive.map { it.shortName }.toSet()
            )
        }

    fun compute(
        previous: Map<String, SnapshotEntry>?,
        current: List<AppInfo>,
        now: Long
    ): List<PermissionChange> {
        if (previous.isNullOrEmpty()) return emptyList()
        val changes = mutableListOf<PermissionChange>()

        for (app in current) {
            val before = previous[app.packageName]
            val nowGranted = app.grantedSensitive.map { it.shortName }.toSet()

            if (before == null) {
                changes += PermissionChange(
                    packageName = app.packageName,
                    appName = app.appName,
                    type = ChangeType.NEW_APP,
                    newScore = app.riskScore,
                    timestamp = now
                )
                continue
            }

            val added = (nowGranted - before.sensitiveGranted).sorted()
            val removed = (before.sensitiveGranted - nowGranted).sorted()

            when {
                added.isNotEmpty() -> changes += PermissionChange(
                    packageName = app.packageName,
                    appName = app.appName,
                    type = ChangeType.PERMISSIONS_ADDED,
                    addedPermissions = added,
                    oldScore = before.score,
                    newScore = app.riskScore,
                    timestamp = now
                )

                app.riskScore - before.score >= SCORE_JUMP -> changes += PermissionChange(
                    packageName = app.packageName,
                    appName = app.appName,
                    type = ChangeType.SCORE_INCREASED,
                    oldScore = before.score,
                    newScore = app.riskScore,
                    timestamp = now
                )

                removed.isNotEmpty() -> changes += PermissionChange(
                    packageName = app.packageName,
                    appName = app.appName,
                    type = ChangeType.PERMISSIONS_REMOVED,
                    removedPermissions = removed,
                    oldScore = before.score,
                    newScore = app.riskScore,
                    timestamp = now
                )

                app.versionCode != before.versionCode -> changes += PermissionChange(
                    packageName = app.packageName,
                    appName = app.appName,
                    type = ChangeType.UPDATED,
                    oldScore = before.score,
                    newScore = app.riskScore,
                    timestamp = now
                )
            }
        }

        return changes
            .sortedWith(compareBy({ priority(it.type) }, { -it.newScore }))
            .take(MAX_ITEMS)
    }

    private fun priority(type: ChangeType): Int = when (type) {
        ChangeType.PERMISSIONS_ADDED -> 0
        ChangeType.NEW_APP -> 1
        ChangeType.SCORE_INCREASED -> 2
        ChangeType.PERMISSIONS_REMOVED -> 3
        ChangeType.UPDATED -> 4
    }
}
