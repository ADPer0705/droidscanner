# DroidScanner — Rootless Android Security Scanner (PS-01)

**Find risky Android apps and device-protection gaps through public, rootless Android APIs and an explainable risk score.**

Android 8.0+ (API 26+) · Kotlin + Jetpack Compose · 100% on-device · **no `INTERNET` permission**

---

## What it answers

| Question | Where |
|---|---|
| **Q1 — Which of my apps can do the most harm?** | Dashboard: device health score, risk distribution, top-5 consumers, full ranked list with search + filters |
| **Q2 — Why is each one flagged?** | App detail: "Why flagged" list (≥3 plain-language reasons for every High/Critical app), full point-by-point score breakdown, special access panel, per-permission "does it fit?" tags |
| **Q3 — What can I do right now?** | One-tap **App Settings** (revoke permissions) and **Uninstall** buttons, plus a PDF report with a "what to do now" checklist |

## Features

**Must-haves**

- **App inventory:** label, lazy-loaded icon, package, version, install/update date, installer source, target/min SDK, system-app toggle
- **Permissions:** declared vs. granted, protection level (normal / dangerous / signature / special), plain-language description for ~380 permissions (curated table for everything that matters + bulk AOSP database for coverage, `PermissionMapper` is generated and test-guarded)
- **Nine categories:** Special access, SMS, Contacts & call logs, Camera & microphone, Location, Phone & identity, Storage & media, Background & persistence, Network (+ "Other" for the rest) — per app and as a device-wide summary
- **Special access detection:** enabled accessibility services, active device admins, enabled notification listeners, plus AppOps-verified overlay / install-packages / usage-stats grants (enabled vs merely declared)
- **Device protection posture:** secure screen lock, Android patch recency, encryption status where Android exposes it, USB debugging / developer options, and enabled high-impact app access. Each gap has a plain-language recommendation and, where Android permits, a shortcut to the relevant system settings.
- **Risk score 0–100** in four bands with a transparent breakdown of every point

**Should-haves**

- **Combination rules** (9): banking trojan, SMS stealer, utility context mismatch, hidden surveillance, social-graph harvester, device admin on non-security app, sideloaded SMS, OTP exfiltration, background tracker
- **Context mismatch via on-device ML:** bundled Universal Sentence Encoder (MediaPipe Tasks Text, ~6 MB) compares the app's own metadata against 40 app-function archetypes and permission-use descriptions
- **Install monitoring:** runtime package-change receiver + snapshot diff on resume → "Changed since last scan" card and High/Critical notifications
- **Permission diff:** updates that add sensitive permissions are called out explicitly

**Stretch**

- **Static APK checks:** exported components, cleartext traffic, debuggable flag, SDK range, SHA-256 (computed lazily on the detail screen)
- **Shareable PDF report** generated with `PdfDocument` inside the app
- Hash lookup against external threat intelligence is deliberately **not** implemented — see Privacy

## Rootless boundaries

DroidScanner can audit installed packages and the device signals Android deliberately exposes to ordinary apps. It cannot reliably prove that a device is rooted, its bootloader is unlocked, Play Protect is enabled, or the OS is compromised—those claims need privileged hardware attestation, device-owner access, or a trusted remote verifier. The UI labels these limits rather than treating absence of evidence as a clean bill of health.

## ML design and roadmap

The current APK uses a bundled Universal Sentence Encoder through MediaPipe. It runs offline and compares an app's label/package/category against permission-use descriptions and curated app-purpose archetypes. It provides only a bounded, explainable context signal; it never declares malware and cannot by itself make an app Critical.

The next useful models are deliberately staged, so the project does not become a black box:

1. **Signed feature-model for APK metadata:** a compact, calibrated gradient-boosted-tree or linear model using permissions, exported-component counts, SDK age, installer provenance, special-access state, and update deltas. Keep feature contributions visible (for example with per-feature attribution) and require a curated, versioned benign/malicious dataset.
2. **Local anomaly detector:** Isolation Forest or a small autoencoder trained on each device's historical app feature vectors. It should flag unusual *changes* for review, not label malware. This is particularly useful after an update adds accessibility, overlay, SMS, or notification access.
3. **Optional cloud reputation, only with affirmative consent:** signed APK-certificate/hash reputation and verified threat-feed matches. A hash is still identifying data; this must be opt-in, transparent, and never block app use on its own.
4. **Runtime behavioural features only where Android permits:** battery/network usage, notification access, and recent install/update signals can enrich a future model, but Android sandboxing prevents a rootless scanner from inspecting another app's private traffic, process memory, or code execution.

