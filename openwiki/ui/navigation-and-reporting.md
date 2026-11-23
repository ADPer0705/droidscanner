---
type: ui
title: UI Navigation, Remediation & Reporting
description: >-
  Documents DroidScanner's obsidian-emerald Material 3 theme and custom adaptive
  icon, 3-tab NavGraph plus Settings/Timeline/Pre-Install APK/Detail/Rubric
  routes, crash-safe OEM SettingsIntents, guided uninstall & trust UX, and
  on-device PDF and JSON report exporters.
tags: [compose, navigation, material3, theme, settings-intents, pdf-export, json-export]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T19:21:54.091Z
sources:
  - id: openwiki-source-256a2849e56457a5b7c82b01
    resource: repo://app/src/main/java/com/idiots3/riskscanner/navigation/NavGraph.kt
  - id: openwiki-source-60f6ea1c5a328ed9e4d2c856
    resource: repo://app/src/main/java/com/idiots3/riskscanner/report/JsonReportExporter.kt
  - id: openwiki-source-34cd7e2661a854cf3423a2f8
    resource: repo://app/src/main/java/com/idiots3/riskscanner/report/PdfExporter.kt
  - id: openwiki-source-08b800e0f6891edc86144bf9
    resource: repo://app/src/main/java/com/idiots3/riskscanner/ui/components/SettingsIntents.kt
  - id: openwiki-source-1cfb53132a6ca53930ea3cac
    resource: repo://app/src/main/java/com/idiots3/riskscanner/ui/screens/AppDetailScreen.kt
  - id: openwiki-source-2661030ed55702a0bf4d034a
    resource: repo://app/src/main/java/com/idiots3/riskscanner/ui/screens/DashboardScreen.kt
  - id: openwiki-source-98f381577b44fea099d1b4bc
    resource: repo://app/src/main/java/com/idiots3/riskscanner/ui/theme/Theme.kt
  - id: openwiki-source-cd2d8ec20b18975ecdaceb13
    resource: repo://app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
  - id: openwiki-source-356b2075123850922b8121cd
    resource: repo://app/src/main/res/xml/file_paths.xml
generated: { by: "antigravity", at: "2026-11-23T19:21:54.091Z" }
---

# UI Navigation, Remediation & Reporting

DroidScanner's UI is built with Jetpack Compose and Material 3 (`RiskAuditorTheme` in `app/src/main/java/com/idiots3/riskscanner/ui/theme/Theme.kt`). On Android 12+ (`API 31+`) it uses Material You dynamic color when enabled, falling back on Android 8–11 to a custom **Obsidian, Signal Emerald (`#34D399` / `#047857`), and Radar Cyan (`#22D3EE` / `#0E7490`)** palette aligned with DroidScanner's custom adaptive, monochrome (`ic_launcher_monochrome.xml` for Android 13+ themed icons), and legacy WebP launcher icons. Shared badges, risk rings, lazy LRU app icons (`AppIconCache` / `AppIconImage`), and formatters live in `ui/components/CommonComponents.kt`.

## Navigation & Screens (`NavGraph`)

`NavGraph` (`app/src/main/java/com/idiots3/riskscanner/navigation/NavGraph.kt`) hosts a top-level `Scaffold` with a Material 3 `NavigationBar` visible on the three primary tabs and hidden on secondary screens, plus a `LaunchedEffect` handling notification deep links (`pendingDeepLinkPackage`, `pendingDeepLinkTab`) and incoming pre-install `.apk` URIs (`pendingApkUri`):

1. **Overview (`DashboardScreen`):**
   - Renders a 3-step `QuickStartCheckupCard` for first-time setup, the unified `Device Security Score` from `RecommendationEngine` with sub-score pills (`App Safety`, `Device Hardening`, and `Behaviour` when Usage Access is active), prioritized `Recommended actions` (`RecommendationsCard`), `PreInstallScannerBannerCard`, compact `DevicePostureSummaryCard`, `RecentChangesCard` diffs, `AiProgressCard`, `TopRiskyAppsSection`, and `ReportActionsCard`.
   - Clicking any `RiskDistributionRow` card, `SideloadedWarningCard`, or `CategoryBreakdownSection` tile jumps straight into the **Apps** tab with that filter applied via `MainViewModel.applyDashboardJumpFilter()`.
2. **Apps (`AppListScreen`):**
   - Real-time search by app/package name, risk-band chips, quick filters (`Sideloaded`, `Special access`, `Hidden apps`, `Trusted`), permission category dropdown, sort selector (`Highest risk`, `Name (A–Z)`, `Recently updated`, `Most permissions`), and a persisted system-apps toggle.
3. **Device (`DeviceHubScreen`):**
   - Displays all 16 `DevicePostureCard` checks (each non-protected check has its own direct Settings button plus a fingerprint-bound Acknowledge/Dismiss action), the **Special Access Hub** grouping active and declared holders of Accessibility, Device Admin, Notification Access, Overlay, Install Unknown Apps, and All-Files Access, and the optional **Behavioural Insights** opt-in card.
