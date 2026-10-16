package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.ProtectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the permission knowledge base.
 *
 * The remote bulk-database commit once replaced the curated table and silently dropped
 * every runtime permission the score depends on (SMS, camera, contacts, location, ...).
 * These assertions make that class of regression impossible to merge unnoticed.
 */
class PermissionMapperTest {

    private fun assertMapping(
        permission: String,
        category: PermissionCategory,
        level: ProtectionLevel
    ) {
        val info = PermissionMapper.getPermissionInfo(permission)
        assertTrue("$permission missing from the permission database", info != null)
        assertEquals("$permission category", category, info!!.category)
        assertEquals("$permission protection level", level, info.protectionLevel)
        assertTrue("$permission should have a description", info.description.isNotBlank())
    }

    @Test
    fun `sms permissions stay mapped to the SMS category`() {
        assertMapping("android.permission.READ_SMS", PermissionCategory.SMS, ProtectionLevel.DANGEROUS)
        assertMapping("android.permission.RECEIVE_SMS", PermissionCategory.SMS, ProtectionLevel.DANGEROUS)
        assertMapping("android.permission.SEND_SMS", PermissionCategory.SMS, ProtectionLevel.DANGEROUS)
    }

    @Test
    fun `camera microphone and location stay mapped`() {
        assertMapping("android.permission.CAMERA", PermissionCategory.CAMERA_MICROPHONE, ProtectionLevel.DANGEROUS)
        assertMapping("android.permission.RECORD_AUDIO", PermissionCategory.CAMERA_MICROPHONE, ProtectionLevel.DANGEROUS)
        assertMapping("android.permission.ACCESS_FINE_LOCATION", PermissionCategory.LOCATION, ProtectionLevel.DANGEROUS)
        assertMapping("android.permission.ACCESS_BACKGROUND_LOCATION", PermissionCategory.LOCATION, ProtectionLevel.DANGEROUS)
    }

    @Test
    fun `contacts call logs and identity stay mapped`() {
        assertMapping("android.permission.READ_CONTACTS", PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS)
        assertMapping("android.permission.READ_CALL_LOG", PermissionCategory.CONTACTS_CALL_LOGS, ProtectionLevel.DANGEROUS)
        assertMapping("android.permission.READ_PHONE_STATE", PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS)
        assertMapping("android.permission.CALL_PHONE", PermissionCategory.PHONE_IDENTITY, ProtectionLevel.DANGEROUS)
    }

    @Test
    fun `storage media and persistence stay mapped`() {
        assertMapping("android.permission.READ_MEDIA_IMAGES", PermissionCategory.STORAGE_MEDIA, ProtectionLevel.DANGEROUS)
        assertMapping("android.permission.MANAGE_EXTERNAL_STORAGE", PermissionCategory.STORAGE_MEDIA, ProtectionLevel.SPECIAL)
        assertMapping("android.permission.RECEIVE_BOOT_COMPLETED", PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL)
        assertMapping("android.permission.FOREGROUND_SERVICE", PermissionCategory.BACKGROUND_PERSISTENCE, ProtectionLevel.NORMAL)
    }

    @Test
    fun `special access permissions keep the highest-weight mapping`() {
        assertMapping("android.permission.SYSTEM_ALERT_WINDOW", PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL)
        assertMapping("android.permission.REQUEST_INSTALL_PACKAGES", PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL)
        assertMapping("android.permission.PACKAGE_USAGE_STATS", PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL)
        assertMapping("android.permission.WRITE_SECURE_SETTINGS", PermissionCategory.SPECIAL_ACCESS, ProtectionLevel.SPECIAL)
    }

    @Test
    fun `unscored housekeeping permissions stay in OTHER`() {
        assertMapping("android.permission.POST_NOTIFICATIONS", PermissionCategory.OTHER, ProtectionLevel.NORMAL)
        assertMapping("android.permission.QUERY_ALL_PACKAGES", PermissionCategory.OTHER, ProtectionLevel.NORMAL)
    }

    @Test
    fun `network stays low weight`() {
        assertMapping("android.permission.INTERNET", PermissionCategory.NETWORK, ProtectionLevel.NORMAL)
        assertMapping("android.permission.ACCESS_NETWORK_STATE", PermissionCategory.NETWORK, ProtectionLevel.NORMAL)
    }

    @Test
    fun `aosp system permissions do not leak into scored categories`() {
        // REBOOT must not be swept into Background & Persistence by the "BOOT" substring.
        val reboot = PermissionMapper.getPermissionInfo("android.permission.REBOOT")
        assertTrue("REBOOT should be present for coverage", reboot != null)
        assertEquals(PermissionCategory.OTHER, reboot!!.category)
    }

    @Test
    fun `unknown permissions fall back to OTHER not NETWORK`() {
        val unknown = "android.permission.TOTALLY_MADE_UP_PERMISSION"
        assertEquals(PermissionCategory.OTHER, PermissionMapper.guessCategory(unknown))
        assertEquals(PermissionCategory.OTHER, PermissionMapper.getCategory(unknown))
        assertEquals("Permission: TOTALLY_MADE_UP_PERMISSION", PermissionMapper.getDescription(unknown))
        assertEquals(ProtectionLevel.NORMAL, PermissionMapper.getProtectionLevel(unknown))
    }

    @Test
    fun `database keeps broad coverage`() {
        assertTrue(
            "expected at least 350 mapped permissions, found ${PermissionMapper.mappedPermissionCount()}",
            PermissionMapper.mappedPermissionCount() >= 350
        )
        assertEquals(ScoringConfig.VERSION, ScoringConfig.VERSION) // sanity: config object is reachable
    }
}
