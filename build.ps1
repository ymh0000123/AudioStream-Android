# AudioStream Android 编译脚本
# 包名: com.xiaofeishu.audiostream
# 签名: xiaofeishu

param(
    [string]$BuildType = "release",
    [string]$DeviceIp,
    [ValidatePattern('^[vV]?\d+(\.\d+){1,2}([-.+][0-9A-Za-z.-]+)?$')]
    [string]$VersionName,
    [ValidateRange(1, 2147483647)]
    [Nullable[int]]$VersionCode,
    [switch]$Uninstall,
    [switch]$NoInstall
)

$ErrorActionPreference = "Stop"

function Pause-Exit {
    param([int]$Code = 0)
    if ($Host.Name -eq "ConsoleHost") {
        Write-Host ""
        Write-Host "按任意键退出..." -ForegroundColor Gray
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    }
    exit $Code
}

function Get-GitVersion {
    $defaultVersionName = "1.0.1"
    $defaultVersionCode = 2

    try {
        $null = git --version 2>&1
        if (-not $?) { return @{ Name = $defaultVersionName; Code = $defaultVersionCode } }

        $null = git rev-parse --is-inside-work-tree 2>&1
        if (-not $?) { return @{ Name = $defaultVersionName; Code = $defaultVersionCode } }

        $tag = git describe --tags --abbrev=0 2>$null
        $versionName = if ($tag) { $tag -replace '^[vV]', '' } else { $defaultVersionName }

        $commitCount = git rev-list --count HEAD 2>$null
        $versionCode = if ($commitCount -and $commitCount -match '^\d+$') { [int]$commitCount } else { $defaultVersionCode }

        return @{ Name = $versionName; Code = $versionCode }
    } catch {
        return @{ Name = $defaultVersionName; Code = $defaultVersionCode }
    }
}

Write-Host ""
Write-Host "  ============================================" -ForegroundColor Cyan
Write-Host "       AudioStream Android 编译脚本" -ForegroundColor Cyan
Write-Host "       包名: com.xiaofeishu.audiostream" -ForegroundColor Cyan
Write-Host "  ============================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Java 环境
if (-not $env:JAVA_HOME) {
    Write-Host "⚠️  JAVA_HOME 未设置，尝试使用默认路径..." -ForegroundColor Yellow
}

# 检查 Android SDK
if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    Write-Host "⚠️  ANDROID_HOME 未设置，尝试使用默认路径..." -ForegroundColor Yellow
}

# 进入项目目录
Set-Location $PSScriptRoot

function Read-LocalProperties {
    $props = @{}
    $path = Join-Path $PSScriptRoot "local.properties"
    if (-not (Test-Path $path)) {
        return $props
    }

    Get-Content $path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line -split "=", 2
            if ($parts.Count -eq 2) {
                $props[$parts[0].Trim()] = $parts[1].Trim()
            }
        }
    }

    return $props
}

$localProps = Read-LocalProperties
$keystorePath = "xiaofeishu.keystore"
$keyAlias = if ($env:KEY_ALIAS) { $env:KEY_ALIAS } elseif ($localProps["KEY_ALIAS"]) { $localProps["KEY_ALIAS"] } else { "xiaofeishu" }
$keystorePassword = if ($env:KEYSTORE_PASSWORD) { $env:KEYSTORE_PASSWORD } else { $localProps["KEYSTORE_PASSWORD"] }
$keyPassword = if ($env:KEY_PASSWORD) { $env:KEY_PASSWORD } elseif ($localProps["KEY_PASSWORD"]) { $localProps["KEY_PASSWORD"] } else { $keystorePassword }

if (-not $keystorePassword) {
    Write-Host "❌ 未设置 KEYSTORE_PASSWORD。请在环境变量或 local.properties 中配置签名密码。" -ForegroundColor Red
    Pause-Exit 1
}

try {

# 步骤 1: 生成签名密钥（如果不存在）
if (-not (Test-Path $keystorePath)) {
    Write-Host "📦 正在生成签名密钥..." -ForegroundColor Green

    $keytoolArgs = @(
        "-genkeypair"
        "-v"
        "-storetype", "PKCS12"
        "-keystore", $keystorePath
        "-storepass", $keystorePassword
        "-keyalg", "RSA"
        "-keysize", "4096"
        "-validity", "36500"
        "-alias", $keyAlias
        "-keypass", $keyPassword
        "-dname", "CN=AudioStream Android, OU=AudioStream, O=XiaoFeiShu, L=Singapore, ST=Singapore, C=SG"
    )

    & keytool @keytoolArgs

    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ 签名密钥生成失败" -ForegroundColor Red
        Pause-Exit 1
    }
    Write-Host "✅ 签名密钥已生成: $keystorePath" -ForegroundColor Green
} else {
    Write-Host "✅ 签名密钥已存在: $keystorePath" -ForegroundColor Green
}

# 步骤 2: 自动检测版本
if (-not $VersionName -or $null -eq $VersionCode) {
    $gitVersion = Get-GitVersion
    if (-not $VersionName) {
        $VersionName = $gitVersion.Name
        $versionSource = "git tag"
    } else {
        $versionSource = "参数"
    }
    if ($null -eq $VersionCode) {
        $VersionCode = $gitVersion.Code
    }
} else {
    $versionSource = "参数"
}
$normalizedVersionName = $VersionName -replace '^[vV]', ''

