package com.xiaofeishu.audiostream.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.xiaofeishu.audiostream.BuildConfig
import com.xiaofeishu.audiostream.MainActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常处理器。
 *
 * 行为：
 * 1. 把异常堆栈写入 filesDir/crash/ 目录下的单个文件（时间戳命名，最新即当前崩溃）。
 * 2. 启动 [CrashActivity]（运行在 :crash 独立进程，不依赖 Hilt，避免崩溃回路）。
 * 3. 结束当前进程。
 *
 * 注意：[CrashActivity] 跑在独立进程，不能直接持有任何 Hilt 注入的单例；
 * 它从崩溃文件读取纯文本展示。
 */
class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        @Volatile private var instance: CrashHandler? = null

        /** 目录名。 */
        const val DIR_NAME = "crash"

        fun install(context: Context) {
            if (instance != null) return
            instance = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(instance)
        }

        /** 崩溃目录。 */
        fun crashDir(context: Context): File =
            File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

        /** 读取最近一次崩溃报告；无则返回 null。 */
        fun loadLatest(context: Context): String? {
            val files = crashDir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
                ?: return null
            return files.maxByOrNull { it.lastModified() }?.readText()
        }

        /** 清除全部崩溃报告。 */
        fun clearAll(context: Context) {
            crashDir(context).listFiles()?.forEach { it.delete() }
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        runCatching { writeReport(buildReport(t, e)) }

        // 启动崩溃页（独立进程），结束当前（崩溃）进程，使崩溃页成为栈底。
        // 用 runCatching 包裹：若 startActivity 失败则回退到系统默认处理（弹"应用已停止"）。
        val started = runCatching {
            val intent = Intent(context, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
            Process.killProcess(Process.myPid())
            true
        }.getOrDefault(false)

        if (!started) {
            defaultHandler?.uncaughtException(t, e)
        }
    }

    private fun buildReport(t: Thread, e: Throwable): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        return buildString {
            appendLine("===== AudioStream Crash Report =====")
            appendLine("Time: $time")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Thread: ${t.name}")
            appendLine("Process: ${Process.myPid()}")
            appendLine("Thread state: ${t.state}")
            appendLine("------------------------------------")
            // 打印完整 cause 链：系统/运行时崩溃时顶层异常的栈常被清空，真实原因在 cause 里。
            var current: Throwable? = e
            var depth = 0
            while (current != null && depth < 12) {
                val sw = StringWriter()
                current.printStackTrace(PrintWriter(sw))
                val label = if (depth == 0) "Top exception" else "Caused by ($depth)"
                appendLine("$label: ${current::class.java.name}: ${current.message ?: "<no message>"}")
                appendLine("Stacktrace:")
                appendLine(sw.toString())
                current = current.cause
                depth++
                if (current != null) appendLine("------------------------------------")
            }
            appendLine("===== End of Report =====")
        }
    }

    private fun writeReport(report: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val file = File(crashDir(context), "crash_$timestamp.txt")
        file.writeText(report)
        // 只保留最近 10 份，超出删除最旧的
        crashDir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(10)
            ?.forEach { it.delete() }
    }
}
