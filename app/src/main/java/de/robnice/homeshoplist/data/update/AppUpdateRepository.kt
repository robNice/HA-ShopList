package de.robnice.homeshoplist.data.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.robnice.homeshoplist.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val tagName: String,
    val apkDownloadUrl: String,
    val releaseUrl: String,
    val changelog: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val update: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
}

enum class InstallStartResult {
    Started,
    NeedsInstallPermission
}

class AppUpdateRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val releaseAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(GithubRelease::class.java)

    fun isGithubUpdaterAllowed(): Boolean {
        val installerPackage = getInstallerPackageName()
        return installerPackage == null ||
                installerPackage !in setOf(PLAY_STORE_PACKAGE, FDROID_PACKAGE)
    }

    fun getInstallerPackageName(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager
                    .getInstallSourceInfo(context.packageName)
                    .installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun checkLatestRelease(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HA-ShopList/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub returned HTTP ${response.code}")
            }

            val body = response.body.string()
            val release = releaseAdapter.fromJson(body)
                ?: throw IOException("GitHub release response could not be parsed")

            val versionName = release.tagName.removePrefix("v").removePrefix("V")
            if (compareVersions(versionName, BuildConfig.VERSION_NAME) <= 0) {
                return@withContext UpdateCheckResult.UpToDate
            }

            val apkAsset = release.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true)
            } ?: throw IOException("Latest GitHub release does not contain an APK asset")

            UpdateCheckResult.UpdateAvailable(
                AppUpdateInfo(
                    versionName = versionName,
                    tagName = release.tagName,
                    apkDownloadUrl = apkAsset.browserDownloadUrl,
                    releaseUrl = release.htmlUrl,
                    changelog = release.body.orEmpty().trim()
                )
            )
        }
    }

    suspend fun downloadApk(update: AppUpdateInfo): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(update.apkDownloadUrl)
            .header("User-Agent", "HA-ShopList/${BuildConfig.VERSION_NAME}")
            .build()

        val updateDir = File(context.cacheDir, "updates").apply {
            mkdirs()
        }
        val target = File(updateDir, "ha-shoplist-${update.tagName}.apk")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("APK download failed with HTTP ${response.code}")
            }

            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        target
    }

    fun startInstall(activity: Activity, apkFile: File): InstallStartResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(settingsIntent)
            return InstallStartResult.NeedsInstallPermission
        }

        val apkUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        activity.startActivity(intent)
        return InstallStartResult.Started
    }

    companion object {
        const val UPDATE_CHECK_INTERVAL_MILLIS: Long = 24L * 60L * 60L * 1000L

        private const val PLAY_STORE_PACKAGE = "com.android.vending"
        private const val FDROID_PACKAGE = "org.fdroid.fdroid"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/robNice/HA-ShopList/releases/latest"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

private data class GithubRelease(
    @param:Json(name = "tag_name") val tagName: String,
    @param:Json(name = "html_url") val htmlUrl: String,
    val body: String?,
    val assets: List<GithubAsset>
)

private data class GithubAsset(
    val name: String,
    @param:Json(name = "browser_download_url") val browserDownloadUrl: String
)

private fun compareVersions(left: String, right: String): Int {
    val leftParts = left.toVersionParts()
    val rightParts = right.toVersionParts()
    val count = maxOf(leftParts.size, rightParts.size)

    for (index in 0 until count) {
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }
        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }

    return 0
}

private fun String.toVersionParts(): List<Int> {
    return trim()
        .substringBefore("-")
        .split(".")
        .map { part -> part.toIntOrNull() ?: 0 }
}
