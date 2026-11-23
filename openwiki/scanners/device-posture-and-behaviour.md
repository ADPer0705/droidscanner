---
type: component
title: Device Posture & Behavioural Insights
description: >-
  Documents DroidScanner's 16 rootless device-protection checks (lock-screen OTP
  privacy, Private DNS, CA cert subjects, VPN/captive Wi-Fi, networked keyboards,
  MDM, build hygiene) and optional batched UsageStats/NetworkStats upload-ratio
  outlier analysis.
tags: [device-posture, behavioural-analysis, usage-stats, network-stats, security-checks]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T19:21:54.091Z
sources:
  - id: openwiki-source-f5224ceab5f140b2355cb946
    resource: repo://app/src/main/java/com/idiots3/riskscanner/model/DevicePosture.kt
  - id: openwiki-source-ddec8f6c843148b1bf649e43
    resource: repo://app/src/main/java/com/idiots3/riskscanner/scanner/BehaviourAnomalyScanner.kt
  - id: openwiki-source-700ca86a4cd9bc40d9eea6a4
    resource: repo://app/src/main/java/com/idiots3/riskscanner/scanner/DevicePostureScanner.kt
generated: { by: "antigravity", at: "2026-11-23T19:21:54.091Z" }
---

# Device Posture & Behavioural Insights

Beyond per-APK risk scoring, DroidScanner evaluates device-wide configuration posture across 16 rootless checks and—when the user explicitly opts in via Android Usage Access—runtime resource and upload-ratio outliers.

## 16 Rootless Device Protection Checks (`DevicePostureScanner`)

`DevicePostureScanner` (`app/src/main/java/com/idiots3/riskscanner/scanner/DevicePostureScanner.kt`) runs 16 checks on `Dispatchers.IO`, delegating pure evaluation logic to `DevicePostureEvaluator` so every rule is unit-testable without a device. Unavailable APIs return `PostureStatus.UNKNOWN`, and both `UNKNOWN` and user-acknowledged (`isDismissed`) checks are excluded from `DevicePosture.protectionScore` (`app/src/main/java/com/idiots3/riskscanner/model/DevicePosture.kt`, scoring `PROTECTED = 100`, `REVIEW = 60`, `AT_RISK = 0`). Each check exposes a `detailFingerprint` so if a dismissed configuration later regresses, the dismissal resets automatically via `TrustValidator.isPostureDismissalValid()`.

| Check ID | Signal & Source | Status Rules | Remediation Action |
|---|---|---|---|
| `screen_lock` | `KeyguardManager.isDeviceSecure` | `true` → `PROTECTED`, `false` → `AT_RISK` | `SECURITY_SETTINGS` |
| `lock_screen_privacy` | `Settings.Secure` `lock_screen_show_notifications` / `lock_screen_allow_private_notifications` | Hidden (`0`) → `PROTECTED`; private content shown (`1`) → `REVIEW` (exposes SMS OTPs on lock screen) | `LOCK_SCREEN_SETTINGS` |
| `screen_timeout` | `Settings.System.SCREEN_OFF_TIMEOUT` & `Settings.Global.STAY_ON_WHILE_PLUGGED_IN` | Stay-awake on → `REVIEW`; `>= 30m` or Never → `AT_RISK`; `> 5m` → `REVIEW`; `<= 5m` → `PROTECTED` | `DISPLAY_SETTINGS` / `DEVELOPER_SETTINGS` |
| `security_patch` | `Build.VERSION.SECURITY_PATCH` (`DevicePostureEvaluator`) | `<= 120d` → `PROTECTED`, `<= 365d` → `REVIEW`, `> 365d` → `AT_RISK` | `SYSTEM_UPDATE` |
| `os_version` | `Build.VERSION.SDK_INT` (`DevicePostureEvaluator`) | `>= 34` → `PROTECTED`, `33` → `REVIEW`, `< 33` (EOL) → `AT_RISK` | `SYSTEM_UPDATE` |
| `os_build_hygiene` | `Build.TAGS`, `Build.TYPE`, `package_verifier_enable`, `verifier_verify_adb_installs`, legacy `INSTALL_NON_MARKET_APPS` | Production `user` build with no disabled verifier flags → `PROTECTED`; `test-keys`, non-`user` build, disabled verifier, or legacy unknown sources → `AT_RISK` | `SECURITY_SETTINGS` |
| `encryption` | `DevicePolicyManager.storageEncryptionStatus` | Active / Active-per-user → `PROTECTED`, Inactive → `AT_RISK`, else `UNKNOWN` | `SECURITY_SETTINGS` |
| `adb` / `adb_wifi` / `developer_options` | `Settings.Global.ADB_ENABLED`, `adb_wifi_enabled`, `DEVELOPMENT_SETTINGS_ENABLED` | Both USB + Wi-Fi ADB → `AT_RISK`; either ADB or dev options → `REVIEW`; off → `PROTECTED` | `DEVELOPER_SETTINGS` |
| `user_ca_certs` | `KeyStore.getInstance("AndroidCAStore")` `user:` aliases + X.509 CN/O subject & expiry | `0` → `PROTECTED`, `> 0` → `AT_RISK` (lists up to 6 cert subjects + expired tags) | `CREDENTIAL_SETTINGS` |
| `private_dns` | `Settings.Global` `private_dns_mode` & `private_dns_specifier` | `opportunistic` or `hostname` matching 15 reputable DoT providers → `PROTECTED`; `off` or custom unrecognized host → `REVIEW` | `PRIVATE_DNS_SETTINGS` |
| `http_proxy` | `Settings.Global.HTTP_PROXY` / `http.proxyHost` | Blank or `:0` → `PROTECTED`, configured → `REVIEW` | `NETWORK_SETTINGS` |
| `network_transport` | Read-only `ConnectivityManager.getNetworkCapabilities` (`TRANSPORT_VPN`, `NET_CAPABILITY_CAPTIVE_PORTAL`) + installed VPN-service apps | Captive portal or active VPN tunnel → `REVIEW` (lists VPN apps); neither → `PROTECTED` | `NETWORK_SETTINGS` / `VPN_SETTINGS` |
| `default_roles` | `isDefaultSmsApp`, `isDefaultDialer`, `isDefaultBrowser`, `isDefaultLauncher` + `isSideloaded` | Any sideloaded holder → `AT_RISK` with holder details | `DEFAULT_APPS_SETTINGS` |
| `input_autofill` | Enabled third-party networked keyboards, sideloaded Autofill, sideloaded Assistant | Sideloaded networked keyboard / autofill / assistant → `AT_RISK`; store-installed networked keyboard → `REVIEW` | `INPUT_METHOD_SETTINGS` / `AUTOFILL_SETTINGS` |
| `device_management` | `DevicePolicyManager.isDeviceOwnerApp` / `isProfileOwnerApp` + `UserManager.userProfiles` | Active Device Owner or Profile Owner → `REVIEW`; none → `PROTECTED` | `DEVICE_ADMIN_SETTINGS` |
| `privileged_access` | Unreviewed (`!isUserTrusted`) active Accessibility, Notification Listener, or Device Admin count | `0` → `PROTECTED`, `> 0` → `REVIEW` | `APP_REVIEW` |

