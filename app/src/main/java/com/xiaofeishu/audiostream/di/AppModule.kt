package com.xiaofeishu.audiostream.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.gson.Gson
import com.xiaofeishu.audiostream.data.appDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/** 音频写入专用单线程 dispatcher。AudioTrack.write 是阻塞调用，独占线程避免与
 *  Default 调度器上的状态/flow 计算抢线程，从而消除长播时调度抖动导致的背压丢帧。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.appDataStore

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // 流式，无读超时
        .pingInterval(15, TimeUnit.SECONDS)       // WebSocket 保活
        .build()

    @Provides
    @Singleton
    @PlaybackDispatcher
    fun providePlaybackDispatcher(): kotlinx.coroutines.ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "audio-write").apply { isDaemon = true }
        }.asCoroutineDispatcher()
}
