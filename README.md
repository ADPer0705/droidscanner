# RiskScanner — On-Device App Risk Auditor (PS-01)

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Offline First](https://img.shields.io/badge/Privacy-100%25%20On--Device-success.svg)](#)

> **Hackathon Problem Statement PS-01:** On-Device App Risk Auditor  
> *Find suspicious Android apps through their permissions, special access, and an explainable risk score.*

---

## 🎯 The 3 Core Non-Technical User Questions

| Question | How RiskScanner Answers It |
|---|---|
| **Q1: Which of my apps can do the most harm?** | **Security Health Overview & Top Risk Apps:** Normalized 0–100 risk score, sorted risk leaderboards, and immediate threat tier badges (**Critical, High, Medium, Low**). |
| **Q2: Why is each one flagged?** | **"Why Flagged" Explainability Panel:** Plain-language, bullet-pointed security reasons highlighting specific attack patterns (Banking Trojans, SMS Data Stealers, Context Mismatches). |
| **Q3: What can I do about it right now?** | **1-Tap Action Triage:** Dedicated direct links to **"Open App Settings"** (to revoke permissions) and **"Uninstall"** (to delete the app immediately). |

---

## 🛡️ The 9 Mandatory Permission Categories

Special access carries the highest baseline weight because it enables banking trojan overlays and screen scraping:

1. **Special Access (Highest Weight - 25 pts):** `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `PACKAGE_USAGE_STATS`, Accessibility Services, Device Admin, Notification Listener.
2. **SMS (15 pts):** `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS` (OTP interception, premium SMS fraud).
3. **Contacts & Call Logs (12 pts):** `READ_CONTACTS`, `READ_CALL_LOG` (Social graph harvesting & extortion).
4. **Camera & Microphone (14 pts):** `CAMERA`, `RECORD_AUDIO` (Covert recording & surveillance).
5. **Location (12 pts):** `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` (Stalking & physical tracking).
6. **Phone & Identity (10 pts):** `READ_PHONE_STATE`, `CALL_PHONE` (Device fingerprinting & toll fraud).
7. **Storage & Media (8 pts):** `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES` (Photo & private document theft).
8. **Background & Persistence (8 pts):** `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (Survives device reboots).
9. **Network (3 pts):** `INTERNET`, `ACCESS_NETWORK_STATE` (The exfiltration pipeline; force multiplier in combination rules).

---

## ⚡ Correlation & Combination Rules (MobSF-Inspired)

- **Banking Trojan Pattern (+20 pts):** Accessibility Service + Screen Overlay (`SYSTEM_ALERT_WINDOW`) $\to$ Injects fake login overlays and simulates touch inputs.
- **SMS Data Stealer (+15 pts):** SMS read/receive + Internet + Boot Persistence $\to$ Steals 2FA OTP codes on system startup.
- **Suspicious Device Administrator (+12 pts):** Active Device Admin rights granted to non-MDM/non-security apps $\to$ Prevents uninstallation and enables device lock/wipe.
- **Mobile Spyware Pattern (+10 pts):** Camera + Audio + Location + Internet $\to$ Background surveillance beacon.
- **Context Mismatch (+15 pts):** Utility apps (calculators, flashlights, wallpapers, compasses) demanding SMS, Contacts, or Microphone access.
- **Sideloaded Risk (+10 pts):** APKs installed from unknown / non-Play Store sources holding sensitive runtime permissions.

---

## 🏗️ Architecture & Features

- **100% On-Device:** Zero root or ADB required. No user telemetry ever leaves the device.
- **MVVM Architecture:** Clean unidirectional data flow with Kotlin Coroutines and StateFlow.
- **Jetpack Compose + Material 3:** Modern, responsive UI with smooth animations, dark/light theme support, and color-coded threat badges.
- **Background Install Monitor:** `BroadcastReceiver` listens for package additions and sends high-priority notifications when an installed app scores High or Critical.
- **Static APK Inspection:** Extracts declared vs. granted permissions, protection levels (`normal`, `dangerous`, `signature`, `special`), installer source, and computes SHA-256 base APK hashes.

---

## 📄 Deliverables

- **Scoring Rubric:** Complete 1-page risk methodology documented in [`SCORING_RUBRIC.md`](SCORING_RUBRIC.md).
- **Package Name:** `com.idiots3.riskscanner`
- **Target SDK:** Android 14 / 15 / 16 (API 26–36)
