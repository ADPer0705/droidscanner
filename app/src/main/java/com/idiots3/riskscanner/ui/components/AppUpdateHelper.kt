package com.idiots3.riskscanner.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.net.URLEncoder

data class LocalAppUpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val recommendedAbi: String,
    val obtainiumPackage: String?,
    val installedViaObtainium: Boolean
) {
    val obtainiumInstalled: Boolean get() = obtainiumPackage != null
}

/**
 * Zero-INTERNET update check & Obtainium / GitHub Releases handoff helper.
 *
 * DroidScanner explicitly strips android.permission.INTERNET at the manifest level, so manual
 * update checks delegate cleanly to Obtainium (opening DroidScanner's entry directly when
 * installed) or the latest GitHub Releases page in the user's browser.
 */
object AppUpdateHelper {

    const val APPLICATION_ID = "com.idiots3.droidscanner"
    const val GITHUB_REPO_URL = "https://github.com/ADPer0705/droidscanner"
    const val GITHUB_RELEASES_URL = "https://github.com/ADPer0705/droidscanner/releases/latest"
    const val SITE_INSTALL_URL = "https://droidscanner.dearanayji.rocks/#install"

    val OBTAINIUM_PACKAGES = listOf(
        "dev.imranr.obtainium",
        "dev.imranr.obtainium.fdroid"
    )

    fun obtainiumAddUri(repoUrl: String = GITHUB_REPO_URL): String =
        "obtainium://add/$repoUrl"

    fun obtainiumConfigJson(): String =
        """{"id":"$APPLICATION_ID","url":"$GITHUB_REPO_URL","author":"ADPer0705","name":"DroidScanner"}"""

    fun obtainiumImportWebRedirectUrl(): String {
        val deepLink = "obtainium://app/" + obtainiumConfigJson()
        val encoded = URLEncoder.encode(deepLink, Charsets.UTF_8.name()).replace("+", "%20")
        return "https://apps.obtainium.imranr.dev/redirect?r=$encoded"
    }

    fun recommendedAbi(supportedAbis: Array<String>): String = when {
        supportedAbis.contains("arm64-v8a") -> "arm64-v8a"
        supportedAbis.contains("armeabi-v7a") -> "armeabi-v7a"
        supportedAbis.contains("x86_64") -> "x86_64"
        else -> "universal"
    }

    fun inspectLocal(context: Context): LocalAppUpdateInfo {
        val pm = context.packageManager
        val pkgName = context.packageName
        var versionName = "?"
        var versionCode = 0L

        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkgName, 0)
            }
            versionName = info.versionName ?: "?"
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }

        val obtainiumPkg = OBTAINIUM_PACKAGES.firstOrNull { candidate ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(candidate, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(candidate, 0)
                }
                true
            }.getOrDefault(false)
        }

        val installedViaObtainium = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val src = pm.getInstallSourceInfo(pkgName)
                src.installingPackageName in OBTAINIUM_PACKAGES ||
                    src.initiatingPackageName in OBTAINIUM_PACKAGES
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkgName) in OBTAINIUM_PACKAGES
            }
        }.getOrDefault(false)

        return LocalAppUpdateInfo(
            versionName = versionName,
            versionCode = versionCode,
            recommendedAbi = recommendedAbi(Build.SUPPORTED_ABIS ?: emptyArray()),
            obtainiumPackage = obtainiumPkg,
            installedViaObtainium = installedViaObtainium
        )
    }

    fun launchObtainiumUpdate(context: Context, obtainiumPackage: String? = null): Boolean {
        val uri = Uri.parse(obtainiumAddUri())
        val candidates = buildList {
            if (!obtainiumPackage.isNullOrBlank()) {
                add(
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(obtainiumPackage)
                    }
                )
            }
            add(Intent(Intent.ACTION_VIEW, uri))
            if (!obtainiumPackage.isNullOrBlank()) {
                context.packageManager.getLaunchIntentForPackage(obtainiumPackage)?.let(::add)
            }
        }
        return launchFirst(context, candidates)
    }

    fun openGitHubReleases(context: Context): Boolean =
        launchUrls(context, GITHUB_RELEASES_URL, SITE_INSTALL_URL)

    fun openObtainiumSetup(context: Context): Boolean =
        launchUrls(context, obtainiumImportWebRedirectUrl(), SITE_INSTALL_URL)

    fun openProjectSite(context: Context): Boolean =
        launchUrls(context, SITE_INSTALL_URL)

    private fun launchUrls(context: Context, vararg urls: String): Boolean =
        launchFirst(context, urls.map { Intent(Intent.ACTION_VIEW, Uri.parse(it)) })

    private fun launchFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            val launched = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        return false
    }
}
