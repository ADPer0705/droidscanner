package com.idiots3.riskscanner.engine

import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.SpecialAccessInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImpersonationDetectorTest {

    private val targets = listOf(
        ImpersonationTarget(
            label = "Google Play Services",
            aliases = listOf("play services"),
            officialPackages = setOf("com.google.android.gms"),
            mustBeSystem = true
        ),
        ImpersonationTarget(
            label = "WhatsApp",
            aliases = listOf("whatsapp messenger"),
            officialPackages = setOf("com.whatsapp", "com.whatsapp.w4b"),
            mustBeSystem = false
        )
    )

    private fun app(
        name: String,
        pkg: String,
        installer: String = "Sideloaded",
        isSystem: Boolean = false
    ) = AppInfo(
        appName = name,
        packageName = pkg,
        versionName = "1.0",
        versionCode = 1L,
        installDate = 0L,
        updateDate = 0L,
        installerSource = installer,
        targetSdk = 34,
        minSdk = 26,
        isSystemApp = isSystem,
        hasLauncherActivity = true,
        androidCategory = null,
        apkPath = "",
        permissions = emptyList(),
        specialAccess = SpecialAccessInfo()
    )

    @Test
    fun `flags fake system app and unicode homoglyph spoofing`() {
        val fakeGms = app("Google Play Services", "com.evil.helper")
        val hit1 = ImpersonationDetector.detect(fakeGms, targets)
        assertNotNull(hit1)
        assertEquals("Google Play Services", hit1!!.targetLabel)

        // Cyrillic 'о' (U+043E) and zero-width space (U+200B)
        val homoglyph = app("G\u043E\u043Egle Pl\u0430y\u200B Services", "com.fake.gms")
        val hit2 = ImpersonationDetector.detect(homoglyph, targets)
        assertNotNull(hit2)
        assertEquals("Google Play Services", hit2!!.targetLabel)
    }

    @Test
    fun `flags sideloaded package typosquatting`() {
        val typo = app("My Chat", "com.whatsap", installer = "Sideloaded (APK)")
        val hit = ImpersonationDetector.detect(typo, targets)
        assertNotNull(hit)
        assertEquals("WhatsApp", hit!!.targetLabel)
    }

    @Test
    fun `official packages and system apps are never flagged`() {
        assertNull(
            ImpersonationDetector.detect(
                app("Google Play Services", "com.google.android.gms", installer = "Pre-installed (System)", isSystem = true),
                targets
            )
        )
        assertNull(
            ImpersonationDetector.detect(
                app("WhatsApp", "com.whatsapp", installer = "Google Play Store"),
                targets
            )
        )
    }
}
