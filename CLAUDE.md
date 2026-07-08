# CLAUDE.md

本文件为 Claude Code 在此仓库中工作时提供项目指引。

## 项目概述

AudioStream（小废鼠 AudioStream）是一个 Android 原生音频流播放客户端。App 通过 **WebSocket** 协议连接到音频流服务器，接收 PCM 音频数据并通过 `AudioTrack` 实时播放；同时通过 **mDNS** 在局域网内自动发现可用的流服务器。播放由前台服务承载，断开后可从历史记录快速重连。

- 包名：`com.xiaofeishu.audiostream`
- 应用 ID：`com.xiaofeishu.audiostream`
- minSdk 26 / targetSdk 35 / compileSdk 35
- 单 module 结构（`:app`），无子模块

## 技术栈

- **语言**：Kotlin 2.0.21，JVM target 17
- **构建**：Gradle 8.11.1（Kotlin DSL，`build.gradle.kts`），AGP 8.9.1
- **UI**：Jetpack Compose（BOM 2024.09.02）+ Material3 + Navigation Compose
- **DI**：Hilt 2.50（KSP 2.0.21-1.0.27）
- **网络**：OkHttp 4.12.0（WebSocket）
- **序列化**：Gson 2.10.1
- **持久化**：Jetpack DataStore Preferences
- **媒体**：AndroidX Media 1.7.0（`MediaSession`）
- **音频**：Android `AudioTrack`（PCM 8/16 bit，单声道/立体声）
- **服务发现**：Android `NsdManager`（mDNS / `_audiostream._tcp.`）

> 注意：依赖未使用 Version Catalog（无 `libs.versions.toml`），版本号直接写在 `app/build.gradle.kts` 的 dependencies 中。

## 构建与运行

环境要求：JDK 17、Android SDK（`ANDROID_HOME`）、`JAVA_HOME` 已设置。

```bash
# 清理 + 编译 release（默认）
./gradlew clean assembleRelease

# 编译 debug
./gradlew clean assembleDebug

# 或使用项目自带的封装脚本（会自动生成缺失的签名密钥）
./build.ps1              # PowerShell，默认 release；./build.ps1 -BuildType debug
./build.bat debug        # cmd
```

产物路径：`app/build/outputs/apk/<debug|release>/*.apk`

**签名**：`xiaofeishu.keystore`（仓库根目录，**不入版本控制**，由 `build.ps1`/`build.bat` 在缺失时自动调用 `keytool` 生成）。release 的 `signingConfig` 从 `local.properties` 读取 `KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`，缺失时回退到脚本内置默认值 `xiaofeishu`——即口令不再硬编码进仓库。release 启用 `isMinifyEnabled = true`，混淆规则见 `app/proguard-rules.pro`。

> ⚠️ 签名密钥与口令**不应**硬编码进仓库：keystore 已被 `.gitignore` 忽略，gradle 配置从 `local.properties`（也已忽略）读取。修改时注意：release 构建依赖该 keystore 存在，脚本会在缺失时自动生成。

## 架构

代码位于 `app/src/main/java/com/xiaofeishu/audiostream/`，按职责分包：

```
├── AudioStreamApp.kt        # Application，持有全局 instance
├── MainActivity.kt          # 单 Activity，Compose 入口；持有 Service 绑定 + 导航
├── service/
│   └── AudioStreamService.kt# 前台服务，连接/播放/通知/WakeLock 的核心编排者
├── network/
│   ├── AudioFormat.kt        # 音频格式数据类（sampleRate/channels/bitsPerSample）
│   ├── AudioEvent.kt        # sealed class：Connected / AudioData / Disconnected / Error
│   ├── protocol/
│   │   ├── AudioProtocol.kt  # 协议接口：connect() -> Flow<AudioEvent>
│   │   └── WebSocketProtocol.kt # ws://<host>:<port>/ws，首条消息为格式握手
│   └── discovery/
│       └── MdnsDiscovery.kt  # NsdManager 封装，发现 _audiostream._tcp.
├── audio/
│   └── AudioPlayer.kt       # AudioTrack 封装，PCM 播放
├── data/
│   └── ConnectionHistory.kt # DataStore 持久化连接历史/音量/协议偏好
└── ui/
    ├── theme/Theme.kt
    ├── screen/  (HomeScreen / PlayerScreen / SettingsScreen)
    └── component/ (ServerCard / ConnectionStatus)
```

### 关键数据流

1. **发现**：`MdnsDiscovery.discover()` 返回 `Flow<DiscoveredServer>`，`MainActivity` 收集后填充列表。
2. **连接**：用户在 `HomeScreen` 选择服务器 → `MainActivity` 把待连接信息存入 `pendingAddress/Port/Protocol` 并导航到 `PlayerScreen` → 用户点击连接 → `AudioStreamService.connect(address, port, protocol)`。
3. **播放**：`AudioStreamService` 实例化 `AudioProtocol`（WebSocket），收集其 `Flow<AudioEvent>`：
   - `Connected(format)` → 初始化 `AudioPlayer`、提升前台通知、获取 WakeLock。
   - `AudioData(bytes)` → 写入 `AudioTrack`，累加 `receivedBytes`。
   - `Disconnected` / `Error` → 释放 WakeLock、移除前台通知、状态回到 `DISCONNECTED`。
4. **状态**：Service 通过 `StateFlow` 暴露 `connectionState` / `audioFormat` / `volume` / `receivedBytes`，`MainActivity` 用多个 `LaunchedEffect` 分别收集并转成 Compose state。
5. **UI ↔ Service**：`MainActivity` 用 `bindService` 拿到 `AudioStreamService` 实例，UI 直接调用其 `connect/disconnect/setVolume`。

### 协议细节（修改网络层时务必遵循）

- **WebSocket**：URL 为 `ws://<address>:<port>/ws`。服务端建立后发送第一条文本消息（JSON，含 `sample_rate` / `channels` / `bits_per_sample`，可选 `type:"format"`），作为格式握手；后续为二进制 PCM 帧。
- 通过 `AudioFormat.fromJson()` 解析握手消息。

## 编码约定

- Kotlin 官方代码风格（`kotlin.code.style=official`，见 `gradle.properties`）。
- 协程：Service 用 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`；UI 层用 `remember { CoroutineScope(Dispatchers.IO) }` + `LaunchedEffect`。注意 `MainActivity` 中存在未绑定到生命周期的 `CoroutineScope`（小坑，改动时留意泄漏）。
- 状态对外暴露用 `StateFlow`（`asStateFlow()`），内部用 `MutableStateFlow`。
- 资源字符串集中在 `app/src/main/res/values/strings.xml`，UI 文案为中文。
- 签名口令、keystore 不入仓库：keystore 由 `build.ps1`/`build.bat` 缺失时自动生成，gradle 从 `local.properties` 读取口令（缺失回退脚本默认值）。不要把口令/keystore 重新硬编码进 gradle 或脚本。

## 仓库环境说明

- Windows 开发机，shell 为 Git Bash。路径用正斜杠或引号包裹的 Windows 路径均可。
- Maven 仓库走阿里云镜像（`maven.aliyun.com`）+ jitpack，定义在 `settings.gradle.kts`，`RepositoriesMode.FAIL_ON_PROJECT_REPOS`，**不要在子 module 里声明 repositories**。
- `.gradle/` 与 `app/build/` 为构建产物，不要纳入版本控制或人工编辑。
- `.nezha/config.toml` 是 Nezha 工具配置（默认 agent=claude，permission=ask），与构建无关，改动需用户确认。
