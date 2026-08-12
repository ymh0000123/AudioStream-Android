# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**
-keep class okio.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.xiaofeishu.audiostream.network.** { *; }
-keep class com.xiaofeishu.audiostream.data.dto.** { *; }
-keep class com.xiaofeishu.audiostream.domain.model.** { *; }
# Gson needs the no-arg ctor and field names of serialized types
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# 关键：保留 Gson TypeToken 的匿名子类与其泛型签名。
# R8 若擦除 TypeToken 子类的 generic superclass 签名，
# TypeToken 会抛 IllegalStateException: TypeToken must be created with a type argument。
-keep,allowobfuscation class com.google.gson.reflect.TypeToken { *; }
-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { <fields>; }
-keep class com.google.gson.** { <init>(); }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep,allowobfuscation @dagger.hilt.android.HiltAndroidApp class *
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_Factory { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Miuix（HyperOS 风格 UI 组件库）
# Miuix 打包了 compose-multiplatform 的 components-resources，其资源收集器
# （Res / ExpectResourceCollectors）通过反射式的顶层属性访问，R8 全量裁剪后
# 会在首帧渲染时抛 NoClassDefFoundError，故整体保留。
-keep class top.yukonga.miuix.kmp.** { *; }
-dontwarn top.yukonga.miuix.kmp.**
-keep class org.jetbrains.compose.resources.** { *; }
-dontwarn org.jetbrains.compose.resources.**

