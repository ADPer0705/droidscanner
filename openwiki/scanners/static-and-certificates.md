---
type: component
title: Static APK, DEX Bytecode, Certificates & Pre-Install Scanner
description: >-
  Documents lazy StaticInspector execution across base and split APKs, hidden
  payload and packer checks, memory-mapped DexParser/DexInspector tracker and
  sensitive-API analysis, CertificateInspector v1–v3.1 scheme detection, and the
  zero-storage-permission SAF Pre-Install APK Scanner.
tags: [static-analysis, dex-parser, trackers, certificates, apk-signing, pre-install]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T19:21:54.091Z
sources:
  - id: openwiki-source-5d8d806b025a1b086b731787
    resource: repo://app/src/main/java/com/idiots3/riskscanner/model/SecurityModels.kt
  - id: openwiki-source-fd0627f9fff4988cee63fd04
    resource: repo://app/src/main/java/com/idiots3/riskscanner/scanner/ApkFileScanner.kt
  - id: openwiki-source-8b54d4cfde26c982d563f64b
    resource: repo://app/src/main/java/com/idiots3/riskscanner/scanner/CertificateInspector.kt
  - id: openwiki-source-0706ee68b99f23840fa76f8c
    resource: repo://app/src/main/java/com/idiots3/riskscanner/scanner/DexInspector.kt
  - id: openwiki-source-ba6f515dea4eafb22b679b65
    resource: repo://app/src/main/java/com/idiots3/riskscanner/scanner/DexParser.kt
  - id: openwiki-source-840b9d7fd8794fad7623622b
    resource: repo://app/src/main/java/com/idiots3/riskscanner/scanner/StaticInspector.kt
generated: { by: "antigravity", at: "2026-11-23T19:21:54.091Z" }
---

# Static APK, DEX Bytecode, Certificates & Pre-Install Scanner

To keep full-device scans fast and memory flat on 2 GB–4 GB consumer phones, DroidScanner runs deep APK container inspection, streaming DEX bytecode parsing, and SHA-256 hashing lazily when the user opens **App Detail** (`MainViewModel.loadStaticChecks(packageName)`) or inspects a downloaded `.apk` via **Pre-Install APK Scanner**.

## Lazy Static & Split-APK Container Inspection (`StaticInspector`)

`StaticInspector` (`app/src/main/java/com/idiots3/riskscanner/scanner/StaticInspector.kt`) runs on `Dispatchers.IO` for both installed apps (`inspect()`) and uninstalled SAF archives (`inspectArchive()`):

1. **Exported vs. unprotected components:** Queries `PackageManager` with `GET_ACTIVITIES or GET_SERVICES or GET_RECEIVERS or GET_PROVIDERS`, recording total exported counts alongside unprotected exported counts:
   - Activities: `exported && permission == null && name != launcherActivity` (excluding the main launcher activity to avoid false warnings).
   - Services & Receivers: `exported && permission == null`.
   - Providers: `exported && readPermission == null && writePermission == null`.
   - Collects up to `MAX_UNPROTECTED_LIST = 12` `UnprotectedComponent` entries prioritized by Service → Receiver → Provider → Activity.
2. **Manifest & build flags:** Reads `FLAG_USES_CLEARTEXT_TRAFFIC`, `FLAG_DEBUGGABLE`, `FLAG_ALLOW_BACKUP`, `FLAG_TEST_ONLY`, `targetSdkVersion`, `minSdkVersion`, and `splitSourceDirs?.size`.
3. **Container, split-APK & hidden-payload hygiene (`inspectContainer`):** Never loads an APK into Java heap:
   - Streams `ZipFile` entries across the base APK and readable `splitSourceDirs` to collect up to `MAX_NATIVE_LIBS = 24` unique `lib/*/*.so` basenames and matches them against 15 known commercial packer/obfuscator libraries in `assets/tracker_signatures.json` (`packerLibs`).
   - Flags ZIP anomalies (`../` or leading-`/` path-traversal entries and duplicate `AndroidManifest.xml` or `classes.dex` entries).
   - Reads only the first 4 magic bytes of files >= 32 bytes under `assets/` and `res/raw/` (up to `MAX_HIDDEN_PAYLOADS = 8`) to flag hidden DEX bytecode (`dex\n`), hidden native ELF binaries (`\x7fELF`), or disguised ZIP/embedded APK archives (excluding benign extensions like `.zip`, `.jar`, `.docx`, `.epub`).
   - Computes streaming 64 KB-buffered `SHA-256` of the base APK locally. If OEM restrictions block reading `sourceDir`, sets `StaticChecks.limitedAnalysisNote` and still returns manifest + certificate metadata cleanly.

## Streaming DEX Bytecode & Tracker Analysis (`DexInspector` & `DexParser`)

For non-system apps and pre-install archives, `StaticInspector` invokes `DexInspector.inspectApk()` (`scanner/DexInspector.kt`) backed by the pure-JVM `DexParser` (`scanner/DexParser.kt`):

