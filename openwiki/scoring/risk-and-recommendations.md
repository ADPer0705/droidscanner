---
type: architecture
title: Risk Scoring v3, Impersonation & Recommendations
description: >-
  Documents ScoringConfig v3 as the single source of truth, category saturation
  math, fast static/signing red flags, 19 combination rules, ImpersonationDetector
  homoglyph/typosquatting checks, KnownSafeApps calibration, signer-aware
  PermissionDiff, and RecommendationEngine's capped Device Security Score.
tags: [scoring, risk-engine, impersonation, permissions, diffing, recommendations]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T19:21:54.091Z
sources:
  - id: openwiki-source-803b954746c947cfd4e6e686
    resource: repo://app/src/main/java/com/idiots3/riskscanner/engine/ImpersonationDetector.kt
  - id: openwiki-source-4ac5e55e40ac74d782b7a0cd
    resource: repo://app/src/main/java/com/idiots3/riskscanner/engine/KnownSafeApps.kt
  - id: openwiki-source-ab5163c78789c047359fa681
    resource: repo://app/src/main/java/com/idiots3/riskscanner/engine/PermissionDiff.kt
  - id: openwiki-source-d426341840811fbc8f341921
    resource: repo://app/src/main/java/com/idiots3/riskscanner/engine/PermissionMapper.kt
  - id: openwiki-source-2e27674726991c840ee41c0c
    resource: repo://app/src/main/java/com/idiots3/riskscanner/engine/RecommendationEngine.kt
  - id: openwiki-source-6081837d7dbf0dfab71b4fdd
    resource: repo://app/src/main/java/com/idiots3/riskscanner/engine/RiskScorer.kt
  - id: openwiki-source-f3e048ec11ca6c72cd45dc1d
    resource: repo://app/src/main/java/com/idiots3/riskscanner/engine/ScoringConfig.kt
  - id: openwiki-source-7c877b24187f06be23499c18
    resource: repo://app/src/main/java/com/idiots3/riskscanner/model/AppInfo.kt
generated: { by: "antigravity", at: "2026-11-23T19:21:54.091Z" }
---

# Risk Scoring v3, Impersonation & Recommendations

DroidScanner's deterministic rule engine (v3) is the authoritative source for every app's `0–100` risk score, plain-language reasons, and the overall Device Security Score.

## Single Source of Truth (`ScoringConfig`)

`ScoringConfig` (`app/src/main/java/com/idiots3/riskscanner/engine/ScoringConfig.kt`, `VERSION = 3`) defines every weight, static-signal penalty, combination rule, AI guardrail, and keyword list consumed by both `RiskScorer` and the in-app `ScoringRubricScreen`.

### Risk Bands (`RiskLevel`)

| Band | Score Range | Intended Guidance |
|---|---|---|
| `LOW` | `0–25` | Standard access, no action needed |
| `MEDIUM` | `26–50` | Review granted permissions when convenient |
| `HIGH` | `51–75` | Audit special access and revoke unused grants |
| `CRITICAL` | `76–100` | Severe indicators; review and uninstall |

## Deterministic Risk Scoring (`RiskScorer`)

`RiskScorer.score(app, ai)` (`app/src/main/java/com/idiots3/riskscanner/engine/RiskScorer.kt`) short-circuits DroidScanner itself (`isSelfApp` → `0/100`) and otherwise sums auditable point contributions into `List<ScoreLine>` and `List<RiskReason>` (each tagged with `ReasonKind`: `CATEGORY`, `SPECIAL_ACCESS`, `COMBO_RULE`, `PROVENANCE`, `STATIC`, `IMPERSONATION`, `TRUSTED_CONTEXT`, `AI`):

1. **Nine-Category Saturation Scoring:**
   - Category weights: `SMS` (12), `Camera & Microphone` (10), `Contacts & Call Logs` (8), `Location` (8), `Phone & Identity` (6), `Storage & Media` (5), `Background & Persistence` (5), `Network` (2); `Other` carries `0`, and `Special Access` uses its own table.
   - Risk units per permission: granted sensitive (`1.0`), declared-only sensitive (`0.35`), granted normal (`0.25`), declared-only normal (`0.10`).
   - Category points saturate at `CATEGORY_SATURATION_UNITS = 1.5`: `round(weight * min(1.0, units / 1.5))`.
2. **Special Access Weights:**
   - Verified enabled: Accessibility (`+20`), Device Admin (`+15`), Notification Listener (`+15`), Screen Overlay (`+12`), Install Packages (`+10`), Usage Stats (`+8`), All Files Access (`+8`). Verified `KnownSafeApps` entries receive half weight on expected active privileges (Accessibility, Device Admin, Notification Listener, Install Packages) while remaining transparent in the breakdown.
   - Declared-only / unverified: Accessibility (`+6`), Screen Overlay (`+4`), Device Admin (`+3`), Install Packages (`+3`), Usage Stats (`+2`), plus untracked privileged permissions (`+4` each, capped at `+8`).
3. **Fast Static, Signing & Impersonation Red Flags (`ScoringConfig.StaticSignals`):**
   - Evaluated on every non-system app without APK file I/O: app impersonation (`+24`), debug-certificate signing (`+16`), `android:debuggable=true` (`+12`), legacy `targetSdk < 23` auto-grant bypass with granted sensitive permissions (`+12`), weak signing key (`+6`), and outdated `targetSdk` 23–28 with sensitive grants (`+4`).
