package com.xiaofeishu.audiostream.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.xiaofeishu.audiostream.BuildConfig

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "关于",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 应用名称和版本
        ListItem(
            headlineContent = { Text("小废鼠 AudioStream") },
            supportingContent = { Text("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // GitHub 仓库
        ListItem(
            headlineContent = { Text("GitHub 仓库") },
            supportingContent = { Text("ymh0000123/AudioStream-Android") },
            trailingContent = {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
            },
            modifier = Modifier.clickable {
                openUrl(context, "https://github.com/ymh0000123/AudioStream-Android")
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 开源许可
        ListItem(
            headlineContent = { Text("开源许可") },
            supportingContent = { Text("Apache License 2.0") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 应用说明
        Text(
            text = "应用介绍",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "AudioStream 是一个 Android 原生音频流播放客户端，通过 WebSocket 协议连接到音频流服务器，接收 PCM 音频数据并实时播放。支持 mDNS 局域网自动发现、前台服务保活、自动重连、连接历史、收藏服务器等功能。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 技术栈
        Text(
            text = "技术栈",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "• Kotlin & Jetpack Compose\n" +
                    "• OkHttp WebSocket\n" +
                    "• Android AudioTrack\n" +
                    "• mDNS 服务发现\n" +
                    "• Hilt 依赖注入\n" +
                    "• DataStore 持久化",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
