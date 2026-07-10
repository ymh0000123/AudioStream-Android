@echo off
setlocal enabledelayedexpansion
echo.
echo   ============================================
echo        AudioStream Android 编译脚本
echo        包名: com.xiaofeishu.audiostream
echo   ============================================
echo.

REM 进入项目目录
cd /d "%~dp0"

REM 读取签名配置：环境变量优先，其次 local.properties
if exist "local.properties" (
    for /f "usebackq tokens=1,* delims==" %%a in ("local.properties") do (
        if /i "%%a"=="KEYSTORE_PASSWORD" if not defined KEYSTORE_PASSWORD set "KEYSTORE_PASSWORD=%%b"
        if /i "%%a"=="KEY_ALIAS" if not defined KEY_ALIAS set "KEY_ALIAS=%%b"
        if /i "%%a"=="KEY_PASSWORD" if not defined KEY_PASSWORD set "KEY_PASSWORD=%%b"
    )
)
if not defined KEY_ALIAS set "KEY_ALIAS=xiaofeishu"
if not defined KEY_PASSWORD set "KEY_PASSWORD=%KEYSTORE_PASSWORD%"
if not defined KEYSTORE_PASSWORD (
    echo 未设置 KEYSTORE_PASSWORD。请在环境变量或 local.properties 中配置签名密码。
    goto fail
)

REM 检查参数
set BUILD_TYPE=release
set DO_INSTALL=1
set DEVICE_IP=

:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="debug" set BUILD_TYPE=debug
if /i "%~1"=="--debug" set BUILD_TYPE=debug
if /i "%~1"=="no-install" set DO_INSTALL=0
if /i "%~1"=="--no-install" set DO_INSTALL=0
if /i "%~1"=="ip" (
    set DEVICE_IP=%~2
    shift
)
if /i "%~1"=="--ip" (
    set DEVICE_IP=%~2
    shift
)
shift
goto parse_args
:args_done

REM 生成签名密钥
if not exist "xiaofeishu.keystore" (
    echo 正在生成签名密钥...
    keytool -genkeypair -v -storetype PKCS12 -keystore xiaofeishu.keystore -storepass "!KEYSTORE_PASSWORD!" -keyalg RSA -keysize 4096 -validity 36500 -alias "!KEY_ALIAS!" -keypass "!KEY_PASSWORD!" -dname "CN=AudioStream Android, OU=AudioStream, O=XiaoFeiShu, L=Singapore, ST=Singapore, C=SG"
    if errorlevel 1 (
        echo 签名密钥生成失败
        goto fail
    )
    echo 签名密钥已生成
)

REM 清理并编译
echo.
echo 正在编译 %BUILD_TYPE% APK...
call gradlew.bat clean assemble%BUILD_TYPE%

if errorlevel 1 (
    echo 编译失败
    goto fail
)

echo.
echo ============================================
echo   编译完成!
echo   包名: com.xiaofeishu.audiostream
echo   签名: xiaofeishu.keystore
echo   签名别名: !KEY_ALIAS!
echo ============================================

REM ADB 安装
if !DO_INSTALL!==0 goto done

echo.
echo 正在通过 ADB 安装...

REM 无线 ADB 连接
if "!DEVICE_IP!"=="" goto skip_connect
set TARGET=!DEVICE_IP!
REM 如果 IP 没带端口，补上默认 5555
echo !DEVICE_IP! | findstr ":" >nul 2>nul
if !errorlevel! neq 0 set TARGET=!DEVICE_IP!:5555
echo 连接无线 ADB: !TARGET!
adb connect !TARGET!
if errorlevel 1 (
    echo 无线 ADB 连接失败
    goto fail
)
timeout /t 1 /nobreak >nul
:skip_connect

REM 检查设备
for /f "skip=1 tokens=1,2" %%a in ('adb devices') do (
    if "%%b"=="device" goto device_ok
)
echo 未检测到 ADB 设备，请确认设备已连接或无线 ADB 已配对
goto fail
:device_ok

REM 查找 APK
set APK_FILE=
for %%f in (app\build\outputs\apk\!BUILD_TYPE!\*.apk) do set APK_FILE=%%f

if "!APK_FILE!"=="" (
    echo 未找到 APK 文件
    goto fail
)

REM 安装
echo 正在安装: !APK_FILE!
adb install -r "!APK_FILE!"
if errorlevel 1 (
    echo 安装失败
    goto fail
)

echo 安装成功!
echo 启动 App...
adb shell am start -n com.xiaofeishu.audiostream/.MainActivity
goto done

:fail
echo.
pause
exit /b 1

:done
echo.
pause
