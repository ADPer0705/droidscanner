# DroidScanner — Standalone Rootless Android Security Guardian

**Find risky apps, dangerous device configurations, fake/impersonated APKs, and silent privilege changes through public, rootless Android APIs — 100% on-device.**

Android 8.0+ (API 26+) · Kotlin + Jetpack Compose · 100% on-device · **no `INTERNET` permission** · Distributed via GitHub Releases / Obtainium (`arm64-v8a`, `armeabi-v7a`, `x86_64`, and universal APKs)

---

## What it answers

| Question | Where |
|---|---|
| **Q1 — Which apps or device settings can cause harm?** | Overview: unified Device Security Score, prioritized "Recommended actions", Device Posture summary, top risky apps, and full ranked app list |
| **Q2 — Why is each app or setting flagged?** | App Detail: plain-language "Why flagged" panel (≥3 reasons for High/Critical), traceable score breakdown, special access panel, deep APK/DEX/tracker inspection, and bounded on-device AI purpose check |
| **Q3 — What can I do right now — and how do I stay safe over time?** | One-tap **App Settings**, **Uninstall** (with guided 2-step removal for active Device Admins), direct OEM-resilient Settings shortcuts, **Pre-Install APK Scanner**, auto-invalidating **Reviewed & Trusted** workflow, **WorkManager Background Guard**, and shareable PDF/JSON reports |

---

## Capabilities