4. **19 Combination Rules & Provenance:**
   - High-signal threat patterns: `hidden_spyware` (`+28`), `banking_trojan` (`+22`), `uninstall_blocker` (`+20`, active Accessibility + Device Admin on non-security app), `sms_stealer` (`+20`), `utility_mismatch` (`+18`), `sideloaded_keyboard` (`+18`, enabled sideloaded keyboard with `INTERNET`), `nfc_accessibility` (`+18`), `banking_trojan_potential` (`+16`), `screen_capture_a11y` (`+16`, enabled Accessibility + `FOREGROUND_SERVICE_MEDIA_PROJECTION`), `sideloaded_autofill` (`+15`), `sideloaded_core_role` (`+14`, sideloaded default browser or launcher), `repackaged_apk` (`+14`, sideloaded + debuggable or debug-signed), `hidden_app` (`+12`), `data_harvester` (`+12`), `sideloaded_sms` (`+12`), `dropper_chain` (`+12`), `device_admin_mismatch` (`+10`), `otp_exfiltration` (`+8`), and `background_tracking` (`+6`).
   - Non-system sideloaded apps holding granted sensitive permissions incur `SIDELOAD_PENALTY` (`+10`).
5. **False-Positive Calibration & Explainability:**
   - Pre-installed system apps and device-migrated apps are exempt from sideloading penalties.
   - Verified store/system Default SMS apps (`isDefaultSmsApp && !isSideloaded`) are exempt from `sms_stealer` and `data_harvester`.
   - Enabled keyboards (`isEnabledInputMethod`) are exempt from `hidden_app`; camera utilities (`qr scanner`, `barcode`, `mirror`) are exempt from camera triggering `utility_mismatch` while still flagging SMS/contacts/call-log/mic/location.
   - Any `HIGH` or `CRITICAL` app is guaranteed at least `MIN_REASONS_FOR_HIGH_RISK = 3` plain-language reasons by backfilling from top rule breakdown drivers if needed.

## Impersonation Detection & Known-Safe Calibration

- **`ImpersonationDetector`** (`engine/ImpersonationDetector.kt`, 38 targets in `assets/impersonation_targets.json`): Skips system apps, DroidScanner itself, and official packages (`exact` or prefix-matched). Strips zero-width characters (`\u200B-\u200F`, `\u202A-\u202E`, `\uFEFF`), applies Unicode `NFKC` normalization, and maps Cyrillic/Greek/digit homoglyphs (`HOMOGLYPHS`) to catch spoofed labels (e.g., `"Gооgle Plаy Services"`). Also detects sideloaded package typosquatting at Levenshtein edit distance `1..2` against official packages of length `>= 10`.
- **`KnownSafeApps`** (`engine/KnownSafeApps.kt`, 24 verified tools in `assets/known_safe_apps.json`): Matches legitimate high-privilege tools (screen readers, password managers, smartwatch companions, open-source stores) only when store/system provenance matches, optional `pinnedCertSha256` matches, and the APK is **never** debug-signed, debuggable, or weak-key signed.

## Permission Knowledge Base (`PermissionMapper`)

`PermissionMapper` (`engine/PermissionMapper.kt`) maps 350+ Android permissions to `PermissionCategory`, `ProtectionLevel`, and plain-language descriptions, layering a curated scoring table over an AOSP/MobSF-style database. Unknown permissions fall back via `guessCategory()` or safely to `OTHER` (zero score weight).

## Signer-Aware Scan Diffing (`PermissionDiff`)

`PermissionDiff.compute(previous, current, now, userAppsOnly = true)` (`engine/PermissionDiff.kt`) compares user-installed apps consistently against `SnapshotStore` (preventing false floods when toggling system apps) and emits up to `MAX_ITEMS = 30` changes prioritized strictly as:

`SIGNER_CHANGED` → `SPECIAL_ACCESS_GRANTED` → `TRUST_BROKEN` → `PERMISSIONS_ADDED` → `NEW_APP` → `SCORE_INCREASED` (`>= 15` pts) → `POSTURE_REGRESSED` → `PERMISSIONS_REMOVED` → `UPDATED` → `APP_REMOVED`.

## Unified Device Score & Recommendations (`RecommendationEngine`)

`RecommendationEngine.generate(apps, posture, behaviourResult)` (`engine/RecommendationEngine.kt`) excludes user-reviewed trusted apps (`isUserTrusted`), DroidScanner itself, and dismissed posture checks so intentional tools never cause permanent alert fatigue:

- **Actionable recommendations:** Aggregates impersonation warnings (`CRITICAL`), Critical/High apps (`OPEN_APP_DETAIL`), sideloaded or excessive unverified Accessibility grants, non-security active Device Admins, unrecognized Notification Listeners, non-dismissed `AT_RISK`/`REVIEW` posture checks, opted-in behaviour outliers, and browser/app-sideloaded apps—deduplicated by `id` and sorted by `RecommendationPriority.weight`.
- **Calibrated App Safety Score (`appSafetyScore`):** Deducts `-22` per Critical app, `-9` per High app, and `-1.5` per Medium app **capped at `-20` total** so typical consumer phones with 40–50 normal Play Store apps do not collapse to `0/100`.
- **Unified Device Security Score (`0–100`):**
  - Without Usage Access (`behaviourIncluded = false`): `60% App Safety + 40% Device Hardening`.
  - With Usage Access (`behaviourIncluded = true` and non-empty profiles): `50% App Safety + 35% Device Hardening + 15% Behaviour`.

## Related Pages

- [App Inventory, Provenance & Special Access](../scanners/app-and-special-access.md)
- [On-Device ML Purpose Inference](../ml/purpose-inference.md)
- [Testing, Calibration & TestKit](../testing/test-suite-and-testkit.md)
