package com.xiaofeishu.audiostream.ui.screen

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaofeishu.audiostream.R
import com.xiaofeishu.audiostream.domain.model.DarkMode
import com.xiaofeishu.audiostream.domain.model.ThemeMode
import com.xiaofeishu.audiostream.ui.component.LocalHapticFeedbackEnabled
import com.xiaofeishu.audiostream.ui.component.LargeTitle
import com.xiaofeishu.audiostream.ui.component.SteppedSlider
import com.xiaofeishu.audiostream.ui.component.contextClick
import com.xiaofeishu.audiostream.viewmodel.HomeViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 播放延迟固定档位（ms 阈值）：0=关闭跳帧。 */
private val LATENCY_MODES = listOf(0, 100, 150, 200)

/**
 * 设置子页面的统一外壳：带返回键的顶栏（不显示顶栏标题，大标题在内容区展示）
 * + 可滚动内容列。
 *
 * 与 AboutScreen 一样是二级页面（外层隐藏底部导航栏、padding.bottom 为 0），
 * 底部系统手势条 inset 由这里自己吃，避免最后一项被遮挡。
 */
@Composable
private fun SettingsSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.contextClick(hapticEnabled)
                            onBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
                .padding(
                    bottom = padding.calculateBottomPadding() +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                )
        ) {
            LargeTitle(text = title)
            content()
        }
    }
}

/** 视觉与触感设置：配色方案、深色模式、触感反馈。 */
@Composable
fun VisualAndHapticsSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current
    val themeMode by viewModel.themeMode.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val showThemePicker = remember { mutableStateOf(false) }
    val showDarkModePicker = remember { mutableStateOf(false) }

    SettingsSubPageScaffold(title = "视觉与触感", onBack = onBack) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            ArrowPreference(
                title = "配色方案",
                summary = themeMode.displayName,
                onClick = {
                    haptic.contextClick(hapticEnabled)
                    showThemePicker.value = true
                }
            )
            ArrowPreference(
                title = "深色模式",
                summary = darkMode.displayName,
                onClick = {
                    haptic.contextClick(hapticEnabled)
                    showDarkModePicker.value = true
                }
            )
            SwitchPreference(
                checked = hapticFeedbackEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setHapticFeedbackEnabled(enabled)
                    haptic.contextClick(hapticEnabled || enabled)
                },
                title = "触感反馈",
                summary = if (hapticFeedbackEnabled) "主要操作时提供轻微震动" else "已关闭操作震动"
            )
        }
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
    DarkModePickerDialog(
        show = showDarkModePicker.value,
        selected = darkMode,
        onDismiss = { showDarkModePicker.value = false },
        onSelected = { mode ->
            viewModel.saveDarkMode(mode)
            showDarkModePicker.value = false
        }
    )
}

/** 播放设置：延迟模式、链路延迟警告提示。 */
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current
    val context = LocalContext.current
    val latencyMode by viewModel.latencyMode.collectAsState()
    val hideSinkLatencyHint by viewModel.hideSinkLatencyHint.collectAsState()

    SettingsSubPageScaffold(title = "播放", onBack = onBack) {
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
                    haptic.contextClick(hapticEnabled)
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
            // 蓝牙链路延迟警告提示：忽略后可在此恢复
            SwitchPreference(
                checked = !hideSinkLatencyHint,
                onCheckedChange = { show ->
                    haptic.contextClick(hapticEnabled)
                    viewModel.setHideSinkLatencyHint(!show)
                },
                title = "链路延迟警告提示",
                summary = if (hideSinkLatencyHint) {
                    "已忽略：蓝牙输出时不再显示链路延迟警告"
                } else {
                    "蓝牙输出时在播放页显示链路延迟警告"
                }
            )
        }
    }
}

/** 后台保活设置：电池优化豁免。 */
@Composable
fun KeepAliveSettingsScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current

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

    SettingsSubPageScaffold(title = "后台保活", onBack = onBack) {
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
    }
}

/** 数据管理：收藏的服务器、连接历史、清除连接历史。 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current
    val savedServers by viewModel.savedServers.collectAsState()
    val history by viewModel.history.collectAsState()
    val showClearConfirm = remember { mutableStateOf(false) }

    SettingsSubPageScaffold(title = "数据管理", onBack = onBack) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            ArrowPreference(
                title = "收藏的服务器",
                summary = "${savedServers.size} 个",
                onClick = null
            )
            ArrowPreference(
                title = "连接历史",
                summary = "${history.size} 条记录 · 管理收藏与连接记录",
                onClick = {
                    haptic.contextClick(hapticEnabled)
                    onNavigateToHistory()
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 清除历史（真正调用 clearHistory，修复 saveVolume(80) bug）
        Button(
            onClick = {
                haptic.contextClick(hapticEnabled)
                showClearConfirm.value = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Text("清除连接历史")
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

@Composable
private fun DarkModePickerDialog(
    show: Boolean,
    selected: DarkMode,
    onDismiss: () -> Unit,
    onSelected: (DarkMode) -> Unit,
) {
    OverlayDialog(
        show = show,
        title = "深色模式",
        summary = "选择应用的明暗外观。",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DarkMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onSelected(mode) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

/** OverlayDialog 没有内置按钮区，统一封装左取消右确认的按钮行。 */
@Composable
internal fun DialogButtonRow(
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
