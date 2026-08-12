import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// 版本号单一事实源：-P 参数 > git 推导（最近 tag → versionName，提交总数 → versionCode）> 兜底常量。
// 所有入口（build.ps1 / build.bat / CI 工作流 / 裸 gradlew）都走这里，避免各自算出不同版本号。
// 注意：CI 检出必须用 fetch-depth: 0，浅克隆会让提交总数退化成 1。
val fallbackVersionName = "1.3.0"
val fallbackVersionCode = 22

fun gitOutput(vararg command: String): String? = runCatching {
    providers.exec {
        commandLine(*command)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
}.getOrNull()

val appVersionName = (findProperty("appVersionName") as? String
    ?: gitOutput("git", "describe", "--tags", "--abbrev=0")
    ?: fallbackVersionName)
    .removePrefix("v")
    .removePrefix("V")
val appVersionCode = (findProperty("appVersionCode") as? String)
    ?.let { property ->
        property.toIntOrNull()?.takeIf { it > 0 }
            ?: error("appVersionCode must be a positive integer")
    }
    ?: gitOutput("git", "rev-list", "--count", "HEAD")?.toIntOrNull()?.takeIf { it > 0 }
    ?: fallbackVersionCode

android {
    namespace = "com.xiaofeishu.audiostream"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xiaofeishu.audiostream"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField(
            "String",
            "UPDATE_API_URL",
            "\"https://api.github.com/repos/ymh0000123/AudioStream-Android/releases/latest\""
        )
        buildConfigField(
            "String",
            "UPDATE_RELEASES_URL",
            "\"https://github.com/ymh0000123/AudioStream-Android/releases/latest\""
        )
        buildConfigField(
            "String",
            "UPDATE_LATEST_APK_URL",
            "\"https://github.com/ymh0000123/AudioStream-Android/releases/latest/download/app-release.apk\""
        )
        buildConfigField(
            "String",
            "GITHUB_MIRROR_PREFIX",
            "\"https://ghproxy.net/\""
        )
    }

    // 签名口令从环境变量或 local.properties 读取(不入仓库)。
    // build 脚本在首次构建时会按同一套配置自动生成 keystore。
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        create("release") {
            storeFile = file("../xiaofeishu.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: localProps.getProperty("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
                ?: localProps.getProperty("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: localProps.getProperty("KEY_PASSWORD")
                ?: System.getenv("KEYSTORE_PASSWORD")
                ?: localProps.getProperty("KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Miuix（HyperOS 风格组件库）。0.2.9 是最后一个基于 Kotlin 2.0.21 + Compose 1.7.0 的版本，
    // 与本项目版本矩阵对齐；升级 Miuix 需同步升 Kotlin/Compose，勿单独提版本。
    implementation("top.yukonga.miuix.kmp:miuix-android:0.2.9")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Networking - OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Media
    implementation("androidx.media:media:1.7.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
