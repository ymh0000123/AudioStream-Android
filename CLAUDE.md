# CLAUDE.md

本文件为 Claude Code 在此仓库中工作时提供项目指引。

## 项目概述

AudioStream（小废鼠 AudioStream）是一个 Android 原生音频流播放客户端。App 通过 **WebSocket** 协议连接到音频流服务器，接收 PCM 音频数据并通过 `AudioTrack` 实时播放；通过 **mDNS** 在局域网内自动发现可用的流服务器。播放由前台服务保活（通知栏媒体控制 + WakeLock/WifiLock），支持自动重连、连接历史、收藏服务器、码率切换、连接质量统计，以及基于 GitHub Release 的应用内更新检查（含镜像下载）。

- 包名 / 应用 ID：`com.xiaofeishu.audiostream`
- minSdk 26 / targetSdk 35 / compileSdk 35
- 单 module 结构（`:app`），无子模块
- 版本号由 Gradle 属性 `appVersionName` / `appVersionCode` 注入（CI 从 release tag 推导），本地构建有默认值

## 技术栈

- **语言**：Kotlin 2.0.21，JVM target 17
- **构建**：Gradle 8.11.1（Kotlin DSL，`build.gradle.kts`），AGP 8.9.1
- **UI**：Jetpack Compose（BOM 2024.09.02）+ Material3 + Navigation Compose
- **DI**：Hilt 2.50（KSP 2.0.21-1.0.27）
- **网络**：OkHttp 4.12.0（WebSocket，10s ping 保活）
- **序列化**：Gson 2.10.1
- **持久化**：Jetpack DataStore Preferences
- **媒体**：AndroidX Media 1.7.0（`MediaSessionCompat` + MediaStyle 通知）
- **音频**：Android `AudioTrack`（PCM 8/16 bit，单声道/立体声）
- **服务发现**：Android `NsdManager`（mDNS / `_audiostream._tcp.`）

> 注意：依赖未使用 Version Catalog（无 `libs.versions.toml`），版本号直接写在 `app/build.gradle.kts` 的 dependencies 中。版本矩阵是为离线/镜像构建锁定的，勿随意升级。

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

**签名**：`xiaofeishu.keystore`（仓库根目录，**不入版本控制**，由 `build.ps1`/`build.bat` 在缺失时自动调用 `keytool` 生成）。release 的 `signingConfig` 从环境变量或 `local.properties` 读取 `KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`；若 `KEY_PASSWORD` 缺失，则使用 `KEYSTORE_PASSWORD`。release 启用 `isMinifyEnabled = true`，混淆规则见 `app/proguard-rules.pro`。

> ⚠️ 签名密钥与口令**不应**硬编码进仓库：keystore 已被 `.gitignore` 忽略，Gradle 与构建脚本从环境变量或 `local.properties`（也已忽略）读取。修改时注意：release 构建依赖该 keystore 存在，脚本会在缺失时自动生成。

**CI**：`.github/workflows/build-debug.yml` / `build-release.yml`。release 工作流构建 tag 提交并从 tag 推导版本号——修完 CI 问题后必须重新发 release 才生效。

## 架构

代码位于 `app/src/main/java/com/xiaofeishu/audiostream/`，分层 MVVM：**ui → viewmodel → domain（接口/模型/用例）→ data（实现）/ network / audio**。Repository 均为 `@Singleton`，通过 `RepositoryModule` 的 `@Binds` 绑定接口。

