package com.livetube.tv.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.livetube.tv.BuildConfig
import com.livetube.tv.util.Constants
import com.livetube.tv.util.NetworkUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.security.MessageDigest

sealed interface InstallResult {
    data object Started : InstallResult
    data object PermissionRequired : InstallResult
    data class Failed(val message: String) : InstallResult
}

/** Downloads and verifies an update before handing it to Android's package installer. */
class UpdateInstaller(private val context: android.content.Context) {
    suspend fun downloadAndInstall(activity: Activity, release: GitHubRelease): InstallResult =
        withContext(Dispatchers.IO) download@{
            val asset = release.apkAsset()
                ?: return@download InstallResult.Failed("The release does not contain an APK")
            val initialUri = try {
                URI(asset.downloadUrl)
            } catch (_: Exception) {
                return@download InstallResult.Failed("The release URL is malformed")
            }
            if (!Constants.isGitHubDownloadHost(initialUri.host)) {
                return@download InstallResult.Failed("The release URL is not a GitHub asset")
            }

            val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
            val destination = File(updateDirectory, asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            try {
                val download = NetworkUtils.downloadToFile(
                    asset.downloadUrl,
                    destination,
                    headers = mapOf("Accept" to "application/vnd.android.package-archive"),
                )
                val finalUri = try {
                    URI(download.finalUrl)
                } catch (_: Exception) {
                    return@download InstallResult.Failed("The download redirect is malformed")
                }
                if (!Constants.isGitHubDownloadHost(finalUri.host)) {
                    destination.delete()
                    return@download InstallResult.Failed("The download left GitHub's asset hosts")
                }
                verifyPackage(download.file, release)
            } catch (error: CancellationException) {
                destination.delete()
                throw error
            } catch (error: Exception) {
                destination.delete()
                return@download InstallResult.Failed(
                    error.message?.takeIf { it.isNotBlank() } ?: "The update could not be verified",
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                withContext(Dispatchers.Main) {
                    try {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // The caller will report that installation could not be started.
                    }
                }
                return@download InstallResult.PermissionRequired
            }

            val uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destination,
                )
            } catch (_: Exception) {
                return@download InstallResult.Failed("The update file could not be shared")
            }
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return@download try {
                withContext(Dispatchers.Main) {
                    activity.startActivity(installIntent)
                }
                InstallResult.Started
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                InstallResult.Failed("This Android TV device has no package installer")
            }
        }

    private fun verifyPackage(file: File, release: GitHubRelease) {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IllegalArgumentException("The downloaded file is not a readable APK")
        require(archive.packageName == BuildConfig.APPLICATION_ID) {
            "The APK package identity does not match LiveTube TV"
        }
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        require(versionCode(archive) > versionCode(installed)) {
            "The downloaded APK is not newer than the installed app"
        }
        val releaseVersion = release.version
        val installedVersion = AppVersion.parse(BuildConfig.VERSION_NAME)
            ?: throw IllegalArgumentException("The installed version is malformed")
        require(releaseVersion == null || releaseVersion > installedVersion) {
            "The APK version does not match the release metadata"
        }
        val installedSignatures = signingCertificates(installed)
        val archiveSignatures = signingCertificates(archive)
        require(installedSignatures.isNotEmpty() && archiveSignatures.any { it in installedSignatures }) {
            "The APK signing certificate does not match this installation"
        }
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
}
