package com.xiaofeishu.audiostream.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaofeishu.audiostream.ui.component.LocalHapticFeedbackEnabled
import com.xiaofeishu.audiostream.ui.component.LargeTitle
import com.xiaofeishu.audiostream.ui.component.contextClick
import com.xiaofeishu.audiostream.viewmodel.HomeViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 设置主页：各分类入口，点击进入对应子页面
 * （视觉与触感 / 播放 / 后台保活 / 数据管理 / 关于）。
 */
@Composable
fun SettingsScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToVisualAndHaptics: () -> Unit = {},
    onNavigateToPlayback: () -> Unit = {},
    onNavigateToKeepAlive: () -> Unit = {},
    onNavigateToData: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    val savedServers by viewModel.savedServers.collectAsState()
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
        ) {
            LargeTitle(text = "设置")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                ArrowPreference(
                    title = "视觉与触感",
                    summary = "配色方案、深色模式、触感反馈",
                    onClick = {
                        haptic.contextClick(hapticEnabled)
                        onNavigateToVisualAndHaptics()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                ArrowPreference(
                    title = "播放",
                    summary = "延迟模式、链路延迟警告",
                    onClick = {
                        haptic.contextClick(hapticEnabled)
                        onNavigateToPlayback()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                ArrowPreference(
                    title = "后台保活",
                    summary = "电池优化",
                    onClick = {
                        haptic.contextClick(hapticEnabled)
                        onNavigateToKeepAlive()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                ArrowPreference(
                    title = "数据管理",
                    summary = "${savedServers.size} 个收藏 · 连接历史",
                    onClick = {
                        haptic.contextClick(hapticEnabled)
                        onNavigateToData()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                ArrowPreference(
                    title = "关于",
                    summary = "应用版本、检查更新与开源信息",
                    onClick = {
                        haptic.contextClick(hapticEnabled)
                        onNavigateToAbout()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
