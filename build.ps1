# AudioStream Android 编译脚本
# 包名: com.xiaofeishu.audiostream
# 签名: xiaofeishu

param(
    [string]$BuildType = "release",
    [string]$DeviceIp,
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

try {

# 步骤 1: 生成签名密钥（如果不存在）
$keystorePath = "xiaofeishu.keystore"
if (-not (Test-Path $keystorePath)) {
    Write-Host "📦 正在生成签名密钥..." -ForegroundColor Green

    $keytoolArgs = @(
        "-genkeypair"
        "-v"
        "-keystore", $keystorePath
        "-storepass", "xiaofeishu"
        "-keyalg", "RSA"
        "-keysize", "2048"
        "-validity", "10000"
        "-alias", "xiaofeishu"
        "-keypass", "xiaofeishu"
        "-dname", "CN=XiaoFeiShu, OU=AudioStream, O=XiaoFeiShu, L=Beijing, ST=Beijing, C=CN"
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

# 步骤 2: 清理旧构建
Write-Host ""
Write-Host "🧹 正在清理旧构建..." -ForegroundColor Yellow
& .\gradlew.bat clean
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 清理失败" -ForegroundColor Red
    Pause-Exit 1
}

# 步骤 3: 编译 APK
Write-Host ""
Write-Host "🔨 正在编译 $BuildType APK..." -ForegroundColor Green

if ($BuildType -eq "debug") {
    & .\gradlew.bat assembleDebug
} else {
    & .\gradlew.bat assembleRelease
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 编译失败" -ForegroundColor Red
    Pause-Exit 1
}

# 步骤 4: 显示编译结果
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
Write-Host "   签名密码: xiaofeishu" -ForegroundColor White

# 步骤 5: ADB 安装（默认执行，-NoInstall 跳过）
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
