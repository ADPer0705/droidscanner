---
type: testing
title: Testing, Calibration & TestKit
description: >-
  Documents DroidScanner's 11 JVM unit test suites (69 tests), scoring
  calibration, Unicode impersonation, trust auto-invalidation, synthetic DEX
  fixtures, posture evaluators, and the benign :testkit Torch Pro decoy APK.
tags: [testing, junit, calibration, testkit, verification]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T19:21:54.091Z
sources:
  - id: openwiki-source-db16e76ad5135bb0fe466995
    resource: repo://app/src/test/java/com/idiots3/riskscanner/engine/ImpersonationDetectorTest.kt
  - id: openwiki-source-2db2d79162ae4143aa67e63b
    resource: repo://app/src/test/java/com/idiots3/riskscanner/engine/PermissionMapperTest.kt
  - id: openwiki-source-348e2fa080a73b9fa4cb5b94
    resource: repo://app/src/test/java/com/idiots3/riskscanner/engine/RiskScorerTest.kt
  - id: openwiki-source-310cf90d0c54be91871e33e1
    resource: repo://app/src/test/java/com/idiots3/riskscanner/engine/ScoringCalibrationTest.kt
  - id: openwiki-source-fa1887a834a636a8be1e3912
    resource: repo://app/src/test/java/com/idiots3/riskscanner/model/TrustValidatorTest.kt
  - id: openwiki-source-4755dea930594eef56cf10d7
    resource: repo://app/src/test/java/com/idiots3/riskscanner/scanner/DevicePostureEvaluatorTest.kt
  - id: openwiki-source-3a5b1bdf1034e74c43c1a39a
    resource: repo://app/src/test/java/com/idiots3/riskscanner/scanner/DexParserTest.kt
  - id: openwiki-source-e64fc4f943cf33fd42832873
    resource: repo://testkit/build.gradle.kts
  - id: openwiki-source-e0dbba6699c5f2635aca66ea
    resource: repo://testkit/src/main/AndroidManifest.xml
  - id: openwiki-source-5b51d72ec1d3802c547bb3ae
    resource: repo://testkit/src/main/java/com/torch/pro/TestComponents.kt
generated: { by: "antigravity", at: "2026-11-23T19:21:54.091Z" }
---

# Testing, Calibration & TestKit

DroidScanner verifies its deterministic scoring engine, impersonation rules, trust invalidation logic, binary APK and DEX parsers, 16-check device posture evaluators, and statistical behavioural analyzers through 11 fast pure-JVM unit test suites (69 tests total), paired with a standalone benign decoy module (`:testkit`) for manual on-device verification.

## JVM Unit Test Suites (`./gradlew :app:testDebugUnitTest`)

