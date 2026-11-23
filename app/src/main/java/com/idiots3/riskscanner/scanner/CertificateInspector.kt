package com.idiots3.riskscanner.scanner

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.idiots3.riskscanner.model.CertInfo
import com.idiots3.riskscanner.model.SignerInfo
import com.idiots3.riskscanner.model.SigningScheme
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECKey
import java.security.interfaces.RSAKey
import java.util.zip.ZipFile
import kotlin.math.min

/**
 * Extracts and parses X.509 certificate information from an installed APK's signing block
 * and detects real v1 / v2 / v3 / v3.1 signing schemes without loading the APK into RAM.
 */
class CertificateInspector(private val packageManager: PackageManager) {

    fun inspect(packageName: String, apkPath: String = ""): SignerInfo {
        return try {
            val (signatures, hasMultiple, hasPast) = loadSigningInfo(packageName)
            val certs = signatures.mapNotNull(::parseCert)
            val schemes = if (apkPath.isNotBlank()) {
                ApkSignatureSchemeDetector.detectSchemes(File(apkPath))
            } else {
                emptySet()
            }
            SignerInfo(
                certs = certs,
                signingSchemes = schemes,
                hasMultipleSigners = hasMultiple,
                hasPastSigners = hasPast
            )
        } catch (_: Throwable) {
            SignerInfo(certs = emptyList(), signingSchemes = emptySet())
        }
    }

    /**
     * Fast, zero-file-I/O signing metadata extraction from an already-loaded [PackageInfo]
     * (requested with `GET_SIGNING_CERTIFICATES` on API 28+ or `GET_SIGNATURES` on API 26–27).
     * Used during inventory scans and pre-install archive inspection.
     */
    fun inspectFromPackageInfo(packageInfo: PackageInfo, apkPath: String = ""): SignerInfo {
        return try {
            val raw = extractRawSigningData(packageInfo)
            val certs = raw.signatures.mapNotNull(::parseCert)
            val schemes = if (apkPath.isNotBlank()) {
                ApkSignatureSchemeDetector.detectSchemes(File(apkPath))
            } else {
                emptySet()
            }
            SignerInfo(
                certs = certs,
                signingSchemes = schemes,
                hasMultipleSigners = raw.hasMultipleSigners,
                hasPastSigners = raw.hasPastSigners
            )
        } catch (_: Throwable) {
            SignerInfo(certs = emptyList(), signingSchemes = emptySet())
        }
    }

    private data class RawSigningData(
        val signatures: List<Signature>,
        val hasMultipleSigners: Boolean,
        val hasPastSigners: Boolean
    )

