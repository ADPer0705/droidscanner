# DroidScanner — Risk Scoring Rubric & Methodology

**System:** DroidScanner · **Package:** `com.idiots3.droidscanner` · **Engine version:** 3
**Execution:** Android 8.0+ (API 26+) · 100% on-device · **no `INTERNET` permission**

Every number in this document is defined once in `engine/ScoringConfig.kt` and rendered by the
in-app **How scoring works** screen, so documentation can never drift from the engine.

---

## 1. Score bands

| Band | Range | Meaning for an everyday user | Immediate action |
|---|---|---|---|
| **LOW** | 0–25 | Standard access, nothing unusual | None |
| **MEDIUM** | 26–50 | Has sensitive access worth a look | Review granted permissions |
| **HIGH** | 51–75 | Suspicious capability profile or build flags | Audit special access, revoke unused grants |
| **CRITICAL** | 76–100 | Severe indicators (trojan / spyware / impersonation patterns) | Inspect and uninstall |

---

## 2. Category weights (nine mandatory domains)

A category reaches its full weight once it holds **1.5 "risk units"**.

| Permission kind | Units |
|---|---|
| Sensitive (dangerous / special) **granted** | **1.00** |
| Sensitive **declared but not granted** | 0.35 |
| Normal permission granted | 0.25 |
| Normal permission declared only | 0.10 |

`points = weight × min(1, units / 1.5)`

| # | Category | Max points | Example permissions |
|---|---|---|---|
| 01 | Special Access | *own table, §3* | Accessibility, overlay, device admin, notification listener, install packages, usage stats, all-files |
| 02 | SMS | 12 | `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `RECEIVE_MMS` |
| 03 | Camera & Microphone | 10 | `CAMERA`, `RECORD_AUDIO` |
| 04 | Contacts & Call Logs | 8 | `READ_CONTACTS`, `READ_CALL_LOG`, `GET_ACCOUNTS` |
| 05 | Location | 8 | `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` |
| 06 | Phone & Identity | 6 | `READ_PHONE_STATE`, `CALL_PHONE`, `READ_PHONE_NUMBERS` |
| 07 | Storage & Media | 5 | `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` |
| 08 | Background & Persistence | 5 | `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| 09 | Network | 2 | `INTERNET`, `ACCESS_NETWORK_STATE`, `BLUETOOTH_CONNECT` |
| — | Other (calendar, sensors, notifications…) | 0 | Shown in the UI, not scored |

---

## 3. Special access — highest weight

| Privilege | Enabled | Declared only |
|---|---|---|
| Accessibility service (screen reading, input injection) | **+20** | +6 |
| Device administrator (active) | **+15** | +3 |
| Notification listener (OTP interception) | **+15** | — |
| Screen overlay `SYSTEM_ALERT_WINDOW` (fake logins) | **+12** | +4 |
| `REQUEST_INSTALL_PACKAGES` (second-stage payloads) | **+10** | +3 |
| `PACKAGE_USAGE_STATS` (attack timing) | **+8** | +2 |
| `MANAGE_EXTERNAL_STORAGE` (all-files access) | **+8** | — |
| Other privileged permissions (e.g. `WRITE_SECURE_SETTINGS`) | — | +4 each, max +8 |

Recognized legitimate high-privilege tools verified by official package + store provenance + clean production signing (`known_safe_apps.json`) have their special-access weight halved and false-positive mismatch combos suppressed, while never exempting debug-signed, tampered, or impersonated builds.

---

## 4. Fast static, signing & impersonation signals (v3)

Evaluated on every scan for non-system apps:

| Signal | Points | Rationale |
|---|---|---|
| Possible app impersonation | **+24** | Mimics a protected system component or popular banking/messaging app (including Unicode homoglyph spoofing or sideloaded typosquatting) |
| Signed with Android debug certificate | **+16** | Production apps are never debug-signed; indicates test or repackaged APK |
| Debuggable build (`FLAG_DEBUGGABLE`) | **+12** | Exposes app memory/data to debugging and weakens Android isolation |
| Legacy `targetSdk < 23` + sensitive grants | **+12** | Bypasses Android 6+ runtime permission prompts and auto-grants access at install |
| Weak signing key (`RSA < 2048` / `EC < 224`) | +6 | Below modern cryptographic key-strength standards |
| Older `targetSdk 23–28` + sensitive grants | +4 | Lacks modern scoped-storage and background restrictions |

