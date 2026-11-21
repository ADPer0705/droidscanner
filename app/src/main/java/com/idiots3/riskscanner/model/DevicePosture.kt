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
    APP_REVIEW
}

data class PostureCheck(
    val id: String,
    val title: String,
    val status: PostureStatus,
    val detail: String,
    val recommendation: String,
    val action: PostureAction? = null
)

data class DevicePosture(val checks: List<PostureCheck>) {
    val needsAttention: Int
        get() = checks.count { it.status == PostureStatus.AT_RISK }

    val needsReview: Int
        get() = checks.count { it.status == PostureStatus.REVIEW }

    /** Only confirmed checks contribute, so unavailable APIs cannot lower the score. */
    val protectionScore: Int
        get() {
            val known = checks.filter { it.status != PostureStatus.UNKNOWN }
            if (known.isEmpty()) return 0
            val points = known.sumOf {
                when (it.status) {
                    PostureStatus.PROTECTED -> 100
                    PostureStatus.REVIEW -> 60
                    PostureStatus.AT_RISK -> 0
                    PostureStatus.UNKNOWN -> 0
                }
            }
            return points / known.size
        }
}
