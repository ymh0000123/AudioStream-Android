package com.xiaofeishu.audiostream.ui.screen

import android.content.Intent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaofeishu.audiostream.BuildConfig
import com.xiaofeishu.audiostream.R
import com.xiaofeishu.audiostream.data.update.UpdateInfo
import com.xiaofeishu.audiostream.ui.component.LocalEnableBlur
import com.xiaofeishu.audiostream.ui.component.LocalHapticFeedbackEnabled
import com.xiaofeishu.audiostream.ui.component.contextClick
import com.xiaofeishu.audiostream.ui.component.rememberBlurBackdrop
import com.xiaofeishu.audiostream.ui.effect.BgEffectBackground
import com.xiaofeishu.audiostream.viewmodel.UpdateUiState
import com.xiaofeishu.audiostream.viewmodel.UpdateViewModel
import kotlinx.coroutines.flow.onEach
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import java.util.Locale

private const val REPO_URL = "https://github.com/ymh0000123/AudioStream-Android"
private const val RELEASES_URL = "$REPO_URL/releases"
private const val LICENSE_URL = "https://opensource.org/licenses/MIT"

/**
 * AudioStream 关于页：
 *
 * - 顶部 Hero 区(大号项目名 + 版本号 + 仓库地址)悬浮在流光背景上，
 *   随列表滚动分两阶段淡出/缩小(先版本号后项目名)。
 * - 顶栏初始透明，Hero 区滚完后渐显标题与背景色。
 * - 卡片为毛玻璃材质(textureBlur)，低版本或设备不支持时回退纯色。
 * - 列表项使用 HyperOS 风格 ArrowPreference。
 * - 双击 Hero 区可切换 OS2/OS3 两套流光 shader 效果。
 * - "版本发布"行展示最新版本检查结果(复用本项目的 UpdateViewModel)。
 *
 * 依赖 miuix 0.9.1 的 blur 与 preference 模块。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val enableBlur = LocalEnableBlur.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    // 进入页面即触发一次更新检查。
    LaunchedEffect(Unit) {
        updateViewModel.checkForUpdates()
    }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "",
                scrollBehavior = topAppBarScrollBehavior,
                modifier =
                    if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop)
                    else Modifier,
                color =
                    if (blurBackdrop != null) Color.Transparent
                    else colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f),
                titleColor = Color.Transparent,
                defaultWindowInsetsPadding = false,
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
    ) { innerPadding ->
        AboutContent(
            padding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding(),
            ),
            enableBlur = enableBlur,
            lazyListState = lazyListState,
            scrollProgress = scrollProgress,
            onLogoHeightChanged = { logoHeightPx = it },
            updateViewModel = updateViewModel,
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    enableBlur: Boolean,
    lazyListState: LazyListState,
    scrollProgress: Float,
    onLogoHeightChanged: (Int) -> Unit,
    updateViewModel: UpdateViewModel,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current
    val updateState by updateViewModel.uiState.collectAsState()
    val backdrop = rememberLayerBackdrop()
    var isOs3Effect by remember { mutableStateOf(true) }
    val blurEnabled = remember(enableBlur) { enableBlur && isRenderEffectSupported() }
    val isDark = colorScheme.background.luminance() < 0.5f
    val heroBlendColors = remember(isDark) { heroBlendColors(isDark) }
    val cardBlendColors = remember(isDark) { aboutCardBlendToken(isDark) }

    // 检查更新：弹窗只响应用户手动触发（进入页面时的自动检查只更新"版本发布"行状态，不弹窗）
    var downloadOptions by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateDialogRequested by remember { mutableStateOf(false) }

    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }
    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    // 版本发布行右侧状态文本：检查中 "…"、有结果展示最新版本、失败展示错误
    val currentUpdateState = updateState
    val updateStatusText = when (currentUpdateState) {
        UpdateUiState.Idle -> null
        UpdateUiState.Checking -> "检查中…"
        is UpdateUiState.Available -> "发现新版本 ${currentUpdateState.info.versionName}"
        is UpdateUiState.UpToDate -> "已是最新"
        is UpdateUiState.Error -> "检查失败"
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .onEach { offset ->
                if (lazyListState.firstVisibleItemIndex > 0) {
                    projectNameProgress = 1f
                    versionCodeProgress = 1f
                    return@onEach
                }
                if (initialLogoAreaY == 0f && logoAreaY > 0f) {
                    initialLogoAreaY = logoAreaY
                }
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY
                val stage1TotalLength = refLogoAreaY - versionCodeY
                val stage2TotalLength = versionCodeY - projectNameY
                val versionCodeDelay = stage1TotalLength * 0.5f
                versionCodeProgress =
                    ((offset.toFloat() - versionCodeDelay) / (stage1TotalLength - versionCodeDelay)
                        .coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                projectNameProgress =
                    ((offset.toFloat() - stage1TotalLength) / stage2TotalLength
                        .coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
            }
            .collect {}
    }

    @Composable
    fun AboutCard(
        modifier: Modifier = Modifier.padding(horizontal = 12.dp),
        content: @Composable () -> Unit,
    ) {
        Card(
            modifier = modifier.textureBlur(
                backdrop = backdrop,
                shape = RoundedCornerShape(16.dp),
                blurRadius = 60f,
                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                colors = BlurColors(blendColors = cardBlendColors),
                enabled = blurEnabled,
            ),
            colors = CardDefaults.defaultColors(
                color =
                    if (blurEnabled) Color.Transparent
                    else colorScheme.surfaceContainer,
                contentColor = Color.Transparent,
            ),
        ) {
            content()
        }
    }

    BgEffectBackground(
        dynamicBackground = true,
        modifier = Modifier.fillMaxSize(),
        bgModifier =
            if (blurEnabled) Modifier.layerBackdrop(backdrop)
            else Modifier,
        effectBackground = true,
        isOs3Effect = isOs3Effect,
        alpha = { 1f - scrollProgress },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = padding.calculateTopPadding() + 92.dp,
                    start = 24.dp,
                    end = 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val baseTitleFontSize = 32.sp
                val appName = "小废鼠 AudioStream"
                val titleLayout = remember(textMeasurer) {
                    textMeasurer.measure(
                        text = appName,
                        style = TextStyle(
                            fontWeight = FontWeight.Black,
                            fontSize = baseTitleFontSize,
                        ),
                        softWrap = false,
                    )
                }
                val titleFontSize = with(density) {
                    val availableWidthPx = maxWidth.roundToPx().toFloat()
                    val measuredWidthPx = titleLayout.size.width.toFloat().coerceAtLeast(1f)
                    val scale = (availableWidthPx / measuredWidthPx).coerceAtMost(1f)
                    (baseTitleFontSize.value * scale).coerceAtLeast(24f).sp
                }
                Text(
                    text = appName,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .onGloballyPositioned { coordinates ->
                            if (projectNameY != 0f) return@onGloballyPositioned
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            projectNameY = y + size.height
                        }
                        .graphicsLayer {
                            alpha = 1f - projectNameProgress
                            scaleX = 1f - (projectNameProgress * 0.05f)
                            scaleY = 1f - (projectNameProgress * 0.05f)
                        }
                        .textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(16.dp),
                            blurRadius = 150f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = heroBlendColors),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = blurEnabled,
                        ),
                    color = colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleFontSize,
                )
            }
            Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                        " · ${BuildConfig.BUILD_TYPE.uppercase(Locale.getDefault())} BUILD",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1f - versionCodeProgress
                        scaleX = 1f - (versionCodeProgress * 0.05f)
                        scaleY = 1f - (versionCodeProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionCodeY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        versionCodeY = y + size.height
                    },
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = REPO_URL.removePrefix("https://"),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1f - versionCodeProgress
                        scaleX = 1f - (versionCodeProgress * 0.05f)
                        scaleY = 1f - (versionCodeProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionCodeY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        versionCodeY = y + size.height
                    },
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                // 关于页不显示底部导航栏(外层 bottomBar 隐藏、padding.bottom 为 0)，
                // 系统手势条 inset 由这里自己吃，避免最后一张卡片被遮挡。
                bottom = padding.calculateBottomPadding() +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "logoSpacer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .padding(top = 36.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    isOs3Effect = !isOs3Effect
                                },
                            )
                        }
                        .onSizeChanged { onLogoHeightChanged(it.height) }
                        .onGloballyPositioned { coordinates ->
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            logoAreaY = y + size.height
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {}
            }
            item(key = "about") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = padding.calculateBottomPadding()),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    AboutCard {
                        ArrowPreference(
                            title = "项目仓库",
                            endActions = {
                                Text(
                                    text = "GitHub",
                                    fontSize = textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = {
                                haptic.contextClick(hapticEnabled)
                                openUrl(context, REPO_URL)
                            },
                        )
                        ArrowPreference(
                            title = "版本发布",
                            summary = "v${BuildConfig.VERSION_NAME}",
                            endActions = {
                                updateStatusText?.let {
                                    Text(
                                        text = it,
                                        fontSize = textStyles.body2.fontSize,
                                        color = colorScheme.onSurfaceVariantActions,
                                    )
                                }
                            },
                            enabled = updateState != UpdateUiState.Checking,
                            onClick = {
                                haptic.contextClick(hapticEnabled)
                                updateDialogRequested = true
                                // 已有检查结果直接展示；否则触发网络检查
                                if (updateState !is UpdateUiState.Available &&
                                    updateState !is UpdateUiState.UpToDate &&
                                    updateState !is UpdateUiState.Error
                                ) {
                                    updateViewModel.checkForUpdates()
                                }
                            },
                        )
                    }
                    AboutCard {
                        ArrowPreference(
                            title = "开源许可",
                            endActions = {
                                Text(
                                    text = "MIT",
                                    fontSize = textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = {
                                haptic.contextClick(hapticEnabled)
                                openUrl(context, LICENSE_URL)
                            },
                        )
                    }
                    AboutCard {
                        listOf(
                            Pair("Miuix", "https://github.com/compose-miuix-ui/miuix"),
                            Pair("OkHttp", "https://github.com/square/okhttp"),
                            Pair("Hilt", "https://github.com/google/dagger"),
                        ).forEach { (name, repo) ->
                            ArrowPreference(
                                title = name,
                                endActions = {
                                    Text(
                                        text = "GitHub",
                                        fontSize = textStyles.body2.fontSize,
                                        color = colorScheme.onSurfaceVariantActions,
                                    )
                                },
                                onClick = {
                                    haptic.contextClick(hapticEnabled)
                                    openUrl(context, repo)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // 更新弹窗：仅在用户手动点击"版本发布"后展示
    if (updateDialogRequested) {
        UpdateDialogs(
            context = context,
            updateState = updateState,
            downloadOptions = downloadOptions,
            onDownloadOptionsChange = { downloadOptions = it },
            updateViewModel = updateViewModel,
        )
    }
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
    context: android.content.Context,
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
                    fontSize = textStyles.body2.fontSize,
                    color = colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        openUrl(context, RELEASES_URL)
                        dismissAll()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("查看版本发布")
                }
                Spacer(modifier = Modifier.height(8.dp))
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
                    fontSize = textStyles.body2.fontSize,
                    color = colorScheme.onSurfaceVariantSummary
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
    context: android.content.Context,
    info: UpdateInfo,
    onPicked: (String) -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = context.getString(R.string.download_method_desc),
            fontSize = textStyles.body2.fontSize,
            color = colorScheme.onSurfaceVariantSummary
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
    context: android.content.Context,
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
                fontSize = textStyles.body2.fontSize,
                color = colorScheme.onSurfaceVariantSummary
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

private fun heroBlendColors(isDark: Boolean): List<BlendColorEntry> =
    if (isDark) listOf(
        BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
        BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
    )
    else listOf(
        BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
    )

private fun aboutCardBlendToken(isDark: Boolean): List<BlendColorEntry> =
    if (isDark) listOf(
        BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
        BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
    )
    else listOf(
        BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
    )

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
