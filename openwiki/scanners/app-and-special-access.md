---
type: component
title: App Inventory, Provenance & Special Access
description: >-
  Documents AppScanner's batched package enumeration, OEM store and device
  migration install provenance, full OS default-role resolution, fast signing
  metadata, and SpecialAccessRegistry's 4-state Android 10+ AppOps verification.
tags: [scanner, package-manager, provenance, special-access, appops, roles]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T19:21:54.091Z
sources:
  - id: openwiki-source-7c877b24187f06be23499c18
    resource: repo://app/src/main/java/com/idiots3/riskscanner/model/AppInfo.kt
  - id: openwiki-source-ff2fde6e03c18dab2ebff34c
    resource: repo://app/src/main/java/com/idiots3/riskscanner/scanner/AppScanner.kt
generated: { by: "antigravity", at: "2026-11-23T19:21:54.091Z" }
---

# App Inventory, Provenance & Special Access

`AppScanner` (`app/src/main/java/com/idiots3/riskscanner/scanner/AppScanner.kt`) performs the core package-level audit on `Dispatchers.IO` using only public, rootless Android APIs (`QUERY_ALL_PACKAGES`).

## Batched Inventory, Signing & Role Resolution

To avoid N× `PackageManager` IPC overhead across 200+ installed apps, `scanAllApps(includeSystemApps)` queries `GET_PERMISSIONS or GET_SERVICES or GET_RECEIVERS or GET_SIGNING_CERTIFICATES` (falling back to `GET_SIGNATURES` on API 26–27) once and pre-loads shared lookup sets up front:

1. **Launcher visibility (`loadLauncherPackages()`):** Queries `ACTION_MAIN` + `CATEGORY_LAUNCHER` via `queryIntentActivities` once instead of calling `getLaunchIntentForPackage` per app (falling back to `true` when unavailable so apps are not falsely labeled hidden).
2. **Full OS default roles (`DeviceRoles.resolve()`):**
   - `defaultSms`: `Telephony.Sms.getDefaultSmsPackage(context)`
   - `defaultDialer`: `TelecomManager.defaultDialerPackage`
   - `defaultBrowser`: `PackageManager.resolveActivity` for `https://example.com` (`MATCH_DEFAULT_ONLY`)
   - `defaultLauncher`: `PackageManager.resolveActivity` for `ACTION_MAIN` + `CATEGORY_HOME` (`MATCH_DEFAULT_ONLY`)
   - `enabledImes` & `activeIme`: `InputMethodManager.enabledInputMethodList` and `Settings.Secure.DEFAULT_INPUT_METHOD`
   - `autofillPackage`: `Settings.Secure` `autofill_service`
   - `assistantPackage`: `Settings.Secure` `assistant` or `voice_interaction_service`
   These flags both prevent false positives in `RiskScorer` (such as legitimate default messaging apps and keyboards) and detect high-risk sideloaded core-role or keylogger surfaces.
3. **Special-access registry (`SpecialAccessRegistry`):** Pre-loads active Accessibility Services (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` + `AccessibilityManager`), Notification Listeners (`NotificationManagerCompat.getEnabledListenerPackages` with `Settings.Secure` fallback), and active Device Administrators (`DevicePolicyManager.activeAdmins`).
4. **Fast zero-file-I/O signing & catalog enrichment:** For each package, `CertificateInspector.inspectFromPackageInfo()` extracts `signingCertSha256`, `isDebugSigned`, `isWeakSigningKey`, and `isCertExpired` directly from the batched `PackageInfo` without reading APK files, while `KnownSafeApps.match()` and `ImpersonationDetector.detect()` enrich `AppInfo` before `RiskScorer.score(enriched, null)` executes.

## Install Provenance Classification

`resolveInstallProvenance()` inspects `getInstallSourceInfo()` (API 30+ `installingPackageName` and `initiatingPackageName`) or `getInstallerPackageName()` (API 26–29) and maps each app to `InstallProvenance`:

| Provenance | Examples / Condition | Treated as Sideloaded? |
|---|---|---|
| `OFFICIAL_STORE` | Google Play, Samsung Galaxy Store, Amazon App Store, Huawei AppGallery, Xiaomi GetApps, Oppo/OnePlus App Market, Vivo App Store, GrapheneOS Apps | No |
| `ALTERNATIVE_STORE` | F-Droid, Droid-ify, Neo Store, Aurora Store, Obtainium, Accrescent, App Lounge | No |
| `DEVICE_MIGRATION` | Google Restore / Backup, Samsung Smart Switch, Mi Mover, Clone Phone, EasyShare, Huawei Phone Clone, Motorola Migrate | No |
| `PREINSTALLED_SYSTEM` | System apps (`FLAG_SYSTEM`) without a store override | No |
| `ADB_OR_SHELL` | `com.android.shell` as installer or initiator | Yes |
| `BROWSER_OR_APP_SIDELOAD` | Non-installer initiating package (e.g., browser or messenger); resolves initiator's cached app label (`"Sideloaded via <App>"`) | Yes (unless system) |
| `PACKAGE_INSTALLER` | Android or OEM package installer (Google, AOSP, Samsung, MIUI, Oplus/ColorOS, Vivo/BBK, Huawei) without a known store | Yes (unless system) |
| `UNKNOWN` | No installer metadata on a non-system app | Yes |

`AppInfo.isSideloaded` explicitly requires `!isSystemApp && installProvenance.isSideloaded`, preventing pre-installed system apps and apps transferred via phone-migration tools from being falsely penalized as sideloaded.

## Honest Special Access Detection

Android splits special privileges across system registries and AppOps:

- **Directly verifiable system-wide:** Accessibility Services (`BIND_ACCESSIBILITY_SERVICE`), Device Administrators (`BIND_DEVICE_ADMIN`), and Notification Listeners (`BIND_NOTIFICATION_LISTENER_SERVICE`) are both manifest-checked (`*Declared`) and registry-verified (`*Enabled` / `deviceAdminActive`).
- **AppOps-backed (`SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `MANAGE_EXTERNAL_STORAGE`):**
  On Android 10+, calling `AppOpsManager.unsafeCheckOpNoThrow` for *other* UIDs typically throws `SecurityException` or returns `MODE_DEFAULT`. Rather than catching exceptions and returning `false`, `SpecialAccessRegistry.appOpsState()` returns a 4-state `SpecialAccessGrantState`:
  - `ENABLED`: `MODE_ALLOWED`
  - `DECLARED_DENIED`: `MODE_IGNORED` or `MODE_ERRORED`
  - `DECLARED_UNVERIFIED`: declared in manifest, but cross-UID AppOps could not be verified rootlessly (UI prompts the user to verify in Settings)
  - `NOT_DECLARED`: not requested in manifest
- **Additional declared capabilities:** `BIND_VPN_SERVICE` (`vpnServiceDeclared`), `BIND_NFC_SERVICE` (`nfcHceDeclared`), and `FOREGROUND_SERVICE_MEDIA_PROJECTION` (`screenCaptureDeclared`).

## Related Pages

- [Architecture, Trust & Background Guard](../architecture/overview.md)
- [Static APK, DEX Bytecode, Certificates & Pre-Install Scanner](static-and-certificates.md)
- [Risk Scoring v3, Impersonation & Recommendations](../scoring/risk-and-recommendations.md)