    private fun extractRawSigningData(info: PackageInfo): RawSigningData {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            val sigs = signingInfo?.apkContentsSigners?.toList().orEmpty()
            RawSigningData(
                signatures = sigs,
                hasMultipleSigners = signingInfo?.hasMultipleSigners() == true || sigs.size > 1,
                hasPastSigners = signingInfo?.hasPastSigningCertificates() == true
            )
        } else {
            @Suppress("DEPRECATION")
            val sigs = info.signatures?.toList().orEmpty()
            RawSigningData(
                signatures = sigs,
                hasMultipleSigners = sigs.size > 1,
                hasPastSigners = false
            )
        }
    }

    private fun loadSigningInfo(packageName: String): RawSigningData {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            extractRawSigningData(info)
        } else {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            extractRawSigningData(info)
        }
    }

    fun parseCert(sig: Signature): CertInfo? {
        return try {
            val rawBytes = sig.toByteArray()
            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(rawBytes.inputStream()) as X509Certificate
            val subjectName = cert.subjectX500Principal.name
            val issuerName = cert.issuerX500Principal.name
            val keyAlg = cert.publicKey.algorithm
            val keySize = when (val key = cert.publicKey) {
                is RSAKey -> key.modulus.bitLength()
                is ECKey -> key.params.order.bitLength()
                else -> 0
            }
            CertInfo(
                subject = subjectName,
                issuer = issuerName,
                sha256Fingerprint = fingerprint(rawBytes, "SHA-256"),
                sha1Fingerprint = fingerprint(rawBytes, "SHA-1"),
                validFrom = cert.notBefore.time,
                validUntil = cert.notAfter.time,
                keyAlgorithm = keyAlg,
                keySize = keySize,
                isDebugCert = isDebugCert(subjectName),
                isSelfSigned = subjectName == issuerName
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun isDebugCert(subject: String): Boolean {
        val lower = subject.lowercase()
        return lower.contains("android debug") ||
            lower.contains("debug key") ||
            (lower.contains("o=android, c=us") && lower.contains("debug"))
    }

    private fun fingerprint(bytes: ByteArray, algo: String): String {
        val md = MessageDigest.getInstance(algo)
        return md.digest(bytes).joinToString(":") { "%02X".format(it) }
    }
}

/**
 * Pure-JVM detector for APK v1 (JAR META-INF signature block) and APK Signature Scheme
 * v2 / v3 / v3.1 inside the APK Signing Block before the ZIP Central Directory.
 */
object ApkSignatureSchemeDetector {

    private const val EOCD_MIN_LEN = 22
    private const val EOCD_MAX_SEARCH = 65_557L // 22 + max 65535 comment
    private const val APK_SIG_BLOCK_MAGIC = "APK Sig Block 42"
    private const val MAX_SIG_BLOCK_BYTES = 64L * 1024L * 1024L

    private const val ID_V2 = 0x7109871a
    private const val ID_V3 = 0xf05368c0.toInt()
    private const val ID_V3_1 = 0x1b93ad61

    fun detectSchemes(apkFile: File): Set<SigningScheme> {
        if (!apkFile.exists() || !apkFile.isFile || !apkFile.canRead()) return emptySet()
        val found = linkedSetOf<SigningScheme>()
        if (hasV1Signature(apkFile)) found += SigningScheme.V1
        found += detectApkSigBlockSchemes(apkFile)
        return found
    }

    private fun hasV1Signature(apkFile: File): Boolean = try {
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            var matched = false
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.startsWith("META-INF/", ignoreCase = true) && !name.drop(9).contains('/')) {
                    val upper = name.uppercase()
                    if (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")) {
                        matched = true
                        break
                    }
                }
            }
            matched
        }
    } catch (_: Throwable) {
        false
    }

    private fun detectApkSigBlockSchemes(apkFile: File): Set<SigningScheme> {
        val found = linkedSetOf<SigningScheme>()
        try {
            RandomAccessFile(apkFile, "r").use { raf ->
                val fileLen = raf.length()
                if (fileLen < EOCD_MIN_LEN) return emptySet()

                val searchLen = min(fileLen, EOCD_MAX_SEARCH).toInt()
                val tail = ByteArray(searchLen)
                raf.seek(fileLen - searchLen)
                raf.readFully(tail)

                val cdOffset = findCentralDirOffset(tail) ?: return emptySet()
                if (cdOffset < 32L || cdOffset > fileLen) return emptySet()

                // Read 24-byte footer immediately preceding the Central Directory:
                // [8 bytes size_of_block][16 bytes "APK Sig Block 42"]
                val footer = ByteArray(24)
                raf.seek(cdOffset - 24L)
                raf.readFully(footer)

                val magic = String(footer, 8, 16, Charsets.US_ASCII)
                if (magic != APK_SIG_BLOCK_MAGIC) return emptySet()

                val trailingSize = ByteBuffer.wrap(footer, 0, 8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .long
                if (trailingSize < 24L || trailingSize > MAX_SIG_BLOCK_BYTES) return emptySet()

                val blockStart = cdOffset - trailingSize - 8L
                if (blockStart < 0L) return emptySet()

                val sizeBuf = ByteArray(8)
                raf.seek(blockStart)
                raf.readFully(sizeBuf)
                val leadingSize = ByteBuffer.wrap(sizeBuf)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .long
                if (leadingSize != trailingSize) return emptySet()

                val pairsEnd = cdOffset - 24L
                var pos = blockStart + 8L
                val pairHeader = ByteArray(12)
                while (pos + 12L <= pairsEnd) {
                    raf.seek(pos)
                    raf.readFully(pairHeader)
                    val bb = ByteBuffer.wrap(pairHeader).order(ByteOrder.LITTLE_ENDIAN)
                    val pairLen = bb.long
                    val id = bb.int
                    if (pairLen < 4L || pos + 8L + pairLen > pairsEnd) break

                    when (id) {
                        ID_V2 -> found += SigningScheme.V2
                        ID_V3 -> found += SigningScheme.V3
                        ID_V3_1 -> found += SigningScheme.V3_1
                    }
                    pos += 8L + pairLen
                }
            }
        } catch (_: Throwable) {
            return emptySet()
        }
        return found
    }

    private fun findCentralDirOffset(tail: ByteArray): Long? {
        for (i in (tail.size - EOCD_MIN_LEN) downTo 0) {
            if (tail[i] == 0x50.toByte() &&
                tail[i + 1] == 0x4b.toByte() &&
                tail[i + 2] == 0x05.toByte() &&
                tail[i + 3] == 0x06.toByte()
            ) {
                val commentLen = ((tail[i + 21].toInt() and 0xFF) shl 8) or (tail[i + 20].toInt() and 0xFF)
                if (i + EOCD_MIN_LEN + commentLen == tail.size) {
                    val bb = ByteBuffer.wrap(tail, i + 16, 4).order(ByteOrder.LITTLE_ENDIAN)
                    return bb.int.toLong() and 0xFFFFFFFFL
                }
            }
        }
        return null
    }
}
