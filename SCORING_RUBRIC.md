# On-Device App Risk Auditor (PS-01) — Risk Scoring Rubric & Methodology

**System Name:** ShinoDroid Risk Auditor  
**Engine Version:** 1.0 (On-Device Static Permission & Behavioral Correlator)  
**Execution Target:** Android 8.0+ (API 26–35) | 100% On-Device & Zero Network Egress  

---

## 1. Overview & Scoring Scale

Every audited application is evaluated along a normalized risk spectrum from **0 to 100**. The final score maps into four triage bands designed for non-technical users:

| Risk Level | Score Range | Badge Color | User Meaning & Recommended Immediate Action |
| :--- | :---: | :---: | :--- |
| **LOW** | 0 – 25 | `#00E676` (Green) | Standard application permissions. No action needed. |
| **MEDIUM** | 26 – 50 | `#FFEA00` (Yellow) | Moderate capability access. Review granted runtime permissions. |
| **HIGH** | 51 – 75 | `#FF9100` (Orange) | Suspicious or expansive privilege profile. Audit special access and revoke unused grants. |
| **CRITICAL** | 76 – 100 | `#FF1744` (Red) | Severe threat indicators or banking trojan / spyware heuristics. **Recommend immediate uninstall**. |

---

## 2. Nine Base Permission Categories & Baseline Weights

Base scores scale according to declared/granted permissions within the 9 mandatory categories, with highest weight reserved for Special Access (the primary vector abused by Android banking malware):