Models should remain secondary to deterministic checks until they have measurable false-positive/false-negative performance on representative Android versions and OEMs.

## Architecture

```
app/src/main/java/com/idiots3/riskscanner/
├── scanner/      AppScanner (inventory + permissions + special access), StaticInspector (lazy APK checks)
├── engine/       ScoringConfig (single source of truth), RiskScorer (deterministic rules),
│                 PermissionMapper (permission → category), PermissionDiff
├── ml/           TextEmbedderEngine (MediaPipe USE), PurposeInferenceEngine, EmbeddingStore (vector cache)
├── data/         SnapshotStore (last scan, for diffs)
├── model/        AppInfo, AnalysisModels, DeviceSummary
├── viewmodel/    MainViewModel — rules scan first, AI refinement streams in afterwards
├── receiver/     InstallMonitorReceiver + notification helper
├── report/       PdfExporter
└── ui/           Dashboard, App List, App Detail, Scoring Rules; components/
app/src/main/assets/
├── universal_sentence_encoder.tflite   6.1 MB, runs offline
└── purpose_archetypes.json             40 app-function archetypes + permission-use descriptions
```

**Scoring:** see [`SCORING_RUBRIC.md`](SCORING_RUBRIC.md) — also rendered in-app (Dashboard → *Scoring rules*).

**Permission database:** `engine/PermissionMapper.kt` is generated — the curated table (categories,
protection levels, user-facing wording tuned for scoring) wins on conflicts, bulk AOSP entries are
added for coverage, and anything outside the nine categories maps to `Other` (visible, zero weight).
`PermissionMapperTest` pins the scoring-critical mappings. Test apps for on-device verification live
in the optional `:testkit` module (benign, hostile permission profile, never part of this APK):
`./gradlew :testkit:assembleDebug`.

**Two-phase scan:** the deterministic pass renders instantly (rules-only scores, reasons), then the
on-device AI pass refines visible apps in the background with a progress bar; embeddings are cached
on disk so re-scans are instant and the native model is released when idle to keep memory flat.

## Build

**Android Studio:** open the project, let Gradle sync, run the `app` module. `local.properties`
must point at your SDK (`sdk.dir=...`); Android Studio writes it automatically.

**Command line:**

```bash
export JAVA_HOME=/path/to/jdk17-or-21     # AGP 8.13 requires JDK 17–21
export ANDROID_HOME=/path/to/android-sdk  # needs platform 36 + build-tools 36.0.0
./gradlew :app:assembleDebug              # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest          # deterministic engine tests
```

The resulting APK is a **single universal APK** containing `arm64-v8a` + `x86_64` native libraries
(physical devices + emulators). Java 21 is recommended; Gradle 8.13 + AGP 8.13.2 + Kotlin 2.0.21.

## Permissions this app itself asks for

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | Required to enumerate installed apps for the audit (allowed for this hackathon) |
| `POST_NOTIFICATIONS` | Alerts when an installed/updated app scores High or Critical (optional) |

It declares **no `INTERNET` permission**, so the audit cannot send anything anywhere even in theory.

## Privacy & limits (stated honestly)

- Inventory, permission analysis, scoring, embeddings, hashing and the PDF report all run on-device.
- The AI purpose check sees only an app's label, package-name words and Android category — never
  file contents, messages or accounts. It is a **weak signal**: "the app's description doesn't explain
  this permission", never "this app is malware". Similarity values are not probabilities.
- The deterministic rule engine remains authoritative; the AI can add at most 8 points, only to apps
  already scoring Medium+, and can never create a Critical verdict by itself.
- No real malware is used anywhere; test apps used during development are benign APKs that merely
  request risky permission sets.
