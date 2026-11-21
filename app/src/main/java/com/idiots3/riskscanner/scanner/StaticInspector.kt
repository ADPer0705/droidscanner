package com.idiots3.riskscanner.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.StaticChecks
import com.idiots3.riskscanner.model.UnprotectedComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Static APK and manifest component inspector run lazily when the App Detail screen opens.
 * Never loads entire APKs into memory; falls back cleanly to metadata-only checks if OEM
 * restrictions block reading another app's APK path.
 */
class StaticInspector(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val certificateInspector = CertificateInspector(packageManager)

    suspend fun inspect(app: AppInfo, computeHash: Boolean = true): StaticChecks =
        withContext(Dispatchers.IO) {
            try {
                val flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        app.packageName,
                        PackageManager.PackageInfoFlags.of(flags.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(app.packageName, flags)
                }

                val info = packageInfo.applicationInfo
                val appFlags = info?.flags ?: 0
                val launcherActivity = runCatching {
                    packageManager.getLaunchIntentForPackage(app.packageName)?.component?.className
                }.getOrNull()

                val activities = packageInfo.activities.orEmpty()
                val services = packageInfo.services.orEmpty()
                val receivers = packageInfo.receivers.orEmpty()
                val providers = packageInfo.providers.orEmpty()

                val unprotectedActivitiesList = activities.filter {
                    it.exported && it.permission == null && it.name != launcherActivity
                }
                val unprotectedServicesList = services.filter { it.exported && it.permission == null }
                val unprotectedReceiversList = receivers.filter { it.exported && it.permission == null }
                val unprotectedProvidersList = providers.filter {
                    it.exported && it.readPermission == null && it.writePermission == null
                }

                val unprotectedComponents = buildList {
                    unprotectedServicesList.forEach { add(UnprotectedComponent("Service", it.name.substringAfterLast('.'))) }
                    unprotectedReceiversList.forEach { add(UnprotectedComponent("Receiver", it.name.substringAfterLast('.'))) }
                    unprotectedProvidersList.forEach { add(UnprotectedComponent("Provider", it.name.substringAfterLast('.'))) }
                    unprotectedActivitiesList.forEach { add(UnprotectedComponent("Activity", it.name.substringAfterLast('.'))) }
                }.take(MAX_UNPROTECTED_LIST)

                val apkFile = if (app.apkPath.isNotBlank()) File(app.apkPath) else null
                val readableApk = apkFile != null && apkFile.exists() && apkFile.canRead()
                val signerInfo = certificateInspector.inspect(app.packageName, app.apkPath)

                StaticChecks(
                    exportedActivities = activities.count { it.exported },
                    exportedServices = services.count { it.exported },
                    exportedReceivers = receivers.count { it.exported },
                    exportedProviders = providers.count { it.exported },
                    unprotectedExportedActivities = unprotectedActivitiesList.size,
                    unprotectedExportedServices = unprotectedServicesList.size,
                    unprotectedExportedReceivers = unprotectedReceiversList.size,
                    unprotectedExportedProviders = unprotectedProvidersList.size,
                    unprotectedComponents = unprotectedComponents,
                    cleartextTraffic = (appFlags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0,
                    debuggable = (appFlags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                    allowBackup = (appFlags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0,
                    testOnly = (appFlags and ApplicationInfo.FLAG_TEST_ONLY) != 0,
                    targetSdk = info?.targetSdkVersion ?: app.targetSdk,
                    minSdk = info?.minSdkVersion ?: app.minSdk,
                    apkSizeBytes = if (readableApk) apkFile!!.length() else 0L,
                    splitApkCount = info?.splitSourceDirs?.size ?: 0,
                    nativeLibraries = if (readableApk) readNativeLibs(apkFile!!) else emptyList(),
                    sha256 = if (computeHash && readableApk) sha256(apkFile!!) else null,
                    limitedAnalysisNote = if (!readableApk) {
                        "Base APK is not directly readable on this Android build; showing manifest & certificate metadata."
                    } else null,
                    signerInfo = signerInfo
                )
            } catch (t: Throwable) {
                StaticChecks(hashError = "Inspection failed: ${t.message ?: t.javaClass.simpleName}")
            }
        }

    private fun readNativeLibs(file: File): List<String> = try {
        ZipFile(file).use { zip ->
            val libs = linkedSetOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements() && libs.size < MAX_NATIVE_LIBS) {
                val name = entries.nextElement().name
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    libs += name.substringAfterLast('/')
                }
            }
            libs.sorted()
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun sha256(file: File): String? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var read = input.read(buffer)
                while (read > 0) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        const val MAX_UNPROTECTED_LIST = 12
        const val MAX_NATIVE_LIBS = 24
    }
}
