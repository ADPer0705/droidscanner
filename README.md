# RiskScanner — On-Device App Risk Auditor (PS-01)

**Find suspicious Android apps through their permissions, special access and an explainable risk score.**

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
- **Permissions:** declared vs. granted, protection level (normal / dangerous / signature / special), plain-language description for 80+ permissions
- **Nine categories:** Special access, SMS, Contacts & call logs, Camera & microphone, Location, Phone & identity, Storage & media, Background & persistence, Network (+ "Other" for the rest) — per app and as a device-wide summary
- **Special access detection:** enabled accessibility services, active device admins, enabled notification listeners, plus AppOps-verified overlay / install-packages / usage-stats grants (enabled vs merely declared)
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
