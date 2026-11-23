package com.idiots3.riskscanner.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.idiots3.riskscanner.engine.ImpersonationDetector
import com.idiots3.riskscanner.engine.RiskScorer
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.InstallProvenance
import com.idiots3.riskscanner.model.PreInstallScanResult
import com.idiots3.riskscanner.model.PreInstallVerdict
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.model.SpecialAccessGrantState
import com.idiots3.riskscanner.model.SpecialAccessInfo
import com.idiots3.riskscanner.model.StaticChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Zero-storage-permission Pre-Install APK Inspector.
 * Reads a user-picked APK via Android's Storage Access Framework (`content://` URI), copies
 * it temporarily to internal cache, runs manifest, certificate, installed-signer comparison,
 * impersonation, hidden-payload, tracker, and DEX capability inspection, then deletes the temp copy.
 */
class ApkFileScanner(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val appScanner = AppScanner(context)
    private val certificateInspector = CertificateInspector(pm)
    private val staticInspector = StaticInspector(context)

    suspend fun scanUri(uri: Uri): PreInstallScanResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "selected.apk"
        val tmpFile = File(context.cacheDir, "preinstall_inspect_${System.nanoTime()}.apk")

        try {
            val copiedBytes = copyUriToTemp(uri, tmpFile)
            if (copiedBytes <= 0L) {
                return@withContext errorResult(displayName, 0L, "Could not read the selected file.")
            }

            val sigFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                sigFlag

            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(tmpFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(tmpFile.absolutePath, flags)
            }

            if (pkgInfo == null || pkgInfo.applicationInfo == null) {
                return@withContext errorResult(
                    displayName,
                    copiedBytes,
                    "This file does not appear to be a valid Android APK package."
                )
            }

            val app = pkgInfo.applicationInfo!!.apply {
                sourceDir = tmpFile.absolutePath
                publicSourceDir = tmpFile.absolutePath
            }
            val appName = runCatching { app.loadLabel(pm).toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: pkgInfo.packageName

            val signerInfo = certificateInspector.inspectFromPackageInfo(pkgInfo, tmpFile.absolutePath)
            val primaryCert = signerInfo.primaryCert
            val staticChecks = staticInspector.inspectArchive(tmpFile, pkgInfo, signerInfo)

            val permissions = appScanner.extractPermissions(pkgInfo, assumeDeclaredAsPotential = true)
            val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()
            val services = pkgInfo.services.orEmpty()
            val receivers = pkgInfo.receivers.orEmpty()

            val specialAccess = SpecialAccessInfo(
                accessibilityDeclared = services.any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" },
                deviceAdminDeclared = receivers.any { it.permission == "android.permission.BIND_DEVICE_ADMIN" },
                notificationListenerDeclared = services.any { it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" },
                overlayDeclared = requested.contains("android.permission.SYSTEM_ALERT_WINDOW"),
                overlayState = SpecialAccessInfo.deriveState(false, requested.contains("android.permission.SYSTEM_ALERT_WINDOW")),
                installPackagesDeclared = requested.contains("android.permission.REQUEST_INSTALL_PACKAGES"),
                installPackagesState = SpecialAccessInfo.deriveState(false, requested.contains("android.permission.REQUEST_INSTALL_PACKAGES")),
                usageStatsDeclared = requested.contains("android.permission.PACKAGE_USAGE_STATS"),
                usageStatsState = SpecialAccessInfo.deriveState(false, requested.contains("android.permission.PACKAGE_USAGE_STATS")),
                allFilesAccessDeclared = requested.contains("android.permission.MANAGE_EXTERNAL_STORAGE"),
                allFilesAccessState = SpecialAccessInfo.deriveState(false, requested.contains("android.permission.MANAGE_EXTERNAL_STORAGE")),
                vpnServiceDeclared = services.any { it.permission == "android.permission.BIND_VPN_SERVICE" },
                nfcHceDeclared = services.any { it.permission == "android.permission.BIND_NFC_SERVICE" },
                screenCaptureDeclared = requested.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
            )

            val appFlags = app.flags
            val baseInfo = AppInfo(
                appName = appName,
                packageName = pkgInfo.packageName,
                versionName = pkgInfo.versionName ?: "?",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
                },
                installDate = 0L,
                updateDate = 0L,
                installerSource = "Pre-install APK file",
                targetSdk = app.targetSdkVersion,
                minSdk = app.minSdkVersion,
                isSystemApp = false,
                hasLauncherActivity = true,
                androidCategory = null,
                apkPath = tmpFile.absolutePath,
                permissions = permissions,
                specialAccess = specialAccess,
                categories = permissions.groupBy { it.category },
                installProvenance = InstallProvenance.PACKAGE_INSTALLER,
                isDebuggable = (appFlags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                isTestOnly = (appFlags and ApplicationInfo.FLAG_TEST_ONLY) != 0,
                allowsCleartextTraffic = (appFlags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0,
                signingCertSha256 = primaryCert?.sha256Fingerprint,
                isDebugSigned = signerInfo.hasDebugCert,
                isWeakSigningKey = signerInfo.hasWeakKey,
                isCertExpired = signerInfo.hasExpiredCert
            )

            val impersonation = ImpersonationDetector.detect(baseInfo, ImpersonationDetector.load(context))
            val enriched = baseInfo.copy(impersonationTarget = impersonation?.targetLabel)
            val scored = RiskScorer.score(enriched, null)
            val finalAppInfo = enriched.copy(
                ruleScore = scored.ruleScore,
                riskScore = scored.score,
                riskLevel = scored.level,
                riskReasons = scored.reasons,
                scoreBreakdown = scored.breakdown
            )

            // Check if this package is already installed & whether the signing certificate matches!
            val installedComparison = compareWithInstalled(pkgInfo.packageName, primaryCert?.sha256Fingerprint)

            val highlights = buildHighlights(finalAppInfo, staticChecks, installedComparison)
            val (verdict, summary) = determineVerdict(finalAppInfo, staticChecks, installedComparison)

            PreInstallScanResult(
                fileName = displayName,
                fileSizeBytes = copiedBytes,
                appInfo = finalAppInfo,
                staticChecks = staticChecks,
                verdict = verdict,
                summaryMessage = summary,
                highlights = highlights,
                installedPackageMatches = installedComparison.installed,
                signerMismatchWithInstalled = installedComparison.signerMismatch,
                installedVersionName = installedComparison.installedVersion
            )
        } catch (t: Throwable) {
            errorResult(displayName, tmpFile.length(), "Could not inspect APK: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            runCatching { tmpFile.delete() }
        }
    }

    private data class InstalledComparison(
        val installed: Boolean = false,
        val signerMismatch: Boolean = false,
        val installedVersion: String? = null
    )

    private fun compareWithInstalled(packageName: String, candidateCertSha256: String?): InstalledComparison {
        val installedPkg = runCatching {
            val sigFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(sigFlag.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, sigFlag)
            }
        }.getOrNull() ?: return InstalledComparison()

        val installedCert = certificateInspector.inspectFromPackageInfo(installedPkg).primaryCert?.sha256Fingerprint
        val mismatch = !installedCert.isNullOrBlank() &&
            !candidateCertSha256.isNullOrBlank() &&
            !installedCert.equals(candidateCertSha256, ignoreCase = true)

        return InstalledComparison(
            installed = true,
            signerMismatch = mismatch,
            installedVersion = installedPkg.versionName
        )
    }

    private fun buildHighlights(
        app: AppInfo,
        checks: StaticChecks,
        installed: InstalledComparison
    ): List<String> = buildList {
        if (installed.signerMismatch) {
            add("CRITICAL: This APK uses package ${app.packageName}, which is already installed — but it is signed by a DIFFERENT developer certificate!")
        } else if (installed.installed) {
            add("Matches the developer signing certificate of your installed version (${installed.installedVersion ?: "?"}).")
        }
        if (app.impersonationTarget != null) {
            add("CRITICAL: Impersonates \"${app.impersonationTarget}\" from unofficial package ${app.packageName}.")
        }
        if (app.isDebugSigned) {
            add("Signed with an Android debug key rather than a real developer release certificate.")
        }
        if (checks.signerInfo?.isV1Only == true) {
            add("Uses legacy v1-only JAR signing (no modern APK Signature Scheme v2/v3).")
        }
        if (checks.hiddenPayloads.isNotEmpty()) {
            add("Hidden executable payload(s) found inside assets/res: ${checks.hiddenPayloads.take(2).joinToString { it.entryPath }}.")
        }
        if (checks.packerLibs.isNotEmpty()) {
            add("Commercial packer / obfuscation library detected: ${checks.packerLibs.first()}.")
        }
        val dex = checks.dexAnalysis
        if (dex != null) {
            if (dex.financeTargetMatches.size >= 5) {
                add("Code references ${dex.financeTargetMatches.size} banking/crypto apps (common banking-trojan target list).")
            }
            if (dex.highRiskApis.isNotEmpty()) {
                add("Sensitive code APIs found: ${dex.highRiskApis.take(3).joinToString { it.name }}.")
            }
            if (dex.detectedTrackers.isNotEmpty()) {
                add("${dex.detectedTrackers.size} embedded ad/analytics tracker SDK(s) detected.")
            }
        }
        if (app.specialAccess.accessibilityDeclared) {
            add("Requests an Accessibility Service (can read screens and simulate taps if enabled).")
        }
        if (app.specialAccess.deviceAdminDeclared) {
            add("Includes a Device Administrator component (can lock/wipe phone and block uninstall).")
        }
        if (app.specialAccess.installPackagesDeclared) {
            add("Requests permission to install additional APKs (Install unknown apps).")
        }
    }

    private fun determineVerdict(
        app: AppInfo,
        checks: StaticChecks,
        installed: InstalledComparison
    ): Pair<PreInstallVerdict, String> {
        val financeTargetAlert = (checks.dexAnalysis?.financeTargetMatches?.size ?: 0) >= 5
        val severe = installed.signerMismatch ||
            app.impersonationTarget != null ||
            app.riskLevel == RiskLevel.CRITICAL ||
            checks.hiddenPayloads.isNotEmpty() ||
            financeTargetAlert

        return when {
            severe -> PreInstallVerdict.DO_NOT_INSTALL to
                "Strong security red flags were found in this APK. Avoid installing unless you built it yourself and understand every warning below."
            app.riskLevel == RiskLevel.HIGH || app.isDebugSigned || app.isDebuggable || checks.packerLibs.isNotEmpty() ->
                PreInstallVerdict.HIGH_RISK to
                    "This APK requests high-impact capabilities or shows unusual build/signing traits. Verify the source before installing."
            app.riskLevel == RiskLevel.MEDIUM || app.specialAccess.hasAny ->
                PreInstallVerdict.CAUTION to
                    "This APK requests sensitive permissions or special access. Review the capabilities below before installing."
            else -> PreInstallVerdict.LOOKS_SAFE to
                "No major red flags found in static manifest, certificate, or DEX checks. Remember static checks cannot guarantee runtime behaviour."
        }
    }

    private fun copyUriToTemp(uri: Uri, dest: File): Long {
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(64 * 1024)
                var n = input.read(buf)
                while (n > 0) {
                    total += n
                    if (total > MAX_APK_BYTES) {
                        throw IllegalStateException("APK exceeds maximum inspection size (250 MB)")
                    }
                    out.write(buf, 0, n)
                    n = input.read(buf)
                }
            }
        } ?: return 0L
        return total
    }

    private fun resolveDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun errorResult(fileName: String, size: Long, msg: String) = PreInstallScanResult(
        fileName = fileName,
        fileSizeBytes = size,
        appInfo = null,
        staticChecks = StaticChecks(hashError = msg),
        verdict = PreInstallVerdict.CAUTION,
        summaryMessage = msg,
        highlights = emptyList(),
        error = msg
    )

    private companion object {
        const val MAX_APK_BYTES = 250L * 1024L * 1024L
    }
}
