package com.idiots3.riskscanner.scanner

import com.idiots3.riskscanner.model.SigningScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkSignatureSchemeDetectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `unsigned zip reports empty scheme set`() {
        val file = createZip("unsigned.apk", listOf("AndroidManifest.xml" to "manifest".toByteArray()))
        assertTrue(ApkSignatureSchemeDetector.detectSchemes(file).isEmpty())
    }

    @Test
    fun `zip with META-INF RSA entry detects v1`() {
        val file = createZip(
            "v1.apk",
            listOf(
                "AndroidManifest.xml" to "manifest".toByteArray(),
                "META-INF/CERT.RSA" to byteArrayOf(1, 2, 3, 4)
            )
        )
        assertEquals(setOf(SigningScheme.V1), ApkSignatureSchemeDetector.detectSchemes(file))
    }

    @Test
    fun `zip with apk signing block detects v2 and v3 alongside v1`() {
        val rawZip = createZip(
            "v1v2v3.apk",
            listOf(
                "classes.dex" to byteArrayOf(0x64, 0x65, 0x78),
                "META-INF/SIGNER.EC" to byteArrayOf(5, 6, 7)
            )
        ).readBytes()

        val signedBytes = injectApkSigningBlock(
            rawZip,
            listOf(
                0x7109871a to byteArrayOf(0x01, 0x02),
                0xf05368c0.toInt() to byteArrayOf(0x03, 0x04, 0x05)
            )
        )
        val signedFile = tempFolder.newFile("signed-block.apk").apply { writeBytes(signedBytes) }

        assertEquals(
            setOf(SigningScheme.V1, SigningScheme.V2, SigningScheme.V3),
            ApkSignatureSchemeDetector.detectSchemes(signedFile)
        )
    }

    @Test
    fun `nonexistent or truncated files fail safely`() {
        val missing = File(tempFolder.root, "does-not-exist.apk")
        assertTrue(ApkSignatureSchemeDetector.detectSchemes(missing).isEmpty())

        val garbage = tempFolder.newFile("corrupt.apk").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        assertTrue(ApkSignatureSchemeDetector.detectSchemes(garbage).isEmpty())
    }

    private fun createZip(name: String, entries: List<Pair<String, ByteArray>>): File {
        val file = tempFolder.newFile(name)
        ZipOutputStream(file.outputStream()).use { zos ->
            for ((entryName, bytes) in entries) {
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return file
    }

    private fun injectApkSigningBlock(zipBytes: ByteArray, pairs: List<Pair<Int, ByteArray>>): ByteArray {
        val eocdOffset = findEocd(zipBytes)
        val cdOffset = ByteBuffer.wrap(zipBytes, eocdOffset + 16, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int

        val pairsStream = ByteArrayOutputStream()
        for ((id, payload) in pairs) {
            val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            header.putLong(4L + payload.size)
            header.putInt(id)
            pairsStream.write(header.array())
            pairsStream.write(payload)
        }
        val pairBytes = pairsStream.toByteArray()
        val blockSize = pairBytes.size.toLong() + 24L

        val sigBlock = ByteArrayOutputStream()
        sigBlock.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(blockSize).array())
        sigBlock.write(pairBytes)
        sigBlock.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(blockSize).array())
        sigBlock.write("APK Sig Block 42".toByteArray(Charsets.US_ASCII))
        val sigBlockBytes = sigBlock.toByteArray()

        val updatedZip = ByteArray(zipBytes.size + sigBlockBytes.size)
        System.arraycopy(zipBytes, 0, updatedZip, 0, cdOffset)
        System.arraycopy(sigBlockBytes, 0, updatedZip, cdOffset, sigBlockBytes.size)
        System.arraycopy(zipBytes, cdOffset, updatedZip, cdOffset + sigBlockBytes.size, zipBytes.size - cdOffset)

        val newEocdOffset = eocdOffset + sigBlockBytes.size
        val newCdOffset = cdOffset + sigBlockBytes.size
        ByteBuffer.wrap(updatedZip, newEocdOffset + 16, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(newCdOffset)

        return updatedZip
    }

    private fun findEocd(bytes: ByteArray): Int {
        for (i in (bytes.size - 22) downTo 0) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4b.toByte() &&
                bytes[i + 2] == 0x05.toByte() &&
                bytes[i + 3] == 0x06.toByte()
            ) {
                return i
            }
        }
        error("EOCD not found")
    }
}
