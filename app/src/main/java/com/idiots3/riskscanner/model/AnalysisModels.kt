package com.idiots3.riskscanner.model

/** Where a contribution to the final score came from. Deterministic rules stay authoritative. */
enum class ScoreSource { RULES, AI }

/** One transparent, human-readable line of the risk score breakdown. */
data class ScoreLine(
    val label: String,
    val points: Int,
    val detail: String,
    val source: ScoreSource
)

data class ScoreResult(
    val score: Int,
    val level: RiskLevel,
    val ruleScore: Int,
    val aiDelta: Int,
    val reasons: List<RiskReason>,
    val breakdown: List<ScoreLine>
)

/** Result of the on-device embedding comparison for one permission. */
enum class FitStatus(val label: String) {
    FITS("Fits purpose"),
    UNCLEAR("Unclear purpose"),
    UNEXPLAINED("Unexplained"),
    UNKNOWN("Not analyzed")
}

data class PermissionFit(
    val status: FitStatus,
    val similarity: Float,
    val evidence: String
)

/**
 * On-device purpose assessment for one app.
 *
 * IMPORTANT: cosine similarity is an embedding-space distance, NOT a probability.
 * A low score means "the app's own metadata does not explain this access" — it is
 * never, by itself, proof of malicious behaviour.
 */
data class AiAssessment(
    val purposeLabel: String?,
    val purposeSimilarity: Float,
    val permissionFit: Map<PermissionCategory, PermissionFit>,
    val unexplainedCategories: List<PermissionCategory>,
    val modelUsed: Boolean,
    val note: String? = null
)

/** Static APK, DEX, tracker, and container facts gathered lazily on Detail / Pre-Install scan. */
data class StaticChecks(
    val exportedActivities: Int = 0,
    val exportedServices: Int = 0,
    val exportedReceivers: Int = 0,
    val exportedProviders: Int = 0,
    val unprotectedExportedActivities: Int = 0,
    val unprotectedExportedServices: Int = 0,
    val unprotectedExportedReceivers: Int = 0,
    val unprotectedExportedProviders: Int = 0,
    val unprotectedComponents: List<UnprotectedComponent> = emptyList(),
    val cleartextTraffic: Boolean? = null,
    val debuggable: Boolean = false,
    val allowBackup: Boolean = false,
    val testOnly: Boolean = false,
    val targetSdk: Int = 0,
    val minSdk: Int = 0,
    val apkSizeBytes: Long = 0L,
    val splitApkCount: Int = 0,
    val nativeLibraries: List<String> = emptyList(),
    val sha256: String? = null,
    val hashError: String? = null,
    val limitedAnalysisNote: String? = null,
    val signerInfo: SignerInfo? = null,
    val hiddenPayloads: List<HiddenPayload> = emptyList(),
    val packerLibs: List<String> = emptyList(),
    val zipAnomalies: List<String> = emptyList(),
    val dexAnalysis: DexAnalysisResult? = null
) {
    val exportedComponentCount: Int
        get() = exportedActivities + exportedServices + exportedReceivers + exportedProviders

    val unprotectedExportedCount: Int
        get() = unprotectedExportedActivities +
            unprotectedExportedServices +
            unprotectedExportedReceivers +
            unprotectedExportedProviders
}

enum class ChangeType(val label: String) {
    SIGNER_CHANGED("Signing cert changed"),
    SPECIAL_ACCESS_GRANTED("Special access enabled"),
    TRUST_BROKEN("Reviewed app changed"),
    PERMISSIONS_ADDED("New permissions"),
    NEW_APP("Newly installed"),
    SCORE_INCREASED("Risk increased"),
    POSTURE_REGRESSED("Device setting changed"),
    PERMISSIONS_REMOVED("Permissions removed"),
    UPDATED("Updated"),
    APP_REMOVED("Uninstalled")
}

/** A change detected between two scans (install monitoring, permission/special-access diff, or posture regression). */
data class PermissionChange(
    val packageName: String,
    val appName: String,
    val type: ChangeType,
    val addedPermissions: List<String> = emptyList(),
    val removedPermissions: List<String> = emptyList(),
    val addedSpecialAccess: List<String> = emptyList(),
    val detail: String? = null,
    val oldScore: Int = 0,
    val newScore: Int = 0,
    val timestamp: Long = 0L
)