### App Security & Threat Detection (Scoring Engine v3)
- **Full consumer-Android app inventory:** label, lazy-loaded icon, package, version, install/update date, installer & initiating provenance (Play Store, Galaxy Store, Xiaomi GetApps, Oppo/Realme/OnePlus App Market, Vivo, Huawei, F-Droid, Obtainium, Aurora, Accrescent, plus **device-migration tools** like Smart Switch / Google Restore / Mi Mover so migrated apps aren't falsely flagged as sideloaded).
- **Permissions & Special Access:** declared vs. granted across 9 security domains (~380 mapped permissions), plus live-verified Accessibility Services, active Device Administrators, Notification Listeners, and AppOps-checked Overlay, Install Unknown Apps, Usage Stats, All-Files Access, VPN, NFC HCE, and MediaProjection screen capture.
- **Fast static, signing & impersonation checks on every scan:** detects debug-signed APKs, `FLAG_DEBUGGABLE`, weak signing keys, legacy `targetSdk < 23` bypassing runtime permission prompts, and **impersonation / Unicode homoglyph spoofing** of Android system components, messengers, wallets, and banking apps.
- **19 Combination Rules:** Banking Trojan (Accessibility + Overlay), Uninstall-Blocking Control (Accessibility + Device Admin), Screen Capture + Accessibility, SMS Data Stealer, Utility Context Mismatch (camera-aware for QR/mirror utilities), Hidden Surveillance, Social Graph Harvester, Sideloaded Networked Keyboard, Sideloaded Autofill, Sideloaded Default Browser/Launcher, Repackaged APK, Browser-Sideloaded Dropper Chain, NFC Relay + Accessibility, Notification OTP Exfiltration, and Stealth Background Tracker.
- **Streaming low-RAM Deep APK & DEX Inspection:** scans base + split APKs (`splitSourceDirs`) without loading APKs into RAM — detects disguised/hidden APK, DEX, or ELF payloads in `assets/`/`res/raw/`, commercial packers, ZIP anomalies, embedded ad/analytics/location-broker tracker SDKs, sensitive DEX APIs (`DexClassLoader`, `Runtime.exec`, `SmsManager`, `DevicePolicyManager.wipeData`, `MediaProjection`, `PackageInstaller`, `addJavascriptInterface`), banking overlay target lists, and hardcoded public IP endpoints.
- **Pre-Install APK Scanner (zero storage permissions):** inspect any downloaded `.apk` via Android's system file picker or "Open with / Share" **before** installing — including **developer signing-certificate mismatch warnings** if that package is already installed on your phone.
- **Bounded On-Device AI Purpose Check:** bundled MediaPipe Universal Sentence Encoder (~6 MB, offline) adds at most `+8` points on Medium+ apps and can never produce a Critical verdict alone.

### Device Configuration & Environment Posture (16 Rootless Checks)
- **Physical & Lock-Screen Protection:** secure screen lock, lock-screen sensitive notification exposure (SMS OTP privacy), screen auto-lock timeout, and "Stay awake while charging".
- **OS & Build Hygiene:** Android security patch recency, OS support lifecycle, storage encryption status, unofficial `test-keys` / `userdebug` builds, and package verifier status where exposed.
- **Network & Interception Risks:** user-installed CA certificates (showing actual certificate subjects & expiry), **Private DNS** (DNS-over-TLS off vs reputable provider vs unknown custom host), global HTTP proxy, and **active VPN / captive-portal** detection (via read-only local `ACCESS_NETWORK_STATE`).
- **Default Roles, Input Surfaces & MDM:** Default SMS, Phone, Browser, and Home Launcher provenance; enabled third-party **Keyboards (IMEs)**, **Autofill**, and **Assistant** services; active **Device Owner / Profile Owner / Work Profile** detection; and USB / Wireless Debugging.

### Standalone Daily Protection
- **Periodic Background Security Guard (`WorkManager`):** configurable schedule (`Every 12 hours`, `Daily`, or `Off`) running lightweight metadata-only checks while closed. Deep-linked alerts fire ONLY on real events: new High/Critical installs, silent permission or **special-access grants**, **developer signing-certificate changes**, invalidated trusted apps, or device-posture regressions.
- **Auto-Invalidating "Reviewed & Trusted" Workflow:** silence alerts for legitimate high-privilege apps (e.g., password managers, automation tools) or acknowledged settings (e.g., Developer Options). Trust is cryptographically bound to the app's certificate, granted permissions, and special access — if any of them change later, trust automatically clears and re-alerts you.
- **Persistent Security Timeline & Trend History:** rolling local history of installs, escalations, posture regressions, and Device Security Score trend.
- **Optional Behavioural Insights:** user-consented Usage Access using fast batch `NetworkStatsManager.querySummary`, separating **uploads (`tx`)** from **downloads (`rx`)** to spot silent background uploads without falsely flagging foreground video streaming.
- **Shareable PDF & JSON Audit Reports** generated completely on-device.

---

## Rootless Boundaries

DroidScanner audits installed packages and device configurations Android exposes to ordinary apps via public APIs. It cannot reliably prove bootloader lock, root/kernel compromise, or Play Protect status — those require hardware attestation or privileged access. Unavailable signals are marked `Not available` rather than treated as safe.

---

## Permissions DroidScanner Declares

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | Enumerate installed apps for auditing |
| `REQUEST_DELETE_PACKAGES` | Invoke Android's system uninstall dialog on Android 9+ |
| `ACCESS_NETWORK_STATE` | Read-only local transport state (detects active VPN & captive portals; cannot open sockets or send data) |
| `POST_NOTIFICATIONS` | Local security alerts (optional) |
| `PACKAGE_USAGE_STATS` | Optional 7-day background behaviour insights (only active if user grants Usage Access) |
| `RECEIVE_BOOT_COMPLETED` / `WAKE_LOCK` | Merged by AndroidX WorkManager to reschedule periodic background checks after reboot |

`android.permission.INTERNET` is **explicitly stripped** (`tools:node="remove"`), enforcing 100% offline operation at the Android OS sandbox level.

---

## Build & Release (GitHub Releases / Obtainium)

```bash
./gradlew :app:testDebugUnitTest          # Pure-JVM unit tests
./gradlew :app:assembleDebug              # Debug ABI-split + universal APKs
./gradlew :app:assembleRelease -PuseDebugSigningForRelease=true  # R8-shrunk ABI-split + universal APKs
```

Release outputs under `app/build/outputs/apk/release/`:
- `app-arm64-v8a-release.apk` — modern 64-bit consumer Android devices (Obtainium auto-matches)
- `app-armeabi-v7a-release.apk` — 32-bit budget/older consumer Android devices (Obtainium auto-matches)
- `app-x86_64-release.apk` — emulators / x86_64 devices
- `app-universal-release.apk` — manual fallback APK

Scoring methodology: see [`SCORING_RUBRIC.md`](SCORING_RUBRIC.md) — also rendered directly in-app (*Settings → How scoring works*).