Rootless boundaries are stated explicitly in both `DevicePostureScanner` and the UI: without root or remote hardware attestation, DroidScanner never claims to verify bootloader lock state, root/kernel integrity, or Google Play Protect status.

## Optional Behavioural Insights (`BehaviourAnomalyScanner`)

`BehaviourAnomalyScanner` (`app/src/main/java/com/idiots3/riskscanner/scanner/BehaviourAnomalyScanner.kt`) uses `PACKAGE_USAGE_STATS` (disabled by default until the user grants **Usage Access** in Android Settings):

1. **Permission gate (`hasUsageAccess()`):** Checks `AppOpsManager` for DroidScanner's own UID (`Process.myUid()`). If not granted, returns `BehaviourScanResult(permissionGranted = false)` without nagging, and `RecommendationEngine` omits Behaviour from the Device Security Score weighting so declining never penalizes the user.
2. **Batched 7-day data aggregation:**
   - Aggregates `UsageStatsManager.queryUsageStats(INTERVAL_DAILY, ...)` per package (`foregroundMs`, `foregroundServiceMs` on API 29+, `lastTimeUsedMs`).
   - Uses two batched `NetworkStatsManager.querySummary()` calls (one for `TYPE_WIFI`, one for `TYPE_MOBILE`) instead of 2 × N per-UID queries, closing each `NetworkStats` cursor in `finally`, tracking `rxBytes` and `txBytes` separately, and attributing counters only to non-shared single-app UIDs to avoid misattribution.
3. **Pure-JVM Anomaly Analyzer (`BehaviourAnomalyAnalyzer.analyze`):** Requires at least `MIN_APPS_FOR_BASELINE = 5` apps, then flags:
   - **High network outlier:** `netZ >= 2.0` and `totalBytes >= 5 MB`, **exempting** active foreground download/streaming apps (`foregroundMs >= 30m` and `rxBytes / total >= 0.85`) so video/music apps do not generate false alerts.
   - **Unusual upload activity:** `txBytes >= 2 MB` while either never opened (`foregroundMs < 5s`) or an outbound upload outlier (`txZ >= 2.0` and `txBytes >= rxBytes / 2`).
   - **Heavy background service:** `fgSvcZ >= 2.0` and `foregroundServiceMs >= 30 min`.
   - **Active without being opened:** `foregroundMs < 5s` while `foregroundServiceMs >= 10 min` or `totalBytes >= 2 MB`.

## Related Pages

- [Risk Scoring v3, Impersonation & Recommendations](../scoring/risk-and-recommendations.md)
- [UI Navigation, Remediation & Reporting](../ui/navigation-and-reporting.md)
- [Testing, Calibration & TestKit](../testing/test-suite-and-testkit.md)
