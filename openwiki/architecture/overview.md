---
type: architecture
title: Architecture, Trust & Background Guard
description: >-
  Documents DroidScanner's two-phase scan pipeline, MainViewModel StateFlow
  orchestration, local persistence stores (SnapshotStore, TrustStore,
  AuditHistoryStore, ScannerPreferences), live install monitoring, and
  battery-friendly WorkManager background security checks.
tags: [architecture, viewmodel, scan-lifecycle, trust, workmanager, persistence]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T19:21:54.091Z
sources:
  - id: openwiki-source-a16475a44e28b7ec6c9afdbb
    resource: repo://app/src/main/java/com/idiots3/riskscanner/data/SnapshotStore.kt
  - id: openwiki-source-8fa49ea89cfa5446ef8ff685
    resource: repo://app/src/main/java/com/idiots3/riskscanner/data/TrustStore.kt
  - id: openwiki-source-e62bb484427f05c551b7c8d0
    resource: repo://app/src/main/java/com/idiots3/riskscanner/MainActivity.kt
  - id: openwiki-source-7e82cac87510888ac580ab14
    resource: repo://app/src/main/java/com/idiots3/riskscanner/model/TrustModels.kt
  - id: openwiki-source-661a09466f7f4c6158afc70a
    resource: repo://app/src/main/java/com/idiots3/riskscanner/receiver/InstallMonitorReceiver.kt
  - id: openwiki-source-305e868e2ecc7a262dd8a99e
    resource: repo://app/src/main/java/com/idiots3/riskscanner/viewmodel/MainViewModel.kt
  - id: openwiki-source-d595d3a5feabe03953509df1
    resource: repo://app/src/main/java/com/idiots3/riskscanner/worker/BackgroundSecurityWorker.kt
  - id: openwiki-source-d2ad5f03a1cd27f70568be0c
    resource: repo://app/src/main/java/com/idiots3/riskscanner/worker/SecurityScheduleManager.kt
  - id: openwiki-source-89df878e34f12c0da5e5f9b3
    resource: repo://app/src/main/res/xml/backup_rules.xml
  - id: openwiki-source-fc3957012c09f3063a6016dc
    resource: repo://app/src/main/res/xml/data_extraction_rules.xml
generated: { by: "antigravity", at: "2026-11-23T19:21:54.091Z" }
---

# Architecture, Trust & Background Guard

DroidScanner runs an entirely on-device, single-activity Jetpack Compose architecture centered on `MainViewModel`. Every foreground audit executes in two phases so the user gets immediate deterministic results while bounded on-device ML refinement streams in asynchronously, complemented by a lightweight WorkManager background guard while the app is closed.

## System Topology

```
MainActivity (edge-to-edge UI, POST_NOTIFICATIONS, InstallMonitorReceiver, deep links & APK intents)
  └── NavGraph (Overview / Apps / Device + AppDetail + Settings + Timeline + ApkScanner + ScoringRubric)
        └── MainViewModel (StateFlow<MainUiState>)
              ├── Phase 1 (Deterministic):
              │     ├── AppScanner -> KnownSafeApps + ImpersonationDetector -> RiskScorer (v3)
              │     ├── TrustStore + TrustValidator (auto-invalidates changed/escalated apps)
              │     ├── SnapshotStore + PermissionDiff (user-app-consistent diffing & signer changes)
              │     ├── DevicePostureScanner (16 checks) + AuditHistoryStore (posture regressions & trends)
              │     ├── BehaviourAnomalyScanner (opt-in Usage Access)
              │     └── RecommendationEngine -> capped unified Device Security Score
              └── Phase 2 (Streaming ML):
                    └── PurposeInferenceEngine (MediaPipe USE) -> RiskScorer -> releaseModel()

WorkManager (SecurityScheduleManager -> BackgroundSecurityWorker)
  └── Metadata-only periodic guard (12h default / 24h / Off, battery-not-low) -> deep-linked alerts
```

- **`MainActivity`** (`app/src/main/java/com/idiots3/riskscanner/MainActivity.kt`) hosts `NavGraph` with `launchMode="singleTop"`, requests optional `POST_NOTIFICATIONS` on Android 13+, registers `InstallMonitorReceiver` while open, calls `viewModel.checkForExternalChanges()` in `onResume()`, and consumes both notification deep links (`EXTRA_OPEN_PACKAGE`, `EXTRA_OPEN_TAB`) and incoming `ACTION_VIEW` / `ACTION_SEND` `application/vnd.android.package-archive` URIs for pre-install APK inspection.
- **`MainViewModel`** (`app/src/main/java/com/idiots3/riskscanner/viewmodel/MainViewModel.kt`) owns `uiState: StateFlow<MainUiState>`, initializes the WorkManager schedule from `ScannerPreferences`, orchestrates scan/AI/APK-inspection/debounce coroutines, manages user trust & posture acknowledgments, and recomputes filtered/sorted app views and recommendations.

## Two-Phase Scan Pipeline

`MainViewModel.scanApps()` cancels any active scan or AI job, clears cached lazy `staticChecks`, and runs two phases sequentially:

