package com.idiots3.riskscanner.model

/**
 * Facts about protections that belong to the device rather than to one APK.
 * A rootless app can only report signals exposed by Android's public APIs; an
 * unknown result is deliberately kept distinct from a failed check.
 */
enum class PostureStatus(val label: String) {
    PROTECTED("Protected"),
    REVIEW("Review"),
    AT_RISK("Needs attention"),
    UNKNOWN("Not available")
}

enum class PostureAction {
    SECURITY_SETTINGS,
    SYSTEM_UPDATE,
    DEVELOPER_SETTINGS,
    ACCESSIBILITY_SETTINGS,
    NOTIFICATION_SETTINGS,
    CREDENTIAL_SETTINGS,
    NETWORK_SETTINGS,
    USAGE_ACCESS_SETTINGS,
    LOCK_SCREEN_SETTINGS,
    DISPLAY_SETTINGS,
    VPN_SETTINGS,
    PRIVATE_DNS_SETTINGS,
    DEFAULT_APPS_SETTINGS,
    INPUT_METHOD_SETTINGS,
    AUTOFILL_SETTINGS,
    APP_REVIEW
}

data class PostureCheck(
    val id: String,
    val title: String,
    val status: PostureStatus,
    val detail: String,
    val recommendation: String,
    val action: PostureAction? = null,
    val details: List<String> = emptyList(),
    val isDismissed: Boolean = false
) {
    /** Stable fingerprint of the current finding so dismissing resets if the config regresses. */
    val detailFingerprint: String
        get() = "${status.name}:${detail.trim()}:${details.sorted().joinToString("|")}"
}

data class DevicePosture(val checks: List<PostureCheck>) {
    val needsAttention: Int
        get() = checks.count { it.status == PostureStatus.AT_RISK && !it.isDismissed }

    val needsReview: Int
        get() = checks.count { it.status == PostureStatus.REVIEW && !it.isDismissed }

    /**
     * Only confirmed, non-dismissed checks contribute so unavailable APIs or
     * explicitly acknowledged settings (e.g. Developer options on a developer's phone)
     * do not unfairly penalize the score.
     */
    val protectionScore: Int
        get() {
            val active = checks.filter { it.status != PostureStatus.UNKNOWN && !it.isDismissed }
            if (active.isEmpty()) return 100
            val points = active.sumOf {
                when (it.status) {
                    PostureStatus.PROTECTED -> 100
                    PostureStatus.REVIEW -> 60
                    PostureStatus.AT_RISK -> 0
                    PostureStatus.UNKNOWN -> 0
                }
            }
            return points / active.size
        }
}
