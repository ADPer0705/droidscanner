package com.idiots3.riskscanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateHelperTest {

    @Test
    fun `obtainium add deep link targets the DroidScanner GitHub repository`() {
        assertEquals(
            "obtainium://add/https://github.com/ADPer0705/droidscanner",
            AppUpdateHelper.obtainiumAddUri()
        )
    }

    @Test
    fun `obtainium config json contains applicationId, repo url, author, and name`() {
        val json = AppUpdateHelper.obtainiumConfigJson()
        assertTrue(json.contains("\"id\":\"com.idiots3.droidscanner\""))
        assertTrue(json.contains("\"url\":\"https://github.com/ADPer0705/droidscanner\""))
        assertTrue(json.contains("\"author\":\"ADPer0705\""))
        assertTrue(json.contains("\"name\":\"DroidScanner\""))
    }

    @Test
    fun `obtainium web redirect url safely percent-encodes config json without plus spaces`() {
        val url = AppUpdateHelper.obtainiumImportWebRedirectUrl()
        assertTrue(url.startsWith("https://apps.obtainium.imranr.dev/redirect?r="))
        assertTrue(url.contains("obtainium%3A%2F%2Fapp%2F"))
        assertFalse(url.contains("+"))
    }

    @Test
    fun `recommendedAbi prefers arm64-v8a then armeabi-v7a then x86_64 then universal`() {
        assertEquals("arm64-v8a", AppUpdateHelper.recommendedAbi(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("armeabi-v7a", AppUpdateHelper.recommendedAbi(arrayOf("armeabi-v7a", "armeabi")))
        assertEquals("x86_64", AppUpdateHelper.recommendedAbi(arrayOf("x86_64", "x86")))
        assertEquals("universal", AppUpdateHelper.recommendedAbi(emptyArray()))
    }
}
