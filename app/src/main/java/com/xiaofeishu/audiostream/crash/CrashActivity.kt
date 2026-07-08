package com.xiaofeishu.audiostream.crash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xiaofeishu.audiostream.MainActivity

/**
 * 崩溃展示页。运行在 :crash 独立进程，不依赖 Hilt。
 *
 * 读取最近一次崩溃报告并展示，支持复制、分享、重启、清除。
 * 故意使用最简依赖（无主题文件引用、无 DI），避免在主进程崩溃后此页再次出问题。
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashHandler.loadLatest(this) ?: "无可用的崩溃报告。"
        setContent {
            CrashUi(
                report = report,
                onRestart = { restartApp() },
                onClose = { finishAffinity() },
                onClear = { CrashHandler.clearAll(this); finishAffinity() }
            )
        }
    }

    private fun restartApp() {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
        }
        finishAffinity()
    }
}

@Composable
private fun CrashUi(
    report: String,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF1A0F0F))) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF1A0F0F)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "AudioStream 已崩溃",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFF87171)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "很抱歉，应用发生了未预期的错误。\n以下是崩溃日志，可复制后反馈给开发者。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE0E0E0)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = report,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE0E0E0)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            copyToClipboard(context, report)
                            copied = true
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (copied) "已复制" else "复制日志") }

                    Button(
                        onClick = { shareReport(context, report) },
                        modifier = Modifier.weight(1f)
                    ) { Text("分享") }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRestart,
                        modifier = Modifier.weight(1f)
                    ) { Text("重启应用") }

                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFF87171)
                        )
                    ) { Text("清除并关闭") }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun copyToClipboard(context: android.content.Context, text: String) {
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("AudioStream crash log", text))
}

private fun shareReport(context: android.content.Context, report: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AudioStream 崩溃报告")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "分享崩溃报告"))
    }
}