1. **Deterministic pass (`Dispatchers.IO`):**
   - Loads the previous snapshot (`SnapshotStore`) and local trust records (`TrustStore`).
   - Runs `AppScanner.scanAllApps(includeSystemApps)` to enumerate packages, permissions, OEM/migration provenance, OS roles, signing certificate metadata, known-safe matches, and impersonation checks, scoring each app via `RiskScorer.score(enriched, null)`.
   - Validates every trusted app with `TrustValidator.validateAppTrust(app, record)`; if an app's signing certificate changed, gained sensitive permissions or enabled special access, or matches an impersonation warning, trust is revoked automatically and recorded as a `ChangeType.TRUST_BROKEN` event.
   - Computes scan-over-scan diffs via `PermissionDiff.compute(previous, apps, now, userAppsOnly = true)` and persists the user-app snapshot so toggling system apps never floods the timeline or alerts.
   - Runs `DevicePostureScanner.scan(apps, dismissedPosture)`, detects posture regressions via `AuditHistoryStore.detectPostureRegressions()`, runs `BehaviourAnomalyScanner.scan(apps)`, builds `DeviceSummary` and `RecommendationSummary`, persists the merged timeline and score history to `AuditHistoryStore`, publishes `uiState`, and fires local alerts (`InstallAlertNotifier`) for signer changes, broken trust, newly granted special access, and High/Critical installs or escalations.
2. **Streaming on-device AI pass (`launchAiPass()`):**
   - Filters targets via `app.needsAiAnalysis()` (non-self apps holding at least one granted sensitive permission).
   - Calls `purposeEngine.prepare()` to load `purpose_archetypes.json` and initialize MediaPipe `TextEmbedderEngine`.
   - Assesses each target app, re-scores with `RiskScorer.score(withAi, assessment)`, and streams updates to `uiState` while keeping `apps` sorted by `riskScore`.
   - Calls `purposeEngine.flush()` to persist cached vectors and `purposeEngine.releaseModel()` to free native TFLite memory until the next scan, then refreshes `deviceSummary` and `recommendations`.

## Local Persistence & Backup Exclusion

All local state stays strictly on-device and is excluded from Android cloud backup and device transfer in `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml`:

- **`SnapshotStore`** (`app/src/main/java/com/idiots3/riskscanner/data/SnapshotStore.kt`): Stores a compact JSON map in `SharedPreferences` (`risk_snapshot.xml`) recording each package's `versionCode`, `score`, `level`, granted sensitive permissions, enabled special access, `signingCertSha256`, and `isSystemApp`, plus `last_scan` epoch time.
- **`TrustStore` & `TrustValidator`** (`data/TrustStore.kt`, `model/TrustModels.kt`): Atomically persists user-reviewed apps (`TrustedAppRecord`) and acknowledged posture checks (`DismissedPostureRecord`) in `filesDir/trusted_items.json`. App trust is cryptographically and behaviourally bound to `certSha256`, granted sensitive permissions, and enabled special access; posture dismissals are bound to `detailFingerprint` so worsening settings resurface automatically.
- **`AuditHistoryStore`** (`data/AuditHistoryStore.kt`): Atomically persists a rolling audit timeline (`filesDir/audit_history.json`, capped at 120 events and 30 score trend points at >= 6h intervals or score changes) plus `lastPostureStates` for regression detection.
- **`ScannerPreferences`** (`data/ScannerPreferences.kt`): Stores `includeSystemApps`, `plainLanguageMode`, `backgroundSchedule` (default `EVERY_12_HOURS`), `onboardingCompleted`, and `lastBackgroundCheckTime` in `scanner_prefs.xml`.

## Live Install Monitoring & Background Security Guard

- **Runtime package receiver:** Because Android 8+ restricts manifest-declared package broadcasts, `InstallMonitorReceiver` (`receiver/InstallMonitorReceiver.kt`) is registered dynamically while the app is open for `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REPLACED`, and `ACTION_PACKAGE_REMOVED` (ignoring intermediate `EXTRA_REPLACING == true` removals during in-place updates). `MainViewModel.onPackageChanged()` debounces rescans by `SETTLE_DELAY_MS` (`1_500 ms`).
- **External Settings / Uninstall refresh:** Launching any Settings or Uninstall flow calls `viewModel.markExternalSettingsOpened()`. On `MainActivity.onResume()`, `checkForExternalChanges()` rescans immediately when `pendingExternalRefresh` is set, or whenever the last scan exceeds `RESCAN_AGE_MS` (`60_000 ms`).
- **Periodic WorkManager Guard (`SecurityScheduleManager` & `BackgroundSecurityWorker`):** Enqueues `droidscanner_bg_guard` (`PeriodicWorkRequestBuilder` every 12h or 24h with `setRequiresBatteryNotLow(true)` and `ExistingPeriodicWorkPolicy.UPDATE`, or cancels when set to `OFF`). `BackgroundSecurityWorker.doWork()` runs metadata-only user-app, trust-validation, snapshot-diff, and device-posture checks without loading MediaPipe or DEX parsers, records events in `AuditHistoryStore`, and dispatches up to `MAX_ALERTS_PER_RUN = 4` deep-linked notifications (`InstallAlertNotifier`) only for signer changes, broken trust, new special access, High/Critical installs/escalations, or posture regressions.

## Related Pages

- [DroidScanner Quickstart & System Map](../quickstart.md)
- [App Inventory, Provenance & Special Access](../scanners/app-and-special-access.md)
- [On-Device ML Purpose Inference](../ml/purpose-inference.md)
- [UI Navigation, Remediation & Reporting](../ui/navigation-and-reporting.md)