---

## 5. Combination rules (added on top of categories)

| Rule | Condition | Points |
|---|---|---|
| Hidden Surveillance Pattern | Camera + microphone + location + `INTERNET` + no launcher icon | **+28** |
| Banking Trojan Pattern | Accessibility **enabled** + overlay **enabled** | **+22** |
| Uninstall-Blocking Control | Accessibility **enabled** + Device Admin **active** (non-security) | **+20** |
| SMS Data Stealer | SMS granted + `INTERNET` + `RECEIVE_BOOT_COMPLETED` (non-default SMS) | **+20** |
| Context Mismatch (utility) | Utility app holding intrusive access (camera-expected utilities like QR/mirror exempt for `CAMERA` only) | **+18** |
| NFC Relay + Accessibility | Accessibility **enabled** + NFC card emulation service | **+18** |
| Sideloaded Networked Keyboard | Enabled sideloaded keyboard + `INTERNET` | **+18** |
| Accessibility + Overlay Capability | Accessibility **enabled** + overlay declared (unverified) | **+16** |
| Screen Capture + Accessibility | Accessibility **enabled** + `FOREGROUND_SERVICE_MEDIA_PROJECTION` | **+16** |
| Sideloaded Autofill Service | Active sideloaded Autofill provider | **+15** |
| Sideloaded Default Browser / Launcher | Sideloaded default browser or home launcher | **+14** |
| Repackaged / Tampered APK Signal | Sideloaded + (`debuggable` or debug-signed) | **+14** |
| Invisible app with sensitive access | No launcher icon + sensitive grants (excluding keyboards) | +12 |
| Social Graph Harvester | Contacts + call log + SMS + `INTERNET` (non-default SMS) | +12 |
| Sideloaded SMS Access | Sideloaded + SMS granted | +12 |
| Browser/App-Sideloaded Dropper Risk | Sideloaded via another app + install-packages or accessibility | +12 |
| Device Admin on non-security app | Active device admin on non-security/MDM app | +10 |
| Notification OTP Exfiltration | Notification listener + `INTERNET` + secondary capability | +8 |
| Stealth Background Tracker | `ACCESS_BACKGROUND_LOCATION` + `INTERNET` | +6 |
| Sideloaded source | Installed outside recognized stores/migration tools + sensitive grants | +10 |

---

## 6. Unified Device Security Score

- **App Safety (60% without Behaviour; 50% with Behaviour):** starts at `100`, subtracts `-22` per Critical app, `-9` per High app, and `-1.5` per Medium app **capped at `-20`** so normal consumer devices with dozens of regular Play Store apps stay calibrated. User-marked **Reviewed & Trusted** apps are excluded until they change.
- **Device Hardening (40% without Behaviour; 35% with Behaviour):** average across all confirmed, non-dismissed device configuration checks (`Protected = 100`, `Review = 60`, `Needs attention = 0`; `Not available` excluded).
- **Optional Behaviour Insights (15% when Usage Access is granted):** penalizes active unreviewed apps exhibiting background service or upload anomalies.

---

## 7. On-device AI purpose check (bounded, never authoritative)

Bundled Universal Sentence Encoder (~6 MB, MediaPipe Tasks Text, offline):
- Adds **0 points** unless the app already scores **≥ 26** on deterministic rules;
- Adds at most **+8 points** total;
- **Can never push an app into Critical alone** (capped at 75 whenever rules score < 76);
- Similarity scores are text-overlap measurements, never probabilities or malware verdicts.

## 8. Privacy

`android.permission.INTERNET` is explicitly removed in `AndroidManifest.xml`. Read-only `ACCESS_NETWORK_STATE` is used solely to inspect local active-VPN and captive-portal state. All scanning, DEX parsing, ML inference, hashing, and PDF/JSON exports execute 100% on-device.
