package com.xiaofeishu.audiostream

import android.app.Application
import com.xiaofeishu.audiostream.crash.CrashHandler
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口。Hilt 生成依赖图。
 * 安装全局崩溃处理器：捕获未处理异常后展示自定义崩溃页（[com.xiaofeishu.audiostream.crash.CrashActivity]）。
 */
@HiltAndroidApp
class AudioStreamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
