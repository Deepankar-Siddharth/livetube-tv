package com.livetube.tv.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.livetube.tv.BuildConfig
import java.io.File
import java.security.MessageDigest

sealed interface InstallResult {
    data object Started : InstallResult
    data object PermissionRequired : InstallResult
    data class Failed(val message: String) : InstallResult
}

sealed interface DownloadResult {
    data class Ready(val file: File) : DownloadResult
    data object PermissionRequired : DownloadResult
    data class Failed(val message: String) : DownloadResult
}

/**
 * Downloads, verifies and installs a release APK.
 *
 * The download itself is delegated to [ApkDownloader]; every identity check that made the
 * original installer safe is kept here: GitHub asset hosts only, a newer versionCode, the
 * expected package name, and a signing certificate that matches the installed app.
 */
class UpdateInstaller(
    private val context: Context,
    private val downloader: ApkDownloader = ApkDownloader(context),
) {
    suspend fun download(
        release: GitHubRelease,
        onProgress: (ApkDownloadProgress) -> Unit = {},
    ): DownloadResult {
        val asset = release.apkAsset()
            ?: return DownloadResult.Failed("This release does not include an app update")
        if (hasInstallPermission()) {
            val downloaded = try {
                downloader.download(asset, onProgress)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                return DownloadResult.Failed(error.message ?: "The update could not be downloaded")
            }
            val verification = verifyPackage(downloaded.file, release)
            if (verification != null) return DownloadResult.Failed(verification)
            return DownloadResult.Ready(downloaded.file)
        }
        return DownloadResult.PermissionRequired
    }

    suspend fun requestInstallPermission(activity: Activity) {
        if (hasInstallPermission()) return
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } catch (_: Exception) {
            // The caller reports that the permission screen could not be opened.
        }
    }

    /**
     * Builds the intent that hands a verified APK to the Android package installer.
     *
     * The caller launches it, which keeps the result reporting (and any "unknown sources"
     * permission screen) in one place.
     */
    fun installIntent(file: File): Intent? {
        if (!hasInstallPermission()) return null
        if (!file.isFile || file.length() <= 0L) return null
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            return null
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Installs an already downloaded and verified APK, downloading it first if needed. */
    suspend fun downloadAndInstall(
        activity: Activity,
        release: GitHubRelease,
        onProgress: (ApkDownloadProgress) -> Unit = {},
    ): InstallResult = when (val result = download(release, onProgress)) {
        is DownloadResult.Ready -> {
            val intent = installIntent(result.file)
            if (intent == null) {
                InstallResult.Failed("The update could not be opened for installation")
            } else {
                try {
                    activity.startActivity(intent)
                    InstallResult.Started
                } catch (_: Exception) {
                    InstallResult.Failed("This Android TV device has no package installer")
                }
            }
        }

        DownloadResult.PermissionRequired -> {
            requestInstallPermission(activity)
            InstallResult.PermissionRequired
        }

        is DownloadResult.Failed -> InstallResult.Failed(result.message)
    }

    /** Reads the versionCode of a downloaded APK so the caller can confirm an installation. */
    fun versionCodeOf(file: File): Long {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return 0L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archive.versionCode.toLong()
        }
    }

    private fun hasInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /** Returns a user-facing reason when the downloaded APK must not be installed. */
    private fun verifyPackage(file: File, release: GitHubRelease): String? {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "The downloaded update is not a readable app package"
        if (archive.packageName != BuildConfig.APPLICATION_ID) {
            return "The downloaded update does not match LiveTube TV"
        }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        if (versionCode(archive) <= versionCode(installed)) {
            return "The downloaded update is not newer than the installed app"
        }
        val releaseVersion = release.version
        val installedVersion = AppVersion.parse(BuildConfig.VERSION_NAME)
        if (installedVersion == null || (releaseVersion != null && releaseVersion <= installedVersion)) {
            return "The downloaded update does not match the release"
        }
        val installedSignatures = signingCertificates(installed)
        val archiveSignatures = signingCertificates(archive)
        if (installedSignatures.isEmpty() || archiveSignatures.none { it in installedSignatures }) {
            return "The downloaded update is not signed by the LiveTube TV developer"
        }
        return null
    }

    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

    private fun signingCertificates(info: PackageInfo): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty()
        }
        return certificates.mapNotNull { certificate ->
            runCatching {
                MessageDigest.getInstance("SHA-256")
                    .digest(certificate.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }.getOrNull()
        }.toSet()
    }

    companion object {
        /** User-facing message for a device without the "unknown sources" setting. */
        const val ALLOW_INSTALL_MESSAGE =
            "Allow installation permission for LiveTube TV and try again."
    }
}