4. **Secondary Routes:**
   - **App Detail (`AppDetailScreen`):** Decodes `packageName` via `Uri.decode`, triggers lazy `StaticInspector` + DEX/tracker/packer inspection, highlights `ReasonKind.COMBO_RULE` and `ReasonKind.IMPERSONATION` alerts, renders `ScoreBreakdownCard`, `PurposeFitCard`, `SpecialAccessSection`, optional `BehaviourProfileCard`, `PermissionCategoriesSection`, `StaticChecksCard`, `TrustControlCard` (Reviewed & Trusted toggle with automatic invalidation note), and `ActionButtons` (`App Settings` + `Uninstall` — showing a 2-step **Device Admin blocks uninstall** guided dialog when `deviceAdminActive` is true, or `Disable / Info` for system apps, while hiding destructive actions for `isSelfApp`).
   - **Pre-Install APK Scanner (`ApkScannerScreen`):** Launches Android's Storage Access Framework (`ActivityResultContracts.OpenDocument()`) or receives `VIEW`/`SEND` APK intents to inspect uninstalled APKs with zero storage permission, rendering verdict badges, signer-mismatch warnings, and `StaticChecksCard`.
   - **Security Timeline (`HistoryScreen`):** Shows rolling `AuditHistoryStore` events filterable by `All`, `Alerts`, `App changes`, and `Device configs`, alongside recent score trend history and a clear-timeline action.
   - **Settings & Tools (`SettingsScreen`):** Entrypoint to Pre-Install APK Scanner, Background Guard schedule (`Off`, `Every 12 hours`, `Once a day`, plus `Run check now`), Plain-Language Mode toggle, Trusted Apps / Dismissed Posture management, PDF and JSON report export buttons, and `ScoringRubricScreen`.
   - **Scoring Rules (`ScoringRubricScreen`):** Renders live weights, risk bands, 19 combo rules, static red flags, and AI guardrails directly from `ScoringConfig`.

## Crash-Safe OEM Settings Remediation (`SettingsIntents`)

`SettingsIntents` (`app/src/main/java/com/idiots3/riskscanner/ui/components/SettingsIntents.kt`) centralizes Android remediation intents using ordered fallback chains (`launchFirstAvailable`) so OEM ROM differences (Samsung One UI, Xiaomi HyperOS/MIUI, ColorOS, Vivo, Pixel) never throw `ActivityNotFoundException`:

- **Per-app:** `ACTION_APPLICATION_DETAILS_SETTINGS`, `Intent.ACTION_DELETE` (backed by `REQUEST_DELETE_PACKAGES` on Android 9+), `ACTION_MANAGE_OVERLAY_PERMISSION`, `ACTION_MANAGE_UNKNOWN_APP_SOURCES`, and `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` (API 30+ with fallbacks).
- **System-wide:** Accessibility, Notification Listener, Device Admin (`android.settings.DEVICE_ADMIN_SETTINGS` → explicit `DeviceAdminSettingsActivity` component → `ACTION_SECURITY_SETTINGS`), Usage Access, System Update, Trusted Credentials, Developer Options, Wi-Fi/Network, Private DNS, VPN, Lock Screen, Display, Default Apps, Input Method, and Autofill settings.
- Launching any external Settings or Uninstall flow invokes `MainViewModel.markExternalSettingsOpened()` so returning to DroidScanner refreshes immediately.

## On-Device PDF & JSON Reporting (`PdfExporter` & `JsonReportExporter`)

Both exporters run on `Dispatchers.IO`, write into `cacheDir/reports/`, and share exclusively via `FileProvider` (`${applicationId}.fileprovider`, scoped to `cache-path` `reports/` in `app/src/main/res/xml/file_paths.xml`) when the user initiates sharing:

- **`PdfExporter`** (`report/PdfExporter.kt`): Builds a word-wrapped A4 `PdfDocument` (`595×842`) using `Paint.breakText()` (`drawWrapped`), closing the document in `finally`. Includes the unified Device Security Score, engine version (`ScoringConfig.VERSION`), trusted & impersonation counts, all 16 Device Protection Posture checks, top priority recommendations, top 25 risky apps with provenance and top reasons, and remediation checklist.
- **`JsonReportExporter`** (`report/JsonReportExporter.kt`): Writes a structured `droidscanner-audit-<timestamp>.json` snapshot containing `engineVersion`, summary counters, full device posture check statuses/dismissal flags, and every audited app's provenance, `signingCertSha256`, debug flags, enabled special access, granted sensitive permissions, and reasons.

## Related Pages

- [Architecture, Trust & Background Guard](../architecture/overview.md)
- [Risk Scoring v3, Impersonation & Recommendations](../scoring/risk-and-recommendations.md)
- [Device Posture & Behavioural Insights](../scanners/device-posture-and-behaviour.md)
