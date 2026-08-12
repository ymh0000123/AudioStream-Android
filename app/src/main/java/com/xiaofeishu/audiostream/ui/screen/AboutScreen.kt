package com.xiaofeishu.audiostream.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.xiaofeishu.audiostream.BuildConfig
import com.xiaofeishu.audiostream.ui.component.AppTopBar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val REPO_URL = "https://github.com/ymh0000123/AudioStream-Android"
private const val RELEASES_URL = "$REPO_URL/releases"
private const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt"

/** Hero 区（大标题 + 版本号）的占位高度。顶栏压成单行后同步收紧，避免顶部大片留白。 */
private val HeroHeight = 160.dp

/**
 * 关于页。布局与滚动动画参考 Miuzarte/ScrcpyForAndroid 的 AboutScreen：
 *
 * - 顶部 Hero 区展示大号项目名与版本号，随列表滚动分两阶段淡出并轻微缩小：
 *   先淡出版本号（第一阶段过半才开始），再淡出项目名，形成层次感。
 * - 顶栏初始完全透明，Hero 区滚完后才渐显标题与背景色。
 *
 * 注意：原项目基于 Miuix 0.9.x，其毛玻璃（textureBlur / layerBackdrop）与流光背景
 * 依赖 miuix-blur 模块，本项目使用的 0.2.9 无该 API，故只复刻布局与滚动动画。
 */
