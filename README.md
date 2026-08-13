# 小废鼠 AudioStream

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](#-license)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84)
![targetSdk](https://img.shields.io/badge/targetSdk-37-3DDC84)

Android 原生音频流播放客户端。App 通过 **WebSocket** 协议连接到音频流服务器，接收 PCM 音频数据并由 `AudioTrack` 实时播放；通过 **mDNS** 在局域网内自动发现可用的流服务器。播放由前台服务保活，支持自动重连、连接历史、收藏服务器、码率切换与连接质量统计。

---

## ✨ 功能特性

- **WebSocket PCM 流式播放** — 连接后首条文本消息为 JSON 格式握手（`sample_rate` / `channels` / `bits_per_sample`），后续为二进制 PCM 帧，支持 8/16 bit、单声道/立体声。
- **mDNS 局域网自动发现** — 通过 `NsdManager` 发现 `_audiostream._tcp.` 服务，页面可见时自动扫描、下线服务自动移除。
- **前台服务 + 常驻通知** — 播放由前台服务承载，通知栏提供媒体控制，配合 `MediaSession` 接入系统媒体控制与蓝牙耳机按键；活跃连接持有 WakeLock/WifiLock 防止锁屏断连。
- **自动重连** — 断连后按开关自动重连，指数退避 1s → 30s。
- **码率切换** — 播放中可动态切换码率，通过 `bitrate_changed` 事件同步新格式。
- **连接质量统计** — 实时展示码率、延迟与连接质量，辅助排查卡顿。
- **连接历史与收藏** — 最近连接持久化于 DataStore，可一键重连；常用服务器可收藏置顶。
- **应用内更新检查** — 基于 GitHub Release 检查新版本，支持镜像下载（国内网络友好）。
- **Miuix 风格 UI** — 基于 Miuix 组件库（HyperOS 风格），支持毛玻璃效果与深浅色主题。
- **崩溃兜底** — 自定义 `CrashHandler` 记录崩溃日志，并提供友好的崩溃恢复界面。

---

## 🧱 技术栈

| 层 | 技术 |
|---|---|
| 语言 | Kotlin 2.3.21（JVM 17） |
| 构建 | Gradle 8.14 · AGP 8.13.2 · Kotlin DSL |
| UI | Jetpack Compose（BOM 2026.06.01）· Material 3 · Navigation Compose |
| 组件库 | Miuix 0.9.1（`miuix-ui` / `miuix-blur` / `miuix-preference` / `miuix-icons`） |
| 依赖注入 | Hilt 2.58（KSP） |
| 网络 | OkHttp 4.12.0（WebSocket，10s ping 保活） |
| 序列化 | Gson 2.10.1 |
| 持久化 | Jetpack DataStore Preferences |
| 音频 | Android `AudioTrack`（PCM 8/16-bit，单声道/立体声） |
| 媒体 | AndroidX Media 1.7.0（`MediaSessionCompat` + MediaStyle 通知） |
| 服务发现 | Android `NsdManager`（mDNS / `_audiostream._tcp.`） |

---

## 🚀 构建与运行

环境要求：**JDK 17**、Android SDK（`ANDROID_HOME`）、`JAVA_HOME`。

```bash
# 增量编译 release（默认，日常构建不要先 clean）
./gradlew assembleRelease

# 增量编译 debug
./gradlew assembleDebug

# 或使用项目自带的封装脚本
./build.ps1                 # PowerShell，默认 release；./build.ps1 -BuildType debug 走 debug
./build.bat debug           # cmd
```

产物路径：`app/build/outputs/apk/<debug|release>/*.apk`

### 版本号

版本号单一事实源在 `app/build.gradle.kts` 中推导，优先级为：**`-P` 参数 > git 推导 > 兜底常量**：

1. 命令行参数 `-PappVersionName=1.1.0 -PappVersionCode=3`；
2. git 最近 tag（`versionName`，自动去掉前缀 `v`）与提交总数（`versionCode`）——CI 发布构建即走此路径，故需 `fetch-depth: 0` 完整检出；
3. 兜底常量（本地开发默认值）。

```bash
./gradlew assembleRelease -PappVersionName=1.1.0 -PappVersionCode=3
```

`build.ps1` 也支持相同覆盖：

```powershell
.\build.ps1 -VersionName 1.1.0 -VersionCode 3 -NoInstall
```

### 🔑 签名

签名密钥 `xiaofeishu.keystore` **不纳入仓库**（已被 `.gitignore` 忽略）。首次构建时 `build.ps1` / `build.bat` 会通过 `keytool` 自动生成。签名口令从环境变量或 `local.properties`（同样被忽略）读取：`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`（`KEY_PASSWORD` 缺失时回退使用 `KEYSTORE_PASSWORD`）。release 构建启用 R8 混淆（`isMinifyEnabled = true`），规则见 `app/proguard-rules.pro`。

### CI

`.github/workflows/` 下提供 `build-debug.yml` / `build-release.yml` 两个工作流。release 工作流构建 tag 提交并从 tag 推导版本号——修改 CI 后需重新发 release 才生效。

---

## 🏗️ 架构

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

1. **发现**：`HomeScreen` 用 `LifecycleResumeEffect` 让扫描跟随页面可见性启停（离开/退后台即停，释放 MulticastLock 省电）。`MdnsDiscovery.discover()` 发出 `Found`/`Lost` 事件，`DiscoveryRepositoryImpl` 维护去重列表（key = 服务实例名），下线即移除；网络切换或 Wi-Fi 迟到时自动重扫。
2. **连接**：UI（Home/History）选中服务器 → `PlayerViewModel.connect(server)` 直达 `StreamRepository`（@Singleton），同时 `MainActivity.ensureServiceRunning()` 启动前台服务保活。**Service 不参与连接编排**，只消费状态。
3. **播放**：`StreamRepositoryImpl` 收集协议的 `Flow<AudioEvent>`：`Connected(format)` 初始化播放器；`AudioData` 进独立播放队列，由 `@PlaybackDispatcher`（单线程、AUDIO 优先级）写入 `AudioTrack`，解耦网络读取与音频写入；断开/出错按开关自动重连（指数退避）。
4. **状态**：`StreamRepository.state: StateFlow<PlaybackState>` 是唯一事实源。`PlayerViewModel` 映射给 UI；`AudioStreamService` 订阅它更新通知/MediaSession，并按 `connectionState.isActive` 持有/释放 WakeLock + WifiLock（活跃态含 CONNECTED，不能提前丢锁，否则锁屏断连）。
5. **媒体控制**：通知按钮 / 蓝牙耳机键 → Service（MediaSession 回调或 intent action）→ `streamRepository.sendCommand(MediaAction)` → WebSocket 文本命令发给服务端。

### 协议细节

- **WebSocket**：URL 为 `ws://<address>:<port>/ws`（IPv6 字面量地址自动加方括号），默认端口 **19730**。
- 服务端建立连接后发送第一条文本消息（JSON，含 `sample_rate` / `channels` / `bits_per_sample`，可选 `type:"format"`）作为格式握手，10s 未握手判超时；后续二进制帧为 PCM 音频。
- 其他文本消息：媒体状态（`MediaState`）、`type:"bitrate_changed"`（携带新格式）；客户端可发 `{"type":"command","action":"set_bitrate","bitrate":N}` 及播放控制命令。
- **mDNS**：服务类型 `_audiostream._tcp.`。API 29+ 用 `ServiceInfoCallback`，API < 29 回退旧 `resolveService`；API 34+ 取地址优先 IPv4。

---

## ⚠️ 注意事项

- 依赖直接写在 `app/build.gradle.kts` 中，**未使用 Version Catalog**（无 `libs.versions.toml`）。版本矩阵是为离线/镜像构建锁定的，**勿随意升级**；Miuix 0.9.2+ 需要 Kotlin 2.4（无配套 KSP，会破坏 Hilt），请勿单独升级。
- Maven 仓库：官方源在前，阿里云镜像 + jitpack 兜底，定义在 `settings.gradle.kts`（`FAIL_ON_PROJECT_REPOS`），**不要在子 module 中声明 repositories**。
- `pluginManagement` 中的 Hilt 插件 `eachPlugin` 直映射用于绕过镜像上插件 marker 随机解析失败的问题，**不要删除**。
- 签名口令、keystore **不入仓库**：不要重新硬编码进 gradle 或构建脚本。
- `.gradle/` 与 `app/build/` 为构建产物，不要纳入版本控制或人工编辑。

---

## 📄 License

基于 **MIT License** 发布，详见 [LICENSE](LICENSE)。
