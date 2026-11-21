package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.ChangeType
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PermissionDetail
import com.idiots3.riskscanner.model.ProtectionLevel
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.SpecialAccessInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionDiffTest {

    private fun perm(shortName: String, granted: Boolean = true) = PermissionDetail(
        name = "android.permission.$shortName",
        shortName = shortName,
        isDeclared = true,
        isGranted = granted,
        protectionLevel = ProtectionLevel.DANGEROUS,
        category = PermissionCategory.SMS,
        description = shortName
    )

    private fun app(
        pkg: String,
        name: String,
        versionCode: Long = 1L,
        score: Int = 10,
        level: RiskLevel = RiskLevel.LOW,
        grantedPerms: List<String> = emptyList()
    ) = AppInfo(
        appName = name,
        packageName = pkg,
        versionName = "$versionCode.0",
        versionCode = versionCode,
        installDate = 0L,
        updateDate = 0L,
        installerSource = "Google Play Store",
        targetSdk = 34,
        minSdk = 26,
        isSystemApp = false,
        hasLauncherActivity = true,
        androidCategory = null,
        apkPath = "",
        permissions = grantedPerms.map { perm(it, granted = true) },
        specialAccess = SpecialAccessInfo(),
        riskScore = score,
        riskLevel = level
    )

    @Test
    fun `empty or null previous snapshot returns no changes`() {
        val current = listOf(app("com.example.a", "App A"))
        assertTrue(PermissionDiff.compute(null, current, 1000L).isEmpty())
        assertTrue(PermissionDiff.compute(emptyMap(), current, 1000L).isEmpty())
    }

    @Test
    fun `detects new app, added permissions, removed permissions, score jump, and updates sorted by priority`() {
        val previous = mapOf(
            "com.example.perm_added" to SnapshotEntry(1L, 20, "Low", setOf("CAMERA")),
            "com.example.score_jump" to SnapshotEntry(1L, 20, "Low", setOf("CAMERA")),
            "com.example.perm_removed" to SnapshotEntry(1L, 30, "Medium", setOf("CAMERA", "READ_SMS")),
            "com.example.updated" to SnapshotEntry(1L, 10, "Low", emptySet()),
            "com.example.unchanged" to SnapshotEntry(1L, 10, "Low", emptySet())
        )
        val current = listOf(
            app("com.example.updated", "Updated App", versionCode = 2L, score = 10),
            app("com.example.perm_removed", "Removed App", score = 15, grantedPerms = listOf("CAMERA")),
            app("com.example.score_jump", "Score Jump App", score = 45, grantedPerms = listOf("CAMERA")),
            app("com.example.new", "New App", score = 60, grantedPerms = listOf("READ_SMS")),
            app("com.example.perm_added", "Added Perms App", score = 55, grantedPerms = listOf("CAMERA", "READ_SMS")),
            app("com.example.unchanged", "Unchanged App", versionCode = 1L, score = 10)
        )

        val changes = PermissionDiff.compute(previous, current, now = 9999L)
        assertEquals(5, changes.size)
        assertEquals(ChangeType.PERMISSIONS_ADDED, changes[0].type)
        assertEquals(listOf("READ_SMS"), changes[0].addedPermissions)
        assertEquals(ChangeType.NEW_APP, changes[1].type)
        assertEquals(ChangeType.SCORE_INCREASED, changes[2].type)
        assertEquals(ChangeType.PERMISSIONS_REMOVED, changes[3].type)
        assertEquals(listOf("READ_SMS"), changes[4 - 1].removedPermissions)
        assertEquals(ChangeType.UPDATED, changes[4].type)
    }
}