- **Memory-safe extraction (`DexInspector`):** Checks `ActivityManager.isLowRamDevice` to cap inspection at `4` DEX files (`<= 12 MB` each) on low-RAM phones or `8` DEX files (`<= 22 MB` each) on standard devices. Each `classes*.dex` entry is streamed to a temporary file in `cacheDir`, mapped read-only via `FileChannel.map(READ_ONLY)`, parsed, and deleted in `finally`.
- **Single-pass DEX inspection (`DexParser.scanBuffer`):** Validates `"dex\n...\0"` magic and header table bounds (reporting malformed buffers as `parseAnomaly = true` without crashing), then scans:
  1. **`type_ids`:** Matches class descriptors against 66 embedded ad/analytics/profiling tracker SDK prefixes (`assets/tracker_signatures.json`) and indexes watched framework classes.
  2. **`method_ids`:** Flags 10 high-risk API capabilities only when `class_idx` hits a watched class: `DexClassLoader`, `InMemoryDexClassLoader`, `Runtime.exec`, `ProcessBuilder.start`, `SmsManager.send*Message`, `DevicePolicyManager.wipeData`/`lockNow`/`resetPassword*`, `MediaProjectionManager.createScreenCaptureIntent`, `AccessibilityService.performGlobalAction`/`dispatchGesture`, `PackageInstaller.createSession`/`openSession`, and `WebView.addJavascriptInterface`.
  3. **`string_ids`:** Matches against 76 popular banking/UPI/crypto target package names (`assets/finance_targets.json`), counts non-standard cleartext `http://` URLs (ignoring XML/schema/localhost prefixes), and extracts up to 6 public IPv4 endpoints (`isPublicIpv4` filters RFC1918, loopback, link-local, and multicast ranges).

## Signing Certificates & Binary Scheme Detection (`CertificateInspector`)

`CertificateInspector` (`app/src/main/java/com/idiots3/riskscanner/scanner/CertificateInspector.kt`) provides both `inspectFromPackageInfo()` (fast zero-file-I/O extraction during full scans and pre-install inspection) and `inspect()` (combining `PackageManager` with binary APK scheme verification):

- **X.509 certificates (`CertInfo`):** Reads `GET_SIGNING_CERTIFICATES` (API 28+) or `GET_SIGNATURES` (API 26–27), tracks `hasMultipleSigners` and `hasPastSigners` (key rotation), computes colon-formatted `SHA-256` and `SHA-1` fingerprints, extracts RSA/EC bit lengths, and flags `isDebugCert`, `isExpired`, and `isWeakKey` (RSA `< 2048` bits or EC `< 224` bits).
- **Binary scheme detection (`ApkSignatureSchemeDetector.detectSchemes`):** Pure-JVM detector without loading the APK into RAM:
  - **v1 (JAR):** Scans `ZipFile` entries for top-level `META-INF/*.RSA`, `.DSA`, or `.EC`.
  - **v2 / v3 / v3.1 (APK Signing Block):** Uses `RandomAccessFile` to locate the ZIP EOCD (`0x06054b50`), reads the Central Directory offset, validates the 16-byte `"APK Sig Block 42"` magic and matching leading/trailing 64-bit sizes (capped at 64 MB), and walks ID-value pairs for `0x7109871a` (`V2`), `0xf05368c0` (`V3`), and `0x1b93ad61` (`V3_1`).

## Zero-Storage-Permission Pre-Install APK Scanner (`ApkFileScanner`)

`ApkFileScanner.scanUri()` (`app/src/main/java/com/idiots3/riskscanner/scanner/ApkFileScanner.kt`) inspects any user-selected `.apk` via Android's Storage Access Framework (`OpenDocument`) or incoming `VIEW`/`SEND` `content://` intents without requesting storage permissions:

1. Streams up to `MAX_APK_BYTES = 250 MB` into a temporary file in `cacheDir` (always deleted in `finally`).
2. Calls `PackageManager.getPackageArchiveInfo()` with components, permissions, and signing flags, runs `StaticInspector.inspectArchive()`, `ImpersonationDetector.detect()`, and `RiskScorer.score()` (evaluating declared permissions as potential grants).
3. **Installed-signer mismatch check (`compareWithInstalled`):** If the candidate's `packageName` is already installed on the device, compares its signing certificate `SHA-256` against the installed package's certificate and raises a `DO_NOT_INSTALL` verdict on mismatch (classic trojanized-update detection).
4. Emits a clear 4-level `PreInstallVerdict` (`DO_NOT_INSTALL`, `HIGH_RISK`, `CAUTION`, `LOOKS_SAFE`) based on signer mismatch, impersonation, Critical risk, hidden payloads, `>= 5` finance targets, debug signing/build, or packer libraries.

## Related Pages

- [App Inventory, Provenance & Special Access](app-and-special-access.md)
- [UI Navigation, Remediation & Reporting](../ui/navigation-and-reporting.md)
- [Testing, Calibration & TestKit](../testing/test-suite-and-testkit.md)
