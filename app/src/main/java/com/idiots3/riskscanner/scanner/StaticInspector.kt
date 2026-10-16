package com.idiots3.riskscanner.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.StaticChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Static APK facts for a single app, computed lazily when the detail screen opens
 * (parsing components for every installed package during a scan would be wasteful).
 */
class StaticInspector(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

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

                StaticChecks(
                    exportedActivities = packageInfo.activities?.count { it.exported } ?: 0,
                    exportedServices = packageInfo.services?.count { it.exported } ?: 0,
                    exportedReceivers = packageInfo.receivers?.count { it.exported } ?: 0,
                    exportedProviders = packageInfo.providers?.count { it.exported } ?: 0,
                    cleartextTraffic = (appFlags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0,
                    debuggable = (appFlags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                    targetSdk = info?.targetSdkVersion ?: 0,
                    minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) info?.minSdkVersion ?: 0 else 0,
                    sha256 = if (computeHash) sha256(File(app.apkPath)) else null
                )
            } catch (t: Throwable) {
                StaticChecks(hashError = "Inspection failed: ${t.message}")
            }
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
        } catch (t: Throwable) {
            null
        }
    }
}