@Composable
fun AboutScreen() {
    val lazyListState = rememberLazyListState()
    var heroHeightPx by remember { mutableIntStateOf(0) }

    // Hero 区滚出屏幕的进度 0f..1f，驱动顶栏渐显
    val scrollProgress by remember {
        derivedStateOf {
            if (heroHeightPx <= 0) {
                0f
            } else if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset.toFloat() / heroHeightPx)
                    .coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "关于",
                // 顶栏背景与标题随滚动渐显：Hero 区可见时完全透明，避免与大标题重叠
                color = MiuixTheme.colorScheme.background.copy(alpha = scrollProgress),
                modifier = Modifier.graphicsLayer { alpha = scrollProgress }
            )
        }
    ) { innerPadding ->
        AboutContent(
            padding = innerPadding,
            lazyListState = lazyListState,
            onHeroHeightChanged = { heroHeightPx = it }
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    lazyListState: LazyListState,
    onHeroHeightChanged: (Int) -> Unit
) {
    val context = LocalContext.current

    // 各元素在窗口中的纵向位置，用于把滚动偏移换算成分阶段的淡出进度
    var heroBottomY by remember { mutableFloatStateOf(0f) }
    var titleBottomY by remember { mutableFloatStateOf(0f) }
    var versionBottomY by remember { mutableFloatStateOf(0f) }
    var initialHeroBottomY by remember { mutableFloatStateOf(0f) }
    var titleProgress by remember { mutableFloatStateOf(0f) }
    var versionProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .collect { offset ->
                if (lazyListState.firstVisibleItemIndex > 0) {
                    titleProgress = 1f
                    versionProgress = 1f
                    return@collect
                }
                if (initialHeroBottomY == 0f && heroBottomY > 0f) {
                    initialHeroBottomY = heroBottomY
                }
                val refHeroBottom = if (initialHeroBottomY > 0f) initialHeroBottomY else heroBottomY
                // 第一阶段：版本号淡出（滚过一半才开始，制造先后次序）
                val stage1 = refHeroBottom - versionBottomY
                // 第二阶段：项目名淡出
                val stage2 = versionBottomY - titleBottomY
                val versionDelay = stage1 * 0.5f
                versionProgress =
                    ((offset - versionDelay) / (stage1 - versionDelay).coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                titleProgress =
                    ((offset - stage1) / stage2.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Hero 区：不参与滚动，由上方 LazyColumn 的占位 item 让出空间，
        // 自身只按滚动进度做淡出/缩放，实现"被列表推走"的视差感。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = padding.calculateTopPadding() + 32.dp,
                    start = 24.dp,
                    end = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val appName = "小废鼠 AudioStream"
                val baseFontSize = 32.sp
                // 按可用宽度自适应字号，长标题不换行、不裁切
                val measured = remember(textMeasurer) {
                    textMeasurer.measure(
                        text = appName,
                        style = TextStyle(
                            fontWeight = FontWeight.Black,
                            fontSize = baseFontSize
                        ),
                        softWrap = false
                    )
                }
                val titleFontSize = with(density) {
                    val availableWidthPx = maxWidth.roundToPx().toFloat()
                    val measuredWidthPx = measured.size.width.toFloat().coerceAtLeast(1f)
                    val scale = (availableWidthPx / measuredWidthPx).coerceAtMost(1f)
                    (baseFontSize.value * scale).coerceAtLeast(20f).sp
                }
                Text(
                    text = appName,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .onGloballyPositioned { coordinates ->
                            if (titleBottomY != 0f) return@onGloballyPositioned
                            titleBottomY = coordinates.positionInWindow().y + coordinates.size.height
                        }
                        .graphicsLayer {
                            alpha = 1f - titleProgress
                            scaleX = 1f - titleProgress * 0.05f
                            scaleY = 1f - titleProgress * 0.05f
                        },
                    color = MiuixTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleFontSize
                )
            }
            Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        if (versionBottomY != 0f) return@onGloballyPositioned
                        versionBottomY = coordinates.positionInWindow().y + coordinates.size.height
                    }
                    .graphicsLayer {
                        alpha = 1f - versionProgress
                        scaleX = 1f - versionProgress * 0.05f
                        scaleY = 1f - versionProgress * 0.05f
                    },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                // 关于页不显示底部导航栏（外层 bottomBar 隐藏、padding.bottom 为 0），
                // 系统手势条 inset 由这里自己吃，避免最后一张卡片被遮挡
                bottom = padding.calculateBottomPadding() +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
            // Miuix 0.2.9 的 LazyColumn 没有 verticalArrangement 参数，
            // 卡片间距由各 item 自带的 bottom padding 提供（见 AboutCard）。
        ) {
            // 透明占位：为悬浮的 Hero 区让出空间，同时作为滚动进度的量尺
            item(key = "heroSpacer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HeroHeight)
                        .onSizeChanged { onHeroHeightChanged(it.height) }
                        .onGloballyPositioned { coordinates ->
                            heroBottomY = coordinates.positionInWindow().y + coordinates.size.height
                        }
                )
            }

            item(key = "project") {
                AboutCard {
                    SuperArrow(
                        title = "项目仓库",
                        rightText = "GitHub",
                        onClick = { openUrl(context, REPO_URL) }
                    )
                    SuperArrow(
                        title = "版本发布",
                        rightText = "Releases",
                        onClick = { openUrl(context, RELEASES_URL) }
                    )
                }
            }

            item(key = "license") {
                AboutCard {
                    SuperArrow(
                        title = "开源许可",
                        rightText = "Apache-2.0",
                        onClick = { openUrl(context, LICENSE_URL) }
                    )
                }
            }

            item(key = "credits") {
                AboutCard {
                    listOf(
                        "Miuix" to "https://github.com/miuix-kotlin-multiplatform/miuix",
                        "OkHttp" to "https://github.com/square/okhttp",
                        "Hilt" to "https://github.com/google/dagger"
                    ).forEach { (name, repo) ->
                        SuperArrow(
                            title = name,
                            rightText = "GitHub",
                            onClick = { openUrl(context, repo) }
                        )
                    }
                }
            }

            item(key = "intro") {
                AboutCard(insidePadding = true) {
                    Text(
                        text = "AudioStream 是一个 Android 原生音频流播放客户端，" +
                            "通过 WebSocket 协议连接到音频流服务器，接收 PCM 音频数据并实时播放。" +
                            "支持 mDNS 局域网自动发现、前台服务保活、自动重连、连接历史与收藏服务器。",
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutCard(
    insidePadding: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        insideMargin = if (insidePadding) {
            DpSize(16.dp, 16.dp)
        } else {
            DpSize(0.dp, 0.dp)
        }
    ) {
        content()
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
