# 小废鼠 AudioStream · AudioStream

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](#-license--许可证)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84)
![targetSdk](https://img.shields.io/badge/targetSdk-35-3DDC84)

A native Android audio-stream playback client. The app connects to an audio-stream server over **WebSocket**, receives PCM audio data and plays it back in real time via `AudioTrack`; discovered servers are found automatically on the LAN via **mDNS**. Playback runs in a foreground service, with quick reconnect from connection history after a disconnect.

Android 端原生音频流播放客户端。App 通过 **WebSocket** 协议连接到音频流服务器,接收 PCM 音频数据并由 `AudioTrack` 实时播放;同时通过 **mDNS** 在局域网内自动发现可用的流服务器。播放由前台服务承载,断开后可从连接历史快速重连。

---

## ✨ Features · 功能

- **WebSocket PCM streaming · WebSocket PCM 流式播放** — first text message is a JSON format handshake (`sample_rate` / `channels` / `bits_per_sample`), followed by binary PCM frames. Support 8/16-bit, mono/stereo.
  首条文本消息为 JSON 格式握手,后续为二进制 PCM 帧,支持 8/16 bit、单声道/立体声。
- **mDNS auto-discovery · mDNS 自动发现** — discovers `_audiostream._tcp.` services on the LAN via `NsdManager`.
  通过 `NsdManager` 发现局域网内 `_audiostream._tcp.` 服务。
- **Foreground service + persistent notification · 前台服务 + 常驻通知** — keeps playback alive with a WakeLock and media-style controls.
  前台服务承载播放,持有 WakeLock 并提供媒体样式控制。
- **Connection history & quick reconnect · 连接历史与快速重连** — recent connections persisted in DataStore.
  最近连接持久化于 DataStore,可一键重连。
- **Media session · 媒体会话** — play / pause / skip controls wired through `MediaSession`.
  播放/暂停/切歌等通过 `MediaSession` 接入系统媒体控制。
- **Jetpack Compose UI · Compose 界面** — Material 3, navigation-compose, theme-aware.
  Material 3 主题,基于 navigation-compose 的导航。
- **Robust crash handling · 崩溃兜底** — custom crash handler with a friendly recovery screen.
  自定义崩溃处理与友好的崩溃恢复界面。

---

## 🧱 Tech Stack · 技术栈

| Layer · 层 | Choice · 技术 |
|---|---|
| Language · 语言 | Kotlin 2.0.21 (JVM 17) |
| Build · 构建 | Gradle 8.11.1 · AGP 8.9.1 · Kotlin DSL |
| UI | Jetpack Compose (BOM 2024.09.02) · Material 3 · Navigation Compose |
| DI · 依赖注入 | Hilt 2.50 (KSP) |
| Networking · 网络 | OkHttp 4.12.0 (WebSocket) |
| Serialization · 序列化 | Gson 2.10.1 |
| Persistence · 持久化 | Jetpack DataStore Preferences |
| Audio · 音频 | Android `AudioTrack` (PCM 8/16-bit, mono/stereo) |
| Service discovery · 服务发现 | Android `NsdManager` (mDNS / `_audiostream._tcp.`) |
| Media · 媒体 | AndroidX Media 1.7.0 (`MediaSession`) |

---

## 🚀 Build · 构建与运行

Requirements · 环境要求:**JDK 17**, Android SDK (`ANDROID_HOME`), `JAVA_HOME` set.

```bash
# Clean + build release (default) · 清理 + 编译 release(默认)
./gradlew clean assembleRelease

# Build debug · 编译 debug
./gradlew clean assembleDebug

# Or use the bundled scripts · 或使用项目自带的封装脚本
./build.ps1                 # PowerShell, release by default · 默认 release;加 -BuildType debug 走 debug
./build.bat debug           # cmd
```

Artifact path · 产物路径:`app/build/outputs/apk/<debug|release>/*.apk`

The default app version is declared in `app/build.gradle.kts`. To override it locally:
默认应用版本在 `app/build.gradle.kts` 中声明；本地构建可通过参数覆盖：

```bash
./gradlew assembleRelease -PappVersionName=1.1.0 -PappVersionCode=3
```

The official `build.ps1` script accepts the same overrides:
正式的 `build.ps1` 脚本也支持相同的版本覆盖参数：

```powershell
.\build.ps1 -VersionName 1.1.0 -VersionCode 3 -NoInstall
```

GitHub Release builds automatically use the release tag (for example `v1.1.0`) as
the APK `versionName`, and the workflow run number as `versionCode`.
GitHub Release 构建会自动将标签（如 `v1.1.0`）写入 APK 的 `versionName`，并使用工作流运行编号作为 `versionCode`。

### 🔑 Signing · 签名

The signing keystore is **not** checked into the repository. `build.ps1` / `build.bat` will automatically generate `xiaofeishu.keystore` on first build via `keytool` if it is missing. Signing credentials are read from environment variables or `local.properties` (git-ignored): `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

签名密钥**不纳入仓库**,首次构建时 `build.ps1` / `build.bat` 会通过 `keytool` 自动生成 `xiaofeishu.keystore`。签名口令从环境变量或 `local.properties`(已被 git 忽略)读取:`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

---

## 🏗️ Architecture · 架构

```
app/src/main/java/com/xiaofeishu/audiostream/
├── AudioStreamApp.kt        # Application · Hilt entry
├── MainActivity.kt          # Single-Activity, Compose entry
├── service/                  # AudioStreamService · 前台服务(连接/播放/通知/WakeLock)
├── network/
│   ├── AudioEvent.kt        # sealed: Connected / AudioData / Disconnected / Error
│   ├── protocol/            # AudioProtocol + WebSocketProtocol (ws://host:port/ws)
│   └── discovery/           # MdnsDiscovery (_audiostream._tcp.)
├── audio/                   # AudioPlayer · AudioTrack 封装
├── data/                    # DataStore: 连接历史 / 设置 / 已保存服务器
├── domain/                  # model · repository(usecase)
├── di/                      # Hilt modules
├── crash/                   # 自定义 CrashHandler + CrashActivity
└── ui/
    ├── theme/
    ├── component/           # ServerCard / ConnectionStatus / StatsBar ...
    └── screen/              # Home / Player / Settings / History
```

### Key data flow · 关键数据流

1. **Discovery · 发现**:`MdnsDiscovery.discover()` → `Flow<DiscoveredServer>` → `HomeViewModel`.
2. **Connection · 连接**:用户选择服务器 → service `connect(address, port)`.
3. **Playback · 播放**:Service collects the protocol's `Flow<AudioEvent>`:
   - `Connected(format)` → init `AudioPlayer`, raise foreground notification, acquire WakeLock;
   - `AudioData(bytes)` → write to `AudioTrack`;
   - `Disconnected` / `Error` → release WakeLock, drop foreground state.
4. **State · 状态**:Service exposes `StateFlow`s; ViewModels collect and expose Compose `State`.

### Protocol · 协议细节

- WebSocket URL: `ws://<address>:<port>/ws`.
- First message (text/JSON) is a format handshake: `{ "sample_rate", "channels", "bits_per_sample", ["type":"format"] }`.
- Subsequent frames are binary PCM.

---

## ⚠️ Notes · 说明

- Dependencies are declared inline in `app/build.gradle.kts` (no version catalog / `libs.versions.toml`).
  依赖直接写在 `app/build.gradle.kts`,未使用 Version Catalog。
- Maven repositories use the Alibaba mirror + jitpack, declared in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`). Do **not** declare repositories in submodules.
  Maven 仓库走阿里云镜像 + jitpack,定义在 `settings.gradle.kts`;不要在子 module 声明 repositories。
- UI strings are in Chinese, centralized in `app/src/main/res/values/strings.xml`.
  UI 文案为中文,集中在 `strings.xml`。

---

## 📄 License · 许可证

Released under the **MIT License**.

基于 **MIT 许可证**发布。

```
MIT License

Copyright (c) 2026 小废鼠 AudioStream

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
