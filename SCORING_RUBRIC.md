# On-Device App Risk Auditor (PS-01) — Risk Scoring Rubric & Methodology

**System:** RiskScanner · **Package:** `com.idiots3.riskscanner` · **Engine version:** 2
**Execution:** Android 8.0+ (API 26+) · 100% on-device · the app declares **no INTERNET permission**

Every number in this document is defined once in `engine/ScoringConfig.kt` and rendered by the
in-app **Scoring rules** screen, so the documentation can never drift from the engine.

---

## 1. Score bands

| Band | Range | Meaning for a non-technical user | Immediate action |
|---|---|---|---|
| **LOW** | 0–25 | Standard access, nothing unusual | None |
| **MEDIUM** | 26–50 | Has sensitive access worth a look | Review granted permissions |
| **HIGH** | 51–75 | Suspicious capability profile | Audit special access, revoke unused grants |
| **CRITICAL** | 76–100 | Severe indicators (trojan / spyware patterns) | Review and uninstall |

---

## 2. Category weights (nine mandatory domains)

A category reaches its full weight once it holds **1.5 "risk units"**.
One permission contributes:

| Permission kind | Units |
|---|---|
| Sensitive (dangerous / special) **granted** | **1.00** |
| Sensitive **declared but not granted** | 0.35 |
| Normal permission granted | 0.25 |
| Normal permission declared only | 0.10 |

`points = weight × min(1, units / 1.5)`

| # | Category | Max points | Example permissions |
|---|---|---|---|
| 01 | Special Access | *own table, §3* | Accessibility, overlay, device admin, notification listener, install packages, usage stats |
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

Special access is granted through separate settings screens and is what banking trojans abuse.
"Enabled" is verified live (`AppOpsManager`, `Settings.Secure`, `AccessibilityManager`,
`NotificationManager`, `DevicePolicyManager`); "declared" only means the manifest asked for it.

| Privilege | Enabled | Declared only |
|---|---|---|
| Accessibility service (screen reading, input injection) | **+20** | +6 |
| Device administrator (active) | **+15** | — |
| Notification listener (OTP interception) | **+15** | — |
| Screen overlay `SYSTEM_ALERT_WINDOW` (fake logins) | **+12** | +4 |
| `REQUEST_INSTALL_PACKAGES` (second-stage payloads) | **+10** | +3 |
| `PACKAGE_USAGE_STATS` (attack timing) | **+8** | +2 |
| Other privileged permissions (e.g. `WRITE_SECURE_SETTINGS`) | — | +4 each, max +8 |

---

## 4. Combination rules (added on top of categories)

| Rule | Condition | Points | Threat model |
|---|---|---|---|
| Banking Trojan Pattern | Accessibility **enabled** + overlay **enabled** | **+22** | Anatsa / Teabot: fake login injected over the real bank app |
| SMS Data Stealer | SMS read/receive/send **granted** + `INTERNET` + `RECEIVE_BOOT_COMPLETED` | **+20** | SpyNote / Joker: OTP interception that survives reboot |
| Context Mismatch (utility) | Torch/calculator/wallpaper-class app holding SMS, contacts, call log, camera, microphone or location | **+18** | Utility app with no functional need for intrusive access |
| Hidden Surveillance | Camera + microphone + location granted + `INTERNET` **and no launcher icon** | **+28** | Spyware capability with no visible UI |
| Invisible app with sensitive access | No launcher icon while holding sensitive granted permissions | +12 | Background-only apps (keyboards/widgets) are fine — plus sensitive access they are not |
| Social Graph Harvester | Contacts + call log + SMS granted + `INTERNET` | +12 | Blackmail / predatory-loan data extraction |
| Device Admin on non-security app | Active device admin, app is not a security/MDM tool | +10 | Persistence: blocks uninstall |
| Sideloaded SMS Access | Sideloaded + SMS access | +12 | Bypasses Play SMS policy |
| Notification OTP Exfiltration | Notification listener enabled + `INTERNET` + (sideloaded, hidden, accessibility or SMS access) | +8 | Reads banking OTP notifications |
| Stealth Background Tracker | `ACCESS_BACKGROUND_LOCATION` granted + `INTERNET` | +6 | 24/7 location tracking |
| Sideloaded source | Installed outside app stores **and** holds sensitive granted permissions | +10 | No Play Protect screening |

---

## 5. On-device AI purpose check (bounded, never authoritative)

A bundled Universal Sentence Encoder (≈6 MB, MediaPipe Tasks Text, fully offline) compares each
app's own metadata (label, package-name words, Android category) against 40 curated app-function
archetypes and permission-use descriptions. Per sensitive permission it produces
**Fits purpose / Unclear / Unexplained**.

Scoring rules for the AI signal:

- adds **0 points** unless the app already scores **≥ 26** on the deterministic rules;
- 1 unexplained category → +2 (+3 if it is SMS or contacts/call log) · 2 → +4 · 3 → +6 · 4+ → **+8 max**;
- **can never push an app into Critical by itself** (total is capped at 75 whenever the rule score is below 76);
- always shown as its own line item labelled `AI` in the score breakdown.

Interpretation rules the UI follows:

1. A similarity value (e.g. `0.42`) is a text-overlap score, **never a probability**.
2. "Unexplained" means the app's metadata does not explain the access — it is **not** a malware verdict.
3. The deterministic rule engine is authoritative; the AI can only add a small, labelled signal.

---

## 6. Explainability (Q1 / Q2 / Q3)

- **Q1 — which apps can do the most harm:** ranked list + device health score + risk distribution.
- **Q2 — why is it flagged:** every High/Critical app shows **at least 3 plain-language reasons**
  plus a full point-by-point breakdown ("what gave this app its score").
- **Q3 — what can I do right now:** one-tap *App Settings* (revoke permissions) and *Uninstall*,
  plus a checklist in the PDF report.

## 7. Privacy

No `INTERNET` permission. Inventory, scoring, the ML model, SHA-256 hashes and the PDF report all
run on the device. Hash lookup against external threat intelligence is intentionally absent —
"offline" is enforced by the manifest, not by a setting.
