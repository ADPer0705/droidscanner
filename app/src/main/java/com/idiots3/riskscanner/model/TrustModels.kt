package com.idiots3.riskscanner.model

import java.security.MessageDigest

/**
 * User-reviewed trust record for an app. Trust is cryptographically and behaviourally bound
 * to the app's signing certificate, granted sensitive permissions, and enabled special access.
 * Any privilege escalation or signer change automatically invalidates trust and re-alerts.
 */
data class TrustedAppRecord(
    val packageName: String,
    val appName: String,
    val certSha256: String?,
    val grantedSensitivePerms: Set<String>,
    val enabledSpecialAccess: Set<String>,
    val trustedAt: Long,
    val note: String? = null
)

/**
 * User-acknowledged posture item (e.g., Developer options on a developer's personal device).
 * Bound to [detailFingerprint] so if the setting worsens or changes, the dismissal resets.
 */
data class DismissedPostureRecord(
    val checkId: String,
    val title: String,
    val detailFingerprint: String,
    val dismissedAt: Long
)

data class TrustState(
    val trustedApps: Map<String, TrustedAppRecord> = emptyMap(),
    val dismissedPosture: Map<String, DismissedPostureRecord> = emptyMap()
)

sealed class TrustCheckResult {
    data object Valid : TrustCheckResult()
    data class Invalidated(val reasons: List<String>) : TrustCheckResult()
}

/**
 * Pure-JVM validator ensuring "Reviewed & Trusted" items never mask silent privilege escalation.
 */
object TrustValidator {

    fun recordFor(app: AppInfo, now: Long = System.currentTimeMillis(), note: String? = null): TrustedAppRecord =
        TrustedAppRecord(
            packageName = app.packageName,
            appName = app.appName,
            certSha256 = app.signingCertSha256,
            grantedSensitivePerms = app.grantedSensitive.map { it.shortName }.toSet(),
            enabledSpecialAccess = app.specialAccess.enabledLabels.toSet(),
            trustedAt = now,
            note = note
        )

    fun validateAppTrust(app: AppInfo, record: TrustedAppRecord): TrustCheckResult {
        val reasons = mutableListOf<String>()

        if (!record.certSha256.isNullOrBlank() &&
            !app.signingCertSha256.isNullOrBlank() &&
            !record.certSha256.equals(app.signingCertSha256, ignoreCase = true)
        ) {
            reasons += "Signing certificate changed"
        }

        val currentPerms = app.grantedSensitive.map { it.shortName }.toSet()
        val addedPerms = (currentPerms - record.grantedSensitivePerms).sorted()
        if (addedPerms.isNotEmpty()) {
            reasons += "Added sensitive permissions: ${addedPerms.take(4).joinToString(", ")}"
        }

        val currentSpecial = app.specialAccess.enabledLabels.toSet()
        val addedSpecial = (currentSpecial - record.enabledSpecialAccess).sorted()
        if (addedSpecial.isNotEmpty()) {
            reasons += "Enabled special access: ${addedSpecial.joinToString(", ")}"
        }

        if (app.impersonationTarget != null) {
            reasons += "Matches impersonation warning (${app.impersonationTarget})"
        }

        return if (reasons.isEmpty()) TrustCheckResult.Valid else TrustCheckResult.Invalidated(reasons)
    }

    fun isPostureDismissalValid(check: PostureCheck, record: DismissedPostureRecord): Boolean {
        if (check.status == PostureStatus.PROTECTED) return false
        return record.detailFingerprint == check.detailFingerprint
    }

    fun hashSet(values: Set<String>): String {
        val joined = values.sorted().joinToString("|")
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(joined.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }
}
