---
type: guide
title: DroidScanner Quickstart & System Map
description: >-
  Overview of DroidScanner v1.1.1's rootless, offline-first Android security
  guardian, manifest-enforced privacy guarantees, ABI-split Gradle/Obtainium
  build commands, and task-routing map across the wiki.
tags: [quickstart, overview, privacy, gradle, android, obtainium]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T19:21:54.091Z
sources:
  - id: openwiki-source-3bfcb28142050978edf94754
    resource: repo://app/build.gradle.kts
  - id: openwiki-source-a107f16d58beac4b84f5c928
    resource: repo://app/proguard-rules.pro
  - id: openwiki-source-186e96b8d6739f3745947903
    resource: repo://app/src/main/AndroidManifest.xml
  - id: openwiki-source-89df878e34f12c0da5e5f9b3
    resource: repo://app/src/main/res/xml/backup_rules.xml
  - id: openwiki-source-fc3957012c09f3063a6016dc
    resource: repo://app/src/main/res/xml/data_extraction_rules.xml
  - id: openwiki-source-e620d7484b72a53c7fa812cd
    resource: repo://settings.gradle.kts
generated: { by: "antigravity", at: "2026-11-23T19:21:54.091Z" }
---

# DroidScanner Quickstart & System Map

**DroidScanner** (`applicationId = "com.idiots3.droidscanner"`, Kotlin source namespace `com.idiots3.riskscanner`, `versionName = "1.1.1"`, `versionCode = 3`) is a standalone, rootless, 100% on-device Android security guardian for everyday consumer phones running Android 8.0–16 (`minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`). It audits installed apps, pre-install `.apk` files, permissions, special access, OEM/migration install provenance, signing certificates, streaming DEX bytecode, and 16 device configuration checks using only public Android APIs.

## Offline-by-Manifest Privacy Guarantee

DroidScanner enforces zero-transmission operation at the Android OS sandbox level (`app/src/main/AndroidManifest.xml`):

- **Stripped `INTERNET` permission:** `android.permission.INTERNET` is explicitly removed via `tools:node="remove"` so transitive dependencies (such as MediaPipe Tasks Text) can never merge network-socket access into the APK.
- **No cloud backup or device transfer:** `android:allowBackup="false"`, and both `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` exclude `risk_snapshot.xml`, `scanner_prefs.xml`, `embedding_cache.json`, `trusted_items.json`, and `audit_history.json`.
- **Permissions declared:**
  - `QUERY_ALL_PACKAGES`: enumerates installed packages for auditing.
  - `ACCESS_NETWORK_STATE`: read-only local transport state used solely by Device Posture to detect active VPN tunnels and captive-portal sign-in Wi-Fi; grants zero ability to open sockets or transmit data.
  - `REQUEST_DELETE_PACKAGES`: enables Android 9+ (`API 28+`) in-app **Uninstall** buttons to launch Android's system uninstaller dialog.
  - `POST_NOTIFICATIONS` (optional, Android 13+): alerts when background or live checks detect signer changes, broken trust, newly granted special access, High/Critical installs, or posture regressions.
  - `PACKAGE_USAGE_STATS` (optional, user-consented in Android Settings): enables 7-day background-service and upload-ratio behavioural insights without penalizing scores if declined.

## Build, Release Splits & Verification Commands

Configured with Gradle `9.5.0`, AGP `9.3.3`, Kotlin `2.2.10`, WorkManager `2.10.1`, MediaPipe Tasks Text `0.10.35`, R8 code/resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true` with `app/proguard-rules.pro`), and ABI splits for `arm64-v8a`, `armeabi-v7a`, `x86_64`, plus a universal fallback APK (`app/build.gradle.kts`):

```bash
# Run all 11 pure-JVM unit test suites (69 tests)
./gradlew :app:testDebugUnitTest --no-daemon

# Build R8-shrunk ABI-split release APKs (debug-signed for Obtainium/local testing when requested)
./gradlew :app:assembleRelease -PuseDebugSigningForRelease=true --no-daemon

# Build debug app APK (app/build/outputs/apk/debug/)
./gradlew :app:assembleDebug --no-daemon

# Build optional benign decoy APK for real-device testing (testkit/build/outputs/apk/debug/testkit-debug.apk)
./gradlew :testkit:assembleDebug --no-daemon

# Run Android Lint & instrumented test compilation
./gradlew :app:compileDebugAndroidTestKotlin :app:lintDebug --no-daemon
```

Or open the repository in Android Studio and run the `app` configuration on a connected Android 8.0+ device or emulator.

## Wiki Task-Routing Map

| If you need to understand… | Read |
|---|---|
| Two-phase scan pipeline, `MainViewModel`, `TrustStore`, `AuditHistoryStore`, WorkManager background guard | [Architecture, Trust & Background Guard](architecture/overview.md) |
| Package inventory, OEM & migration provenance, default OS roles, 4-state AppOps special access | [App Inventory, Provenance & Special Access](scanners/app-and-special-access.md) |
| Lazy split-APK static checks, streaming `DexParser`, 66 tracker SDKs, v1–v3.1 certs, SAF Pre-Install APK Scanner | [Static APK, DEX Bytecode, Certificates & Pre-Install Scanner](scanners/static-and-certificates.md) |
| 16 device-configuration checks and optional batched `UsageStats`/`NetworkStats` upload-ratio outliers | [Device Posture & Behavioural Insights](scanners/device-posture-and-behaviour.md) |
| `ScoringConfig` v3, 19 combo rules, Unicode impersonation, `KnownSafeApps`, signer diffing, capped Device Score | [Risk Scoring v3, Impersonation & Recommendations](scoring/risk-and-recommendations.md) |
| Bundled MediaPipe Universal Sentence Encoder, bounded +8 AI delta, atomic `EmbeddingStore` | [On-Device ML Purpose Inference](ml/purpose-inference.md) |
| Custom obsidian-emerald theme/icon, Compose `NavGraph` routes, OEM `SettingsIntents`, PDF & JSON exports | [UI Navigation, Remediation & Reporting](ui/navigation-and-reporting.md) |
| All 11 JVM unit test suites (69 tests), calibration guards, and the benign `:testkit` *Torch Pro* decoy | [Testing, Calibration & TestKit](testing/test-suite-and-testkit.md) |