Write-Host ""
Write-Host "📋 版本: $normalizedVersionName (code: $VersionCode) [$versionSource]" -ForegroundColor Yellow

# 步骤 3: 清理旧构建
Write-Host ""
Write-Host "🧹 正在清理旧构建..." -ForegroundColor Yellow
& .\gradlew.bat clean
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 清理失败" -ForegroundColor Red
    Pause-Exit 1
}

# 步骤 4: 编译 APK
Write-Host ""
Write-Host "🔨 正在编译 $BuildType APK..." -ForegroundColor Green

$gradleArgs = @()
if ($normalizedVersionName) {
    $gradleArgs += "-PappVersionName=$normalizedVersionName"
}
if ($null -ne $VersionCode) {
    $gradleArgs += "-PappVersionCode=$VersionCode"
}
$assembleTask = if ($BuildType -eq "debug") { "assembleDebug" } else { "assembleRelease" }
& .\gradlew.bat $assembleTask @gradleArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 编译失败" -ForegroundColor Red
    Pause-Exit 1
}

# 步骤 5: 显示编译结果
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  编译完成!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

$apkPath = "app\build\outputs\apk\$BuildType"
if (Test-Path $apkPath) {
    $apks = Get-ChildItem -Path $apkPath -Filter "*.apk"
    if ($apks) {
        Write-Host ""
        Write-Host "📱 APK 文件:" -ForegroundColor Yellow
        foreach ($apk in $apks) {
            Write-Host "   $($apk.FullName)" -ForegroundColor White
            Write-Host "   大小: $([math]::Round($apk.Length / 1MB, 2)) MB" -ForegroundColor Gray
        }
    }
}

Write-Host ""
Write-Host "📋 包信息:" -ForegroundColor Yellow
Write-Host "   包名: com.xiaofeishu.audiostream" -ForegroundColor White
Write-Host "   签名: xiaofeishu.keystore" -ForegroundColor White
Write-Host "   签名别名: $keyAlias" -ForegroundColor White
Write-Host "   版本: $normalizedVersionName ($VersionCode)" -ForegroundColor White
$metadataPath = Join-Path $apkPath "output-metadata.json"
if (Test-Path $metadataPath) {
    try {
        $metadata = Get-Content $metadataPath -Raw | ConvertFrom-Json
        $apkMetadata = $metadata.elements | Select-Object -First 1
        if ($apkMetadata) {
            Write-Host "   APK 内版本: $($apkMetadata.versionName) ($($apkMetadata.versionCode))" -ForegroundColor Gray
        }
    } catch {
        # AGP 8.x 的 output-metadata.json 格式变更时静默忽略
    }
}

# 步骤 6: ADB 安装（默认执行，-NoInstall 跳过）
if (-not $NoInstall) {
    Write-Host ""
    Write-Host "📲 正在通过 ADB 安装..." -ForegroundColor Cyan

    # 检查 adb
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) {
        Write-Host "❌ 未找到 adb，请确认 Android SDK platform-tools 已加入 PATH" -ForegroundColor Red
        Pause-Exit 1
    }

    # 无线 ADB 连接
    if ($DeviceIp) {
        $target = $DeviceIp
        if ($target -notmatch ":\d+$") {
            $target = "${target}:5555"
        }
        Write-Host "📡 连接无线 ADB: $target" -ForegroundColor Yellow
        & adb connect $target
        if ($LASTEXITCODE -ne 0) {
            Write-Host "❌ 无线 ADB 连接失败" -ForegroundColor Red
            Pause-Exit 1
        }
        Start-Sleep -Seconds 1
    }

    # 检查设备
    $devices = & adb devices 2>$null | Select-String -Pattern "\tdevice$"
    if (-not $devices) {
        Write-Host "❌ 未检测到 ADB 设备，请确认设备已连接或无线 ADB 已配对" -ForegroundColor Red
        Write-Host "   提示: 先在手机上开启「无线调试」，然后用 -DeviceIp <IP> 指定地址" -ForegroundColor Gray
        Pause-Exit 1
    }
    Write-Host "📱 已连接设备:" -ForegroundColor Green
    $devices | ForEach-Object { Write-Host "   $($_.Line)" -ForegroundColor Gray }

    # 卸载旧版本
    if ($Uninstall) {
        Write-Host "🗑️  正在卸载旧版本..." -ForegroundColor Yellow
        & adb uninstall com.xiaofeishu.audiostream 2>$null
    }

    # 安装 APK
    $apkFile = Get-ChildItem -Path $apkPath -Filter "*.apk" | Select-Object -First 1
    if (-not $apkFile) {
        Write-Host "❌ 未找到 APK 文件" -ForegroundColor Red
        Pause-Exit 1
    }

    Write-Host "📦 正在安装: $($apkFile.Name)" -ForegroundColor Green
    & adb install -r $apkFile.FullName

    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ 安装失败" -ForegroundColor Red
        Pause-Exit 1
    }

    Write-Host "✅ 安装成功!" -ForegroundColor Green

    # 启动 App
    Write-Host "🚀 正在启动 App..." -ForegroundColor Green
    & adb shell am start -n com.xiaofeishu.audiostream/.MainActivity
}

} catch {
    Write-Host ""
    Write-Host "❌ 异常: $($_.Exception.Message)" -ForegroundColor Red
    Pause-Exit 1
}

Pause-Exit 0
