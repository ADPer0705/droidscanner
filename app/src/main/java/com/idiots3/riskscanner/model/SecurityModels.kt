package com.idiots3.riskscanner.model

/**
 * APK signing schemes detected from META-INF entries (v1) and the APK Signing Block (v2/v3/v3.1).
 */
enum class SigningScheme(val label: String) {
    V1("v1 (JAR)"),
    V2("v2 (APK Sig Block)"),
    V3("v3 (Key Rotation)"),
    V3_1("v3.1 (Key Rotation)"),
    UNKNOWN("Unknown")
}

/**
 * X.509 signing certificate summary extracted from PackageManager signing metadata.
 */
data class CertInfo(
    val subject: String,
    val issuer: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val validFrom: Long,
    val validUntil: Long,
    val keyAlgorithm: String,
    val keySize: Int,
    val isDebugCert: Boolean,
    val isSelfSigned: Boolean
) {
    val isExpired: Boolean get() = validUntil > 0L && validUntil < System.currentTimeMillis()

    val isWeakKey: Boolean
        get() = (keyAlgorithm.equals("RSA", ignoreCase = true) && keySize in 1..2047) ||
            (keyAlgorithm.equals("EC", ignoreCase = true) && keySize in 1..223)
}

/**
 * Signing metadata for an installed package.
 */
data class SignerInfo(
    val certs: List<CertInfo>,
    val signingSchemes: Set<SigningScheme> = emptySet(),
    val hasMultipleSigners: Boolean = false,
    val hasPastSigners: Boolean = false
) {
    val primaryCert: CertInfo? get() = certs.firstOrNull()
    val hasDebugCert: Boolean get() = certs.any { it.isDebugCert }
    val hasExpiredCert: Boolean get() = certs.any { it.isExpired }
    val hasWeakKey: Boolean get() = certs.any { it.isWeakKey }

    val isV1Only: Boolean
        get() = signingSchemes.isNotEmpty() &&
            signingSchemes.contains(SigningScheme.V1) &&
            !signingSchemes.contains(SigningScheme.V2) &&
            !signingSchemes.contains(SigningScheme.V3) &&
            !signingSchemes.contains(SigningScheme.V3_1)

    val schemeSummary: String
        get() = if (signingSchemes.isEmpty()) SigningScheme.UNKNOWN.label
        else signingSchemes.sortedBy { it.ordinal }.joinToString(" + ") { it.label }
}

/**
 * Exported component that declares no permission guard (potential IPC attack surface).
 */
data class UnprotectedComponent(
    val kind: String,
    val shortName: String
)

/**
 * Embedded ad, analytics, profiling, or location-broker SDK detected from DEX class prefixes.
 */
data class DetectedTracker(
    val name: String,
    val category: String
)

/**
 * Sensitive Android framework or execution API referenced in DEX method tables.
 */
data class HighRiskApiHit(
    val id: String,
    val name: String,
    val category: String,
    val plainExplanation: String
)

/**
 * Executable archive or binary hidden inside APK assets/ or res/raw/ (common dropper/packer signal).
 */
data class HiddenPayload(
    val entryPath: String,
    val kind: String
)

/**
 * Results of the lightweight, memory-mapped DEX inspection.
 */
data class DexAnalysisResult(
    val dexFileCount: Int = 0,
    val detectedTrackers: List<DetectedTracker> = emptyList(),
    val highRiskApis: List<HighRiskApiHit> = emptyList(),
    val financeTargetMatches: List<String> = emptyList(),
    val cleartextHttpCount: Int = 0,
    val rawIpEndpoints: List<String> = emptyList(),
    val parseAnomaly: Boolean = false,
    val skippedLargeDexCount: Int = 0
)

/**
 * Verdict for an APK file inspected before installation via Storage Access Framework.
 */
enum class PreInstallVerdict(val label: String) {
    LOOKS_SAFE("Looks standard"),
    CAUTION("Review before installing"),
    HIGH_RISK("High risk — proceed with caution"),
    DO_NOT_INSTALL("Strong warning — do not install")
}

data class PreInstallScanResult(
    val fileName: String,
    val fileSizeBytes: Long,
    val appInfo: AppInfo?,
    val staticChecks: StaticChecks,
    val verdict: PreInstallVerdict,
    val summaryMessage: String,
    val highlights: List<String>,
    val installedPackageMatches: Boolean = false,
    val signerMismatchWithInstalled: Boolean = false,
    val installedVersionName: String? = null,
    val error: String? = null
)