| Suite | Tests | Focus & Invariants Verified |
|---|---:|---|
| `RiskScorerTest` | 22 | Low baseline, granted vs. declared-only scoring, combo patterns (`banking_trojan_potential`, `uninstall_blocker`, `sideloaded_keyboard`, `repackaged_apk`, `dropper_chain`, `nfc_accessibility`), QR-scanner camera utility exemption, `DEVICE_MIGRATION` sideload exemption, impersonation & debug-cert scoring, self-app `0/100` score, bounded AI delta (`<= 8`), `>= 3` reasons for High/Critical, and 100% point traceability in `scoreBreakdown`. |
| `ScoringCalibrationTest` | 8 | Regression guard keeping realistic app profiles in sensible bands: Play Store chat app (`MEDIUM`), camera app (`<= MEDIUM`), banking app with notification access (`< HIGH`), pre-installed default SMS (`<= MEDIUM`), sideloaded camera mod (`< CRITICAL`), invisible recorder (`HIGH`), and the *Torch Pro* decoy (`CRITICAL`). |
| `PermissionMapperTest` | 10 | Pins scoring-critical permissions (SMS, camera/mic, location, contacts/call logs, storage, persistence, special access), verifies `>= 350` mapped permissions, and ensures AOSP system/unknown permissions map to `OTHER` rather than leaking into scored categories. |
| `DevicePostureEvaluatorTest` | 5 | Tests patch age thresholds (`<= 120d`, `<= 365d`, `> 365d`, blank/invalid), OS EOL cutoffs (`>= 34`, `33`, `< 33`), user CA cert/HTTP proxy rules, `UNKNOWN` exclusion in `protectionScore`, plus Private DNS, lock-screen OTP privacy, screen timeout/stay-awake, VPN/captive Wi-Fi, networked keyboards, and OS build hygiene. |
| `RecommendationEngineTest` | 4 | Confirms 60/40 score weighting when Usage Access is disabled (zero penalty), verifies prioritized recommendations for Critical apps, non-security Device Admins, posture gaps, and behaviour anomalies, verifies the capped `-20` Medium-app penalty across 50 normal Play Store apps, and confirms trusted apps and dismissed posture checks are excluded. |
| `TrustValidatorTest` | 4 | Verifies app trust survives benign version bumps with the same certificate and privileges, auto-invalidates on `signingCertSha256` change or added sensitive permissions / special access, and resets posture dismissals when `detailFingerprint` changes or status becomes `PROTECTED`. |
| `ApkSignatureSchemeDetectorTest` | 4 | Uses `TemporaryFolder` ZIP fixtures with synthetic `APK Sig Block 42` structures to verify unsigned, `V1` (`META-INF/*.RSA|EC`), `V2`, `V3`, and corrupt/truncated file safety. |
| `ImpersonationDetectorTest` | 3 | Tests exact label impersonation, Cyrillic homoglyph + zero-width space spoofing (`"Gооgle Plаy\u200B Services"`), sideloaded package typosquatting (`com.whatsap`), and official/system exemptions. |
| `PermissionDiffTest` | 3 | Verifies empty baseline handling, `SIGNER_CHANGED`, `SPECIAL_ACCESS_GRANTED`, `PERMISSIONS_ADDED`, `NEW_APP`, `SCORE_INCREASED`, `PERMISSIONS_REMOVED`, `UPDATED`, `APP_REMOVED`, priority ordering, and `userAppsOnly = true` filtering. |
| `BehaviourAnomalyAnalyzerTest` | 3 | Verifies `< 5` app minimum baseline, high-network outliers, heavy foreground-service outliers, silent background activity without opens, silent outbound upload flags, and exemption of active foreground streaming downloads. |
| `DexParserTest` | 3 | Builds a synthetic minimal DEX `035` byte buffer in memory to verify tracker SDK matching, `DexClassLoader`/`SmsManager` API hits, finance target matching, cleartext URL counting, public IPv4 filtering (excluding RFC1918/loopback), and safe `parseAnomaly` handling on corrupt buffers. |

## Benign Test Decoy Module (`:testkit`)

`:testkit` (`testkit/build.gradle.kts`, `applicationId = "com.torch.pro"`) is a dependency-free, deliberately harmless APK that is **never** packaged into DroidScanner (`:app`).

- **Purpose:** Lets developers exercise DroidScanner's UI, special-access detection, AI purpose mismatch, and Critical-band scoring on a real phone or emulator without touching real malware.
- **Manifest profile (`testkit/src/main/AndroidManifest.xml`):** Declares `"Torch Pro"` with SMS, contacts, call log, camera, microphone, fine/background location, storage, `INTERNET`, `RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `PACKAGE_USAGE_STATS`, plus stub `BIND_ACCESSIBILITY_SERVICE`, `BIND_NOTIFICATION_LISTENER_SERVICE`, and `BIND_DEVICE_ADMIN` components (`testkit/src/main/java/com/torch/pro/TestComponents.kt`) that perform no runtime work.
- **Build:** `./gradlew :testkit:assembleDebug` produces `testkit/build/outputs/apk/debug/testkit-debug.apk`.

## Instrumented Test Target (`app/src/androidTest/`)

`ExampleInstrumentedTest.kt` verifies the installed application context matches `com.idiots3.droidscanner`. `app/build.gradle.kts` includes an explicit `constraints` entry aligning `androidx.concurrent:concurrent-futures:1.2.0` for `androidTestCompileClasspath` compatibility under AGP 9.

## Related Pages

- [DroidScanner Quickstart & System Map](../quickstart.md)
- [Risk Scoring v3, Impersonation & Recommendations](../scoring/risk-and-recommendations.md)
- [Static APK, DEX Bytecode, Certificates & Pre-Install Scanner](../scanners/static-and-certificates.md)