| # | Category | Baseline Weight | Critical Indicators & Exemplar Permissions | Threat Rationale |
|---|---|:---:|---|---|
| **01** | **Special Access** | **25 pts** | `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `PACKAGE_USAGE_STATS`, Accessibility Service, Device Admin, Notification Listener | Fake login overlays, credential phish, uninstall blocking, silent app drops, notification OTP theft. |
| **02** | **SMS** | **15 pts** | `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` | 2FA / Banking OTP theft, transaction hijacking, premium toll SMS fraud. |
| **03** | **Contacts & Call Logs**| **12 pts** | `READ_CONTACTS`, `WRITE_CONTACTS`, `READ_CALL_LOG`, `WRITE_CALL_LOG`, `GET_ACCOUNTS` | Social graph harvesting, extortion, identity theft, unauthorized address book dumps. |
| **04** | **Camera & Microphone** | **14 pts** | `CAMERA`, `RECORD_AUDIO` | Covert acoustic surveillance, non-consensual photography, ambient room recording. |
| **05** | **Location** | **12 pts** | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` | Geofence tracking, stalking, movement pattern profiling. |
| **06** | **Phone & Identity** | **10 pts** | `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `CALL_PHONE`, `PROCESS_OUTGOING_CALLS` | Persistent hardware fingerprinting (IMEI, IMSI, SIM serial), call redirection fraud. |
| **07** | **Storage & Media** | **8 pts** | `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES` | Document harvesting, private media exfiltration, ransomware file encryption. |
| **08** | **Background & Persistence**| **8 pts** | `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Surviving OS reboots, continuous background beaconing without active UI. |
| **09** | **Network** | **3 pts** | `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `BLUETOOTH_CONNECT` | Exfiltration channel; benign alone, force multiplier in combinations. |

*Per-category calculation:*  
$$\text{Score}_{\text{Cat}} = \min\left(W_{\text{Cat}}, \frac{W_{\text{Cat}} \times \min(3, N_{\text{dangerous}})}{3} + 2 \times N_{\text{granted\_dangerous}}\right)$$

---

## 3. Special Access Privileges (Explicit System Grants)

Special access permissions are verified against OS registries (`AccessibilityManager`, `DevicePolicyManager`, `Settings.Secure`):

| Privilege | Detection Mechanism | Weight Penalty | Plain-Language Justification |
|---|---|:---:|---|
| **Active Accessibility Service** | `AccessibilityManager.getEnabledAccessibilityServiceList` | **+20** | Can read full screen content, capture banking credentials, and simulate touches. |
| **Active Device Administrator** | `DevicePolicyManager.getActiveAdmins` | **+18** | Can lock screen, wipe storage, and prevent uninstallation. |
| **Notification Listener** | `Settings.Secure.enabled_notification_listeners` | **+15** | Reads all incoming push notifications, bypassing SMS privacy for 2FA OTP codes. |
| **Screen Overlay (`SYSTEM_ALERT_WINDOW`)** | `Settings.canDrawOverlays` / Manifest | **+12** | Renders invisible or deceptive overlays above legitimate banking/crypto apps. |
| **Install Packages (`REQUEST_INSTALL_PACKAGES`)**| Manifest / AppOps | **+10** | Can silently download and prompt installation of secondary malware payloads. |
| **Usage Stats (`PACKAGE_USAGE_STATS`)** | AppOps Manager | **+8** | Monitors foreground app launches to time overlay attacks when banking apps open. |

---

## 4. Multi-Vector Combination Rules (MobSF / Threat Intel Correlated)

Attackers rarely rely on single permissions; malware is characterized by coordinated capability chains:

| Rule Name | Correlated Capabilities | Penalty | Threat Model Classification |
|---|---|:---:|---|
| **Banking Trojan Pattern** | `Accessibility Service` + `Overlay (SYSTEM_ALERT_WINDOW)` | **+20** | **Anatsa / Teabot / SharkBot:** Injects fake banking login over banking apps and auto-approves wire transfers. |
| **SMS Data Stealer** | `SMS (READ/RECEIVE)` + `INTERNET` + `RECEIVE_BOOT_COMPLETED` | **+15** | **SpyNote / Joker:** Intercepts 2FA OTPs on boot and transmits them to C2 server. |
| **Suspicious Device Admin** | `Device Admin` active on an app whose label is NOT Security/MDM | **+12** | **Ransomware / Persistence:** Blocks user from disabling device admin or uninstalling app. |
| **Mobile Spyware Pattern** | `CAMERA` + `RECORD_AUDIO` + `LOCATION` + `INTERNET` | **+10** | **Pegasus / FinSpy / SpApp:** Continuous audiovisual and spatial surveillance beacon. |
| **Social Graph Harvester** | `CONTACTS` + `CALL_LOG` + `SMS` + `INTERNET` | **+12** | **Predatory Loan / Blackmail:** Extracts contact book and call logs for social engineering extortion. |
| **Sideloaded SMS Risk** | Sideloaded APK (`installer == Sideloaded`) + `SMS` access | **+10** | Bypasses Google Play Protect SMS policy restrictions; high probability of trojanized dropper. |
| **OTP Exfiltration** | `Notification Listener` + `INTERNET` | **+8** | Intercepts notification OTPs from WhatsApp, Telegram, and banking apps. |
| **Stealth Background Tracker** | `ACCESS_BACKGROUND_LOCATION` + `INTERNET` | **+8** | Covert 24/7 geolocation tracking without foreground indication. |

---

## 5. Context Mismatch Heuristics

Flags low-complexity utility apps requesting high-risk authorizations:
- **Scope Keywords:** `torch`, `flashlight`, `calculator`, `wallpaper`, `weather`, `clock`, `compass`, `cleaner`, `booster`, `battery saver`, `notes`.
- **Trigger:** Any matched utility app holding **SMS**, **Contacts**, **Call Logs**, **Camera**, or **Microphone** permissions.
- **Penalty:** **+15 points** (instantly triggers `CRITICAL` or `HIGH` status with explanation: *"Utility tool holds intrusive data harvesting permissions unmatched to core function"*).

---

## 6. Sideloading Provenance Penalty

- **Verified Play Store / OEM App Store:** 0 penalty.
- **Sideloaded / Unknown Package Source:** **+10 penalty** if the app declares any dangerous runtime permission.

---

## 7. Explainability & Triage (Non-Technical User Answers)

For any app in the **High** or **Critical** tier, the auditor generates a minimum of **3 human-readable explanations**:
1. **Q1: Which of my apps can do the most harm?** $\to$ Highest risk score on Dashboard & Top 5 Threat Card.
2. **Q2: Why is each one flagged?** $\to$ "Why Flagged" card detailing combination rules triggered, active special access, and sensitive categories.
3. **Q3: What can I do about it right now?** $\to$ Direct 1-tap buttons: **"Open App Settings"** (to revoke permissions) or **"Uninstall"** (to remove package).