```
├── AudioStreamApp.kt        # Application：Hilt 入口，安装 CrashHandler
├── MainActivity.kt          # 单 Activity：导航 + 通知权限请求；连接时 startForegroundService
├── service/
│   └── AudioStreamService.kt    # 前台服务：通知栏媒体控制、MediaSession、WakeLock/WifiLock
│                                # 只订阅 StreamRepository.state 并转发媒体指令，不承载连接逻辑
├── viewmodel/
│   ├── HomeViewModel.kt         # 发现列表（合并收藏）、扫描启停、历史/收藏/偏好
│   ├── PlayerViewModel.kt       # 映射 StreamRepository.state 为 UI 状态，转发命令
│   └── UpdateViewModel.kt       # 更新检查
├── domain/
│   ├── model/                   # ServerInfo / PlaybackState / ConnectionState / AudioFormat
│   │                            # MediaState / MediaAction / StreamStats / Protocol(枚举) 等
│   ├── repository/              # StreamRepository / DiscoveryRepository / SettingsRepository 接口
│   └── usecase/                 # 薄封装（Connect/Disconnect/ScanServers）
├── data/
│   ├── StreamRepositoryImpl.kt      # 核心编排：协议选择、连接/断开、独立播放队列、
│   │                                # 自动重连（指数退避 1s→30s）、看门狗、码率/延迟/质量统计
│   ├── DiscoveryRepositoryImpl.kt   # mDNS 扫描编排：MulticastLock、启动失败退避重试、
│   │                                # 默认网络切换自动重扫、Lost 事件移除下线服务
│   ├── SettingsRepositoryImpl.kt    # DataStore：音量/自动重连/码率/延迟档/协议偏好/历史/收藏
│   ├── AppDataStore.kt、*DataSource.kt、dto/
│   └── update/UpdateChecker.kt      # GitHub Release 更新检查（403 时回退解析 release 页）
├── network/
│   ├── AudioEvent.kt            # sealed：Connected / AudioData / StateUpdate / BitrateChanged / Disconnected / Error
│   ├── protocol/
│   │   ├── AudioProtocol.kt         # 协议接口：connect() -> Flow<AudioEvent>
│   │   ├── AudioProtocolFactory.kt
│   │   └── WebSocketProtocol.kt     # ws://<host>:<port>/ws；IPv6 字面量自动加方括号
│   └── discovery/
│       └── MdnsDiscovery.kt     # NsdManager 封装：discover() -> Flow<DiscoveryEvent>(Found/Lost)
├── audio/
│   ├── AudioPlayer.kt           # AudioTrack 封装，PCM 播放
│   └── AudioPlayerFactory.kt
├── crash/
│   ├── CrashHandler.kt          # 未捕获异常写入 filesDir/crash/，拉起 CrashActivity
│   └── CrashActivity.kt         # 跑在 :crash 独立进程，不依赖 Hilt，只读崩溃文件展示
├── di/                          # AppModule(@AppScope/OkHttp/@PlaybackDispatcher)、
│                                # NetworkModule、RepositoryModule
└── ui/
    ├── theme/Theme.kt
    ├── screen/   (HomeScreen / PlayerScreen / HistoryScreen / SettingsScreen)
    └── component/ (ServerCard / ConnectionStatus / QualityIndicator / StatsBar / SteppedSlider)
```

### 关键数据流

1. **发现**：`HomeScreen` 用 `LifecycleResumeEffect` 让扫描跟随页面可见性启停（离开/退后台即停，释放 MulticastLock 省电）。`MdnsDiscovery.discover()` 发出 `Found`/`Lost` 事件，`DiscoveryRepositoryImpl` 维护去重列表（key = 服务实例名），下线即移除；网络切换或 Wi-Fi 迟到时自动重扫。`startScan()` 重复调用 = 重置（清空并重启）。
2. **连接**：UI（Home/History）选中服务器 → `PlayerViewModel.connect(server)` 直达 `StreamRepository`（@Singleton），同时 `MainActivity.ensureServiceRunning()` 启动前台服务保活。**Service 不参与连接编排**，只消费状态。
3. **播放**：`StreamRepositoryImpl` 收集协议的 `Flow<AudioEvent>`：`Connected(format)` 初始化播放器；`AudioData` 进独立播放队列，由 `@PlaybackDispatcher`（单线程、AUDIO 优先级）写入 `AudioTrack`，解耦网络读取与音频写入；断开/出错按开关自动重连（指数退避）。
4. **状态**：`StreamRepository.state: StateFlow<PlaybackState>` 是唯一事实源。`PlayerViewModel` 映射给 UI；`AudioStreamService` 订阅它更新通知/MediaSession，并按 `connectionState.isActive` 持有/释放 WakeLock + WifiLock（活跃态含 CONNECTED，不能提前丢锁，否则锁屏断连）。
5. **媒体控制**：通知按钮 / 蓝牙耳机键 → Service（MediaSession 回调或 intent action）→ `streamRepository.sendCommand(MediaAction)` → WebSocket 文本命令发给服务端。本地"是否在出声"判据必须统一用 Service 的 `isLocallyPlaying()`（mediaState.playing 或 PLAYING 态），否则通知/实际播放会错位。

