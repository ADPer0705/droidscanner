package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.ChangeType
import com.idiots3.riskscanner.model.PermissionChange

/** Compact record of an app's sensitive access, special access, and signer at the time of a scan. */
data class SnapshotEntry(
    val versionCode: Long,
    val score: Int,
    val level: String,
    val sensitiveGranted: Set<String>,
    val enabledSpecialAccess: Set<String> = emptySet(),
    val signingCertSha256: String? = null,
    val isSystemApp: Boolean = false
)

/**
 * Compares two scans to answer "what changed since last time?" — newly installed apps,
 * updated apps, updates that quietly added sensitive permissions or special access,
 * and signing-certificate changes.
 *
 * Always baselines user-installed apps separately from the UI's "Include system apps" toggle
 * so toggling system apps never floods the user with false "Newly installed" alerts.
 */
object PermissionDiff {

    private const val MAX_ITEMS = 30
    private const val SCORE_JUMP = 15

    fun snapshotOf(apps: List<AppInfo>, userAppsOnly: Boolean = true): Map<String, SnapshotEntry> =
        apps
            .filter { !userAppsOnly || !it.isSystemApp }
            .associate { app ->
                app.packageName to SnapshotEntry(
                    versionCode = app.versionCode,
                    score = app.riskScore,
                    level = app.riskLevel.label,
                    sensitiveGranted = app.grantedSensitive.map { it.shortName }.toSet(),
                    enabledSpecialAccess = app.specialAccess.enabledLabels.toSet(),
                    signingCertSha256 = app.signingCertSha256,
                    isSystemApp = app.isSystemApp
                )
            }

    fun compute(
        previous: Map<String, SnapshotEntry>?,
        current: List<AppInfo>,
        now: Long,
        userAppsOnly: Boolean = true
    ): List<PermissionChange> {
        if (previous.isNullOrEmpty()) return emptyList()

        val prevScoped = if (userAppsOnly) previous.filterValues { !it.isSystemApp } else previous
        if (prevScoped.isEmpty()) return emptyList()

        val currentScoped = if (userAppsOnly) current.filter { !it.isSystemApp } else current
        val changes = mutableListOf<PermissionChange>()

        for (app in currentScoped) {
            val before = prevScoped[app.packageName]
            val nowGranted = app.grantedSensitive.map { it.shortName }.toSet()
            val nowSpecial = app.specialAccess.enabledLabels.toSet()

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

            val signerChanged = !before.signingCertSha256.isNullOrBlank() &&
                !app.signingCertSha256.isNullOrBlank() &&
                !before.signingCertSha256.equals(app.signingCertSha256, ignoreCase = true)

            val addedSpecial = (nowSpecial - before.enabledSpecialAccess).sorted()
            val added = (nowGranted - before.sensitiveGranted).sorted()
            val removed = (before.sensitiveGranted - nowGranted).sorted()

            when {
                signerChanged -> changes += PermissionChange(
                    packageName = app.packageName,
                    appName = app.appName,
                    type = ChangeType.SIGNER_CHANGED,
                    detail = "Developer signing certificate changed since last scan",
                    oldScore = before.score,
                    newScore = app.riskScore,
                    timestamp = now
                )

                addedSpecial.isNotEmpty() -> changes += PermissionChange(
                    packageName = app.packageName,
                    appName = app.appName,
                    type = ChangeType.SPECIAL_ACCESS_GRANTED,
                    addedSpecialAccess = addedSpecial,
                    addedPermissions = added,
                    detail = "Enabled: ${addedSpecial.joinToString(", ")}",
                    oldScore = before.score,
                    newScore = app.riskScore,
                    timestamp = now
                )

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

        val currentPackages = currentScoped.map { it.packageName }.toSet()
        for ((pkg, entry) in prevScoped) {
            if (!currentPackages.contains(pkg)) {
                changes += PermissionChange(
                    packageName = pkg,
                    appName = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                    type = ChangeType.APP_REMOVED,
                    oldScore = entry.score,
                    newScore = 0,
                    timestamp = now
                )
            }
        }

        return changes
            .sortedWith(compareBy({ priority(it.type) }, { -it.newScore }))
            .take(MAX_ITEMS)
    }

    private fun priority(type: ChangeType): Int = when (type) {
        ChangeType.SIGNER_CHANGED -> 0
        ChangeType.SPECIAL_ACCESS_GRANTED -> 1
        ChangeType.TRUST_BROKEN -> 2
        ChangeType.PERMISSIONS_ADDED -> 3
        ChangeType.NEW_APP -> 4
        ChangeType.SCORE_INCREASED -> 5
        ChangeType.POSTURE_REGRESSED -> 6
        ChangeType.PERMISSIONS_REMOVED -> 7
        ChangeType.UPDATED -> 8
        ChangeType.APP_REMOVED -> 9
    }
}
