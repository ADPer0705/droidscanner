package com.idiots3.riskscanner.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.idiots3.riskscanner.model.AppInfo
import com.idiots3.riskscanner.model.HiddenPayload
import com.idiots3.riskscanner.model.SignerInfo
import com.idiots3.riskscanner.model.StaticChecks
import com.idiots3.riskscanner.model.UnprotectedComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Static APK, split-APK, container-hygiene, signing, and streaming mini-DEX inspector
 * run lazily when App Detail or Pre-Install APK Scanner opens.
 * Never loads entire APKs into RAM; falls back cleanly to metadata-only checks if OEM
 * restrictions block reading another app's APK path.
 */
class StaticInspector(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val certificateInspector = CertificateInspector(packageManager)
    private val dexInspector = DexInspector(context)

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

                val launcherActivity = runCatching {
                    packageManager.getLaunchIntentForPackage(app.packageName)?.component?.className
                }.getOrNull()

                val apkFile = if (app.apkPath.isNotBlank()) File(app.apkPath) else null
                val signerInfo = certificateInspector.inspect(app.packageName, app.apkPath)
                inspectPackageAndFile(
                    packageInfo = packageInfo,
                    apkFile = apkFile,
                    splitPaths = packageInfo.applicationInfo?.splitSourceDirs?.toList().orEmpty(),
                    launcherActivity = launcherActivity,
                    signerInfo = signerInfo,
                    runDexAnalysis = !app.isSystemApp,
                    computeHash = computeHash,
                    fallbackTargetSdk = app.targetSdk,
                    fallbackMinSdk = app.minSdk
                )
            } catch (t: Throwable) {
                StaticChecks(hashError = "Inspection failed: ${t.message ?: t.javaClass.simpleName}")
            }
        }

    /** Used by [ApkFileScanner] for uninstalled `.apk` files picked via Storage Access Framework. */
    suspend fun inspectArchive(
        apkFile: File,
        packageInfo: PackageInfo?,
        signerInfo: SignerInfo
    ): StaticChecks = withContext(Dispatchers.IO) {
        inspectPackageAndFile(
            packageInfo = packageInfo,
            apkFile = apkFile,
            splitPaths = emptyList(),
            launcherActivity = null,
            signerInfo = signerInfo,
            runDexAnalysis = true,
            computeHash = true,
            fallbackTargetSdk = packageInfo?.applicationInfo?.targetSdkVersion ?: 0,
            fallbackMinSdk = packageInfo?.applicationInfo?.minSdkVersion ?: 0
        )
    }

    private fun inspectPackageAndFile(
        packageInfo: PackageInfo?,
        apkFile: File?,
        splitPaths: List<String>,
        launcherActivity: String?,
        signerInfo: SignerInfo,
        runDexAnalysis: Boolean,
        computeHash: Boolean,
        fallbackTargetSdk: Int,
        fallbackMinSdk: Int
    ): StaticChecks {
        val info = packageInfo?.applicationInfo
        val appFlags = info?.flags ?: 0

        val activities = packageInfo?.activities.orEmpty()
        val services = packageInfo?.services.orEmpty()
        val receivers = packageInfo?.receivers.orEmpty()
        val providers = packageInfo?.providers.orEmpty()

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

        val readableApk = apkFile != null && apkFile.exists() && apkFile.canRead()
        val container = if (readableApk) inspectContainer(apkFile!!) else ContainerFindings()

        val allNativeLibs = linkedSetOf<String>()
        allNativeLibs += container.nativeLibs
        for (splitPath in splitPaths) {
            if (allNativeLibs.size >= MAX_NATIVE_LIBS) break
            val splitFile = File(splitPath)
            if (splitFile.exists() && splitFile.canRead()) {
                allNativeLibs += readNativeLibsOnly(splitFile, MAX_NATIVE_LIBS - allNativeLibs.size)
            }
        }

        val packerCatalog = DexInspector.loadPackerMap(context)
        val detectedPackers = allNativeLibs.mapNotNull { lib ->
            packerCatalog[lib]?.let { packerName -> "$packerName ($lib)" }
        }.distinct()

        val dexResult = if (readableApk && runDexAnalysis) {
            dexInspector.inspectApk(apkFile!!)
        } else null

        return StaticChecks(
            exportedActivities = activities.count { it.exported },
            exportedServices = services.count { it.exported },
            exportedReceivers = receivers.count { it.exported },
            exportedProviders = providers.count { it.exported },
            unprotectedExportedActivities = unprotectedActivitiesList.size,
            unprotectedExportedServices = unprotectedServicesList.size,
            unprotectedExportedReceivers = unprotectedReceiversList.size,
            unprotectedExportedProviders = unprotectedProvidersList.size,
            unprotectedComponents = unprotectedComponents,
            cleartextTraffic = if (info != null) (appFlags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0 else null,
            debuggable = (appFlags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            allowBackup = (appFlags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0,
            testOnly = (appFlags and ApplicationInfo.FLAG_TEST_ONLY) != 0,
            targetSdk = info?.targetSdkVersion ?: fallbackTargetSdk,
            minSdk = info?.minSdkVersion ?: fallbackMinSdk,
            apkSizeBytes = if (readableApk) apkFile!!.length() else 0L,
            splitApkCount = splitPaths.size,
            nativeLibraries = allNativeLibs.sorted(),
            sha256 = if (computeHash && readableApk) sha256(apkFile!!) else null,
            limitedAnalysisNote = if (!readableApk) {
                "Base APK is not directly readable on this Android build; showing manifest & certificate metadata."
            } else null,
            signerInfo = signerInfo,
            hiddenPayloads = container.hiddenPayloads,
            packerLibs = detectedPackers,
            zipAnomalies = container.zipAnomalies,
            dexAnalysis = dexResult
        )
    }

    private data class ContainerFindings(
        val nativeLibs: List<String> = emptyList(),
        val hiddenPayloads: List<HiddenPayload> = emptyList(),
        val zipAnomalies: List<String> = emptyList()
    )

    private fun inspectContainer(file: File): ContainerFindings = try {
        ZipFile(file).use { zip ->
            val libs = linkedSetOf<String>()
            val hiddenPayloads = mutableListOf<HiddenPayload>()
            val zipAnomalies = linkedSetOf<String>()
            val seenCriticalNames = hashSetOf<String>()
            val magicBuf = ByteArray(4)

            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                if (name.contains("../") || name.startsWith('/')) {
                    zipAnomalies += "Path-traversal ZIP entry: $name"
                }

                if (name == "AndroidManifest.xml" || name == "classes.dex") {
                    if (!seenCriticalNames.add(name)) {
                        zipAnomalies += "Duplicate critical entry: $name"
                    }
                }

                if (name.startsWith("lib/") && name.endsWith(".so") && libs.size < MAX_NATIVE_LIBS) {
                    libs += name.substringAfterLast('/')
                }

                // Inspect magic bytes (first 4 bytes only) of assets/ and res/raw/ for hidden payloads.
                if (!entry.isDirectory &&
                    hiddenPayloads.size < MAX_HIDDEN_PAYLOADS &&
                    entry.size >= 32L &&
                    (name.startsWith("assets/") || name.startsWith("res/raw/"))
                ) {
                    val read = runCatching {
                        zip.getInputStream(entry).use { it.read(magicBuf) }
                    }.getOrDefault(0)
                    if (read == 4) {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        when {
                            isDexMagic(magicBuf) -> hiddenPayloads += HiddenPayload(name, "Hidden DEX bytecode")
                            isElfMagic(magicBuf) -> hiddenPayloads += HiddenPayload(name, "Hidden native ELF binary")
                            isZipMagic(magicBuf) && (ext == "apk" || ext !in BENIGN_ZIP_ASSET_EXTS) -> {
                                val kind = if (ext == "apk") "Embedded APK archive" else "Disguised ZIP/APK archive"
                                hiddenPayloads += HiddenPayload(name, kind)
                            }
                        }
                    }
                }
            }
            ContainerFindings(
                nativeLibs = libs.sorted(),
                hiddenPayloads = hiddenPayloads,
                zipAnomalies = zipAnomalies.toList()
            )
        }
    } catch (_: Throwable) {
        ContainerFindings()
    }

    private fun readNativeLibsOnly(file: File, maxAdd: Int): List<String> = try {
        ZipFile(file).use { zip ->
            val libs = linkedSetOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements() && libs.size < maxAdd) {
                val name = entries.nextElement().name
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    libs += name.substringAfterLast('/')
                }
            }
            libs.toList()
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun isDexMagic(b: ByteArray): Boolean =
        b[0] == 0x64.toByte() && b[1] == 0x65.toByte() && b[2] == 0x78.toByte() && b[3] == 0x0A.toByte()

    private fun isElfMagic(b: ByteArray): Boolean =
        b[0] == 0x7F.toByte() && b[1] == 0x45.toByte() && b[2] == 0x4C.toByte() && b[3] == 0x46.toByte()

    private fun isZipMagic(b: ByteArray): Boolean =
        b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() && b[2] == 0x03.toByte() && b[3] == 0x04.toByte()

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
        const val MAX_HIDDEN_PAYLOADS = 8
        val BENIGN_ZIP_ASSET_EXTS = setOf("zip", "jar", "docx", "xlsx", "pptx", "epub", "odt", "kmz", "aar")
    }
}