### 协议细节（修改网络层时务必遵循）

- **WebSocket**：URL 为 `ws://<address>:<port>/ws`（IPv6 字面量地址需加方括号，`WebSocketProtocol` 已处理）。默认端口 19730。
- 服务端建立连接后发送第一条文本消息（JSON，含 `sample_rate`/`channels`/`bits_per_sample`，可选 `type:"format"`）作为格式握手，`AudioFormat.fromJson()` 解析，10s 未握手判超时；后续二进制帧为 PCM 音频。
- 其他文本消息：媒体状态（`MediaState.fromJson`）、`type:"bitrate_changed"`（携带新格式）；客户端可发 `{"type":"command","action":"set_bitrate","bitrate":N}` 及播放控制命令。
- **mDNS**：服务类型 `_audiostream._tcp.`。`MdnsDiscovery` 的版本适配有坑：API 29+ 用 `ServiceInfoCallback`，API < 29 回退旧 `resolveService`（串行队列 + 重试，避免 `FAILURE_ALREADY_ACTIVE` 丢服务）；**所有对 `ServiceInfoCallback` 的引用必须隔离在 `@RequiresApi(Q)` 的独立方法里**（Android 9 的 ART 才不会解析该类导致 `NoClassDefFoundError`），字段/参数用 `Any` 持有。API 34+ 取地址优先 IPv4。

## 编码约定

- Kotlin 官方代码风格（`kotlin.code.style=official`，见 `gradle.properties`）。
- 协程：Repository 注入 `@AppScope`（SupervisorJob + Default）；Service 自建 `serviceScope`；UI 层用 `viewModelScope` + `collectAsStateWithLifecycle`/`collectAsState`。音频写入固定走 `@PlaybackDispatcher` 单线程。
- 状态对外暴露用 `StateFlow`（`asStateFlow()`/`stateIn`），内部用 `MutableStateFlow`；跨层共享状态放 Repository，不放 ViewModel。
- 新增依赖注入优先走已有 module：接口绑定加在 `RepositoryModule`（@Binds），对象构造加在 `AppModule`/`NetworkModule`（@Provides）。
- UI 文案为中文；通知类文案在 `res/values/strings.xml`，Compose 屏幕内亦有直接硬编码的中文文案（跟随所在文件的既有风格）。
- 签名口令、keystore 不入仓库：keystore 由 `build.ps1`/`build.bat` 缺失时自动生成，gradle 从 `local.properties` 读取口令（缺失回退脚本默认值）。不要把口令/keystore 重新硬编码进 gradle 或脚本。

## 仓库环境说明

- Windows 开发机，shell 为 Git Bash。路径用正斜杠或引号包裹的 Windows 路径均可。
- Maven 仓库：官方源（google/mavenCentral）在前，阿里云镜像（`maven.aliyun.com`）+ jitpack 兜底，定义在 `settings.gradle.kts`，`RepositoriesMode.FAIL_ON_PROJECT_REPOS`，**不要在子 module 里声明 repositories**。`pluginManagement` 里有 Hilt 插件的 `eachPlugin` 直映射（绕过镜像上插件 marker 随机解析失败的问题），**不要删**。
- `.gradle/` 与 `app/build/` 为构建产物，不要纳入版本控制或人工编辑。
- `.nezha/config.toml` 是 Nezha 工具配置（默认 agent=claude，permission=ask），与构建无关，改动需用户确认。
