package com.xiaofeishu.audiostream.data.dto

import com.google.gson.annotations.SerializedName

data class GithubReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val pageUrl: String,
    @SerializedName("assets") val assets: List<GithubReleaseAssetDto> = emptyList()
)

data class GithubReleaseAssetDto(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String
)
