package com.xiaofeishu.audiostream.data.update

import com.google.gson.Gson
import com.xiaofeishu.audiostream.BuildConfig
import com.xiaofeishu.audiostream.data.dto.GithubReleaseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val mirrorDownloadUrl: String?
)

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data class UpToDate(val latestVersion: String) : UpdateCheckResult
}

@Singleton
class UpdateChecker @Inject constructor(
    client: OkHttpClient,
    private val gson: Gson
) {
    private val httpClient = client.newBuilder()
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.UPDATE_API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "AudioStream-Android/${BuildConfig.VERSION_NAME}")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 403) {
                return@withContext checkReleasePage(currentVersion)
            }
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    404 -> "暂未找到已发布的版本"
                    else -> "更新服务器返回错误 ${response.code}"
                }
                throw IOException(message)
            }

            val body = response.body?.string()
                ?: throw IOException("更新服务器返回了空内容")
            val release = runCatching {
                gson.fromJson(body, GithubReleaseDto::class.java)
            }.getOrElse { throw IOException("无法解析版本信息", it) }

            val latestVersion = release.tagName.trim().removePrefix("v").removePrefix("V")
            if (latestVersion.isBlank()) {
                throw IOException("最新版本号为空")
            }

            if (VersionComparator.compare(latestVersion, currentVersion) > 0) {
                val apkUrl = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                }?.downloadUrl
                UpdateCheckResult.Available(
                    UpdateInfo(
                        versionName = latestVersion,
                        releaseNotes = release.body.orEmpty().trim(),
                        downloadUrl = apkUrl ?: release.pageUrl,
                        mirrorDownloadUrl = apkUrl?.let(::buildGithubMirrorUrl)
                    )
                )
            } else {
                UpdateCheckResult.UpToDate(latestVersion)
            }
        }
    }

    /** GitHub API 被共享出口限流时，通过 /releases/latest 的重定向地址获取版本号。 */
    private fun checkReleasePage(currentVersion: String): UpdateCheckResult {
        val request = Request.Builder()
            .url(BuildConfig.UPDATE_RELEASES_URL)
            .header("User-Agent", "AudioStream-Android/${BuildConfig.VERSION_NAME}")
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("无法访问版本发布页 (${response.code})")
            }
            val finalUrl = response.request.url
            val tagIndex = finalUrl.pathSegments.indexOfLast { it == "tag" }
            val tagName = finalUrl.pathSegments.getOrNull(tagIndex + 1)
                ?.removePrefix("v")
                ?.removePrefix("V")
                ?.takeIf(String::isNotBlank)
                ?: throw IOException("暂未找到已发布的版本")

            if (VersionComparator.compare(tagName, currentVersion) > 0) {
                UpdateCheckResult.Available(
                    UpdateInfo(
                        versionName = tagName,
                        releaseNotes = "",
                        downloadUrl = BuildConfig.UPDATE_LATEST_APK_URL,
                        mirrorDownloadUrl = buildGithubMirrorUrl(BuildConfig.UPDATE_LATEST_APK_URL)
                    )
                )
            } else {
                UpdateCheckResult.UpToDate(tagName)
            }
        }
    }
}

internal fun buildGithubMirrorUrl(downloadUrl: String): String =
    BuildConfig.GITHUB_MIRROR_PREFIX + downloadUrl

internal object VersionComparator {
    fun compare(left: String, right: String): Int {
        val leftVersion = ParsedVersion.parse(left)
        val rightVersion = ParsedVersion.parse(right)

        val maxSize = maxOf(leftVersion.numbers.size, rightVersion.numbers.size)
        repeat(maxSize) { index ->
            val result = (leftVersion.numbers.getOrNull(index) ?: 0)
                .compareTo(rightVersion.numbers.getOrNull(index) ?: 0)
            if (result != 0) return result
        }

        val leftPreRelease = leftVersion.preRelease
        val rightPreRelease = rightVersion.preRelease
        if (leftPreRelease == null && rightPreRelease != null) return 1
        if (leftPreRelease != null && rightPreRelease == null) return -1
        if (leftPreRelease == null) return 0

        val maxPreReleaseSize = maxOf(leftPreRelease.size, rightPreRelease!!.size)
        repeat(maxPreReleaseSize) { index ->
            val leftPart = leftPreRelease.getOrNull(index) ?: return -1
            val rightPart = rightPreRelease.getOrNull(index) ?: return 1
            val leftNumber = leftPart.toIntOrNull()
            val rightNumber = rightPart.toIntOrNull()
            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> leftPart.compareTo(rightPart, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return 0
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val preRelease: List<String>?
    ) {
        companion object {
            fun parse(raw: String): ParsedVersion {
                val normalized = raw.trim()
                    .removePrefix("v")
                    .removePrefix("V")
                    .substringBefore('+')
                val core = normalized.substringBefore('-')
                val numbers = core.split('.').map { part ->
                    part.takeWhile(Char::isDigit).toIntOrNull() ?: 0
                }
                val preRelease = normalized.substringAfter('-', missingDelimiterValue = "")
                    .takeIf(String::isNotBlank)
                    ?.split('.')
                return ParsedVersion(numbers, preRelease)
            }
        }
    }
}
