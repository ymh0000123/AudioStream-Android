package com.xiaofeishu.audiostream.ui.screen

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaofeishu.audiostream.BuildConfig
import com.xiaofeishu.audiostream.R
import com.xiaofeishu.audiostream.data.update.UpdateInfo
import com.xiaofeishu.audiostream.domain.model.ThemeMode
import com.xiaofeishu.audiostream.ui.component.LocalHapticFeedbackEnabled
import com.xiaofeishu.audiostream.ui.component.SteppedSlider
import com.xiaofeishu.audiostream.ui.component.contextClick
import com.xiaofeishu.audiostream.viewmodel.HomeViewModel
import com.xiaofeishu.audiostream.viewmodel.UpdateUiState
import com.xiaofeishu.audiostream.viewmodel.UpdateViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 播放延迟固定档位（ms 阈值）：0=关闭跳帧。 */
private val LATENCY_MODES = listOf(0, 100, 150, 200)
@Composable
fun SettingsScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    onNavigateToAbout: () -> Unit = {}
) {
    val savedServers by viewModel.savedServers.collectAsState()
    val showClearConfirm = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val globalHapticEnabled = LocalHapticFeedbackEnabled.current
    val latencyMode by viewModel.latencyMode.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val showThemePicker = remember { mutableStateOf(false) }
    val updateState by updateViewModel.uiState.collectAsState()
    var downloadOptions by remember { mutableStateOf<UpdateInfo?>(null) }

    // 电池优化豁免状态
    var batteryIgnored by remember { mutableStateOf(checkBatteryIgnored(context)) }
    // 页面重新可见时刷新状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryIgnored = checkBatteryIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hideSinkLatencyHint by viewModel.hideSinkLatencyHint.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
        ) {
            SmallTitle(text = "外观")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                ArrowPreference(
                    title = "配色方案",
                    summary = themeMode.displayName,
                    onClick = {
                        haptic.contextClick(globalHapticEnabled)
                        showThemePicker.value = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(text = "后台保活")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                ArrowPreference(
                    title = context.getString(R.string.battery_optimization),
                    summary = context.getString(R.string.battery_optimization_desc),
                    endActions = {
                        Text(
                            text = if (batteryIgnored) {
                                context.getString(R.string.battery_optimization_granted)
                            } else {
                                context.getString(R.string.battery_optimization_request)
                            }
                        )
                    },
                    onClick = if (batteryIgnored) {
                        null
                    } else {
                        {
                            val intent = Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                "package:${context.packageName}".toUri()
                            )
                            context.startActivity(intent)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(text = "播放")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp, 16.dp)
            ) {
                Text(
                    text = context.getString(R.string.latency_mode),
                    fontSize = MiuixTheme.textStyles.main.fontSize
                )
                SteppedSlider(
                    values = LATENCY_MODES,
                    currentValue = latencyMode,
                    onValueCommitted = { mode ->
                        haptic.contextClick(globalHapticEnabled)
                        viewModel.saveLatencyMode(mode)
                    },
                    valueLabel = { mode ->
                        when (mode) {
                            100 -> context.getString(R.string.latency_mode_low)
                            150 -> context.getString(R.string.latency_mode_balanced)
                            200 -> context.getString(R.string.latency_mode_stable)
                            else -> context.getString(R.string.latency_mode_disabled)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                SwitchPreference(
                    checked = hapticFeedbackEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setHapticFeedbackEnabled(enabled)
                        haptic.contextClick(globalHapticEnabled || enabled)
                    },
                    title = "触感反馈",
                    summary = if (hapticFeedbackEnabled) "主要操作时提供轻微震动" else "已关闭操作震动"
                )
                // 蓝牙链路延迟警告提示：忽略后可在此恢复
                SwitchPreference(
                    checked = !hideSinkLatencyHint,
                    onCheckedChange = { show ->
                        haptic.contextClick(globalHapticEnabled)
                        viewModel.setHideSinkLatencyHint(!show)
                    },
                    title = "链路延迟警告提示",
                    summary = if (hideSinkLatencyHint) {
                        "已忽略：蓝牙输出时不再显示链路延迟警告"
                    } else {
                        "蓝牙输出时在播放页显示链路延迟警告"
                    }
                )
                ArrowPreference(
                    title = "收藏的服务器",
                    summary = "${savedServers.size} 个",
                    onClick = null
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(text = "关于与更新")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                ArrowPreference(
                    title = context.getString(R.string.check_for_updates),
                    summary = if (updateState == UpdateUiState.Checking) {
                        context.getString(R.string.update_checking)
                    } else {
                        context.getString(R.string.current_version, BuildConfig.VERSION_NAME)
                    },
                    endActions = {
                        if (updateState != UpdateUiState.Checking) {
                            Text(text = context.getString(R.string.check_now))
                        }
                    },
                    enabled = updateState != UpdateUiState.Checking,
                    onClick = {
                        haptic.contextClick(globalHapticEnabled)
                        updateViewModel.checkForUpdates()
                    }
                )
                ArrowPreference(
                    title = "关于",
                    summary = "应用版本与开源信息",
                    onClick = {
                        haptic.contextClick(globalHapticEnabled)
                        onNavigateToAbout()
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 清除历史（真正调用 clearHistory，修复 saveVolume(80) bug）
            Button(
                onClick = {
                    haptic.contextClick(globalHapticEnabled)
                    showClearConfirm.value = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Text("清除连接历史")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    OverlayDialog(
        show = showClearConfirm.value,
        title = "清除连接历史",
        summary = "将删除全部连接历史记录，收藏的服务器不受影响。是否继续？",
        onDismissRequest = { showClearConfirm.value = false }
    ) {
        DialogButtonRow(
            cancelText = "取消",
            confirmText = "清除",
            onCancel = { showClearConfirm.value = false },
            onConfirm = {
                viewModel.clearHistory()
                showClearConfirm.value = false
            }
        )
    }
    ThemePickerDialog(
        show = showThemePicker.value,
        selected = themeMode,
        onDismiss = { showThemePicker.value = false },
        onSelected = { mode ->
            viewModel.saveThemeMode(mode)
            showThemePicker.value = false
        }
    )

    UpdateDialogs(
        context = context,
        updateState = updateState,
        downloadOptions = downloadOptions,
        onDownloadOptionsChange = { downloadOptions = it },
        updateViewModel = updateViewModel
    )
}

@Composable
private fun ThemePickerDialog(
    show: Boolean,
    selected: ThemeMode,
    onDismiss: () -> Unit,
    onSelected: (ThemeMode) -> Unit,
) {
    OverlayDialog(
        show = show,
        title = "配色方案",
        summary = "选择应用的强调色。系统取色在 Android 11 及以下回退为靛蓝紫。",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onSelected(mode) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(themeSwatch(mode), CircleShape)
                    )
                    Text(
                        text = mode.displayName,
                        modifier = Modifier.weight(1f),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                    )
                    if (mode == selected) {
                        Text(
                            text = "已选",
                            color = MiuixTheme.colorScheme.primary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        )
                    }
                }
            }
        }
    }
}

private fun themeSwatch(mode: ThemeMode): Color = when (mode) {
    ThemeMode.SYSTEM -> Color(0xFF607D8B)
    ThemeMode.INDIGO -> Color(0xFF667EEA)
    ThemeMode.OCEAN -> Color(0xFF1565C0)
    ThemeMode.TEAL -> Color(0xFF008577)
    ThemeMode.ORANGE -> Color(0xFFE65100)
    ThemeMode.PINK -> Color(0xFFC2185B)
}

/**
 * 更新相关弹窗。
 *
 * 关键约束：Miuix 0.9.1 的 OverlayDialog 基于独立的 show 布尔状态驱动，
 * 这里全程只使用一个 OverlayDialog 与一个 show 状态，内容按 [UpdateUiState] 分支渲染，
 * 避免同一帧出现多个弹窗互相干扰。
 */
@Composable
private fun UpdateDialogs(
    context: Context,
    updateState: UpdateUiState,
    downloadOptions: UpdateInfo?,
    onDownloadOptionsChange: (UpdateInfo?) -> Unit,
    updateViewModel: UpdateViewModel
) {
    val show = remember { mutableStateOf(false) }

    // 有内容可展示时才打开：下载方式选择优先于版本详情
    val hasContent = downloadOptions != null ||
        updateState is UpdateUiState.Available ||
        updateState is UpdateUiState.UpToDate ||
        updateState is UpdateUiState.Error
    LaunchedEffect(hasContent) {
        show.value = hasContent
    }

    // 统一关闭入口：复位 show 状态 + 清业务状态
    val dismissAll: () -> Unit = {
        show.value = false
        onDownloadOptionsChange(null)
        updateViewModel.dismissResult()
    }

    if (!hasContent) return

    val selectedDownload = downloadOptions
    val title = when {
        selectedDownload != null -> context.getString(R.string.download_method_title)
        updateState is UpdateUiState.Available ->
            context.getString(R.string.update_available_title)
        updateState is UpdateUiState.UpToDate ->
            context.getString(R.string.already_latest_title)
        else -> context.getString(R.string.update_check_failed_title)
    }

    OverlayDialog(
        show = show.value,
        title = title,
        onDismissRequest = dismissAll
    ) {
        when {
            selectedDownload != null -> DownloadMethodContent(
                context = context,
                info = selectedDownload,
                onPicked = { url ->
                    openUrl(context, url)
                    dismissAll()
                },
                onCancel = dismissAll
            )

            updateState is UpdateUiState.Available -> AvailableContent(
                context = context,
                info = updateState.info,
                onCancel = dismissAll,
                onConfirm = {
                    if (updateState.info.mirrorDownloadUrl == null) {
                        openUrl(context, updateState.info.downloadUrl)
                        dismissAll()
                    } else {
                        // 切到下载方式选择：复用同一个弹窗，不新建 SuperDialog
                        onDownloadOptionsChange(updateState.info)
                    }
                }
            )

            updateState is UpdateUiState.UpToDate -> Column {
                Text(
                    text = context.getString(
                        R.string.already_latest_desc,
                        BuildConfig.VERSION_NAME
                    ),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = dismissAll,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.close))
                }
            }

            updateState is UpdateUiState.Error -> Column {
                Text(
                    text = updateState.message,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(16.dp))
                DialogButtonRow(
                    cancelText = context.getString(R.string.close),
                    confirmText = context.getString(R.string.retry),
                    onCancel = dismissAll,
                    onConfirm = {
                        show.value = false
                        updateViewModel.checkForUpdates()
                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadMethodContent(
    context: Context,
    info: UpdateInfo,
    onPicked: (String) -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = context.getString(R.string.download_method_desc),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(4.dp))
        info.mirrorDownloadUrl?.let { mirrorUrl ->
            Button(
                onClick = { onPicked(mirrorUrl) },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.download_via_mirror))
            }
        }
        Button(
            onClick = { onPicked(info.downloadUrl) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(context.getString(R.string.download_via_github))
        }
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(context.getString(R.string.cancel))
        }
    }
}

@Composable
private fun AvailableContent(
    context: Context,
    info: UpdateInfo,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column {
        Column(
            modifier = Modifier
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(context.getString(R.string.latest_version, info.versionName))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = context.getString(R.string.update_notes),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = info.releaseNotes.ifBlank {
                    context.getString(R.string.no_release_notes)
                },
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        DialogButtonRow(
            cancelText = context.getString(R.string.cancel),
            confirmText = context.getString(R.string.download_update),
            onCancel = onCancel,
            onConfirm = onConfirm
        )
    }
}

/** OverlayDialog 没有内置按钮区，统一封装左取消右确认的按钮行。 */
@Composable
private fun DialogButtonRow(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        ) {
            Text(cancelText)
        }
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.weight(1f)
        ) {
            Text(confirmText)
        }
    }
}

private fun checkBatteryIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
