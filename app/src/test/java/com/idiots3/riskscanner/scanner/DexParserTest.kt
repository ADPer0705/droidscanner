package com.idiots3.riskscanner.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexParserTest {

    @Test
    fun `parses synthetic valid dex buffer for trackers, high-risk APIs, finance targets, and endpoints`() {
        val strings = listOf(
            "Lcom/appsflyer/AppsFlyerLib;",
            "Ldalvik/system/DexClassLoader;",
            "Landroid/telephony/SmsManager;",
            "sendTextMessage",
            "<init>",
            "com.paypal.android.p2pmobile",
            "com.phonepe.app",
            "http://c2.example.org/drop",
            "http://schemas.android.com/apk/res/android",
            "http://198.51.100.42:8080/ping",
            "http://192.168.1.10/local"
        )
        // types: 0 -> AppsFlyer, 1 -> DexClassLoader, 2 -> SmsManager
        val types = listOf(0, 1, 2)
        // methods: (classTypeIdx=1, nameStrIdx=4), (classTypeIdx=2, nameStrIdx=3)
        val methods = listOf(1 to 4, 2 to 3)

        val dexBytes = buildMinimalDex(strings, types, methods)
        val catalog = DexScanCatalog(
            trackers = listOf(TrackerSignature("AppsFlyer", "Analytics", "Lcom/appsflyer/")),
            financeTargets = setOf("com.paypal.android.p2pmobile", "com.phonepe.app")
        )

        val result = DexParser.scanBuffer(ByteBuffer.wrap(dexBytes), catalog)
        assertFalse(result.parseAnomaly)
        assertTrue(result.trackers.any { it.name == "AppsFlyer" && it.category == "Analytics" })
        assertTrue(result.highRiskApis.any { it.id == "dynamic_dex_load" })
        assertTrue(result.highRiskApis.any { it.id == "sms_send" })
        assertEquals(setOf("com.paypal.android.p2pmobile", "com.phonepe.app"), result.financeTargets)
        // 3 cleartext URLs in strings: c2.example.org, 198.51.100.42, 192.168.1.10 (schemas.android.com ignored)
        assertEquals(3, result.cleartextHttpCount)
        // Only public IP 198.51.100.42 reported; RFC1918 192.168.1.10 ignored
        assertEquals(setOf("198.51.100.42"), result.rawIpEndpoints)
    }

    @Test
    fun `corrupt or truncated buffers report parseAnomaly safely without throwing`() {
        val corrupt = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5))
        assertTrue(DexParser.scanBuffer(corrupt, DexScanCatalog()).parseAnomaly)

        val badMagic = ByteBuffer.wrap(ByteArray(0x80))
        assertTrue(DexParser.scanBuffer(badMagic, DexScanCatalog()).parseAnomaly)
    }

    @Test
    fun `public ipv4 filter excludes private and loopback ranges`() {
        assertFalse(DexParser.isPublicIpv4("127.0.0.1"))
        assertFalse(DexParser.isPublicIpv4("10.0.0.1"))
        assertFalse(DexParser.isPublicIpv4("192.168.0.1"))
        assertFalse(DexParser.isPublicIpv4("172.16.5.2"))
        assertTrue(DexParser.isPublicIpv4("198.51.100.42"))
        assertTrue(DexParser.isPublicIpv4("8.8.8.8"))
    }

    /**
     * Constructs a minimal valid DEX 035 byte image containing header + string_ids + type_ids +
     * method_ids + string_data payload for pure-JVM parser testing.
     */
    private fun buildMinimalDex(
        strings: List<String>,
        typeStringIndices: List<Int>,
        methods: List<Pair<Int, Int>>
    ): ByteArray {
        val headerSize = 0x70
        val strIdsOff = headerSize
        val typeIdsOff = strIdsOff + strings.size * 4
        val methodIdsOff = typeIdsOff + typeStringIndices.size * 4
        val dataStartOff = methodIdsOff + methods.size * 8

        val dataStream = ByteArrayOutputStream()
        val strDataOffsets = IntArray(strings.size)
        strings.forEachIndexed { idx, s ->
            strDataOffsets[idx] = dataStartOff + dataStream.size()
            val utf8 = s.toByteArray(Charsets.UTF_8)
            // Single-byte ULEB128 length (valid for test strings < 128 chars)
            dataStream.write(s.length)
            dataStream.write(utf8)
            dataStream.write(0)
        }
        val dataBytes = dataStream.toByteArray()
        val totalSize = dataStartOff + dataBytes.size

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        // Magic "dex\n035\0"
        buf.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
        buf.position(0x20)
        buf.putInt(totalSize) // file_size
        buf.putInt(headerSize) // header_size
        buf.putInt(0x12345678) // endian_tag
        buf.position(0x38)
        buf.putInt(strings.size)
        buf.putInt(strIdsOff)
        buf.putInt(typeStringIndices.size)
        buf.putInt(typeIdsOff)
        buf.position(0x58)
        buf.putInt(methods.size)
        buf.putInt(methodIdsOff)

        buf.position(strIdsOff)
        strDataOffsets.forEach { buf.putInt(it) }

        buf.position(typeIdsOff)
        typeStringIndices.forEach { buf.putInt(it) }

        buf.position(methodIdsOff)
        methods.forEach { (classIdx, nameIdx) ->
            buf.putShort(classIdx.toShort())
            buf.putShort(0) // proto_idx
            buf.putInt(nameIdx)
        }

        buf.position(dataStartOff)
        buf.put(dataBytes)
        return buf.array()
    }
}
