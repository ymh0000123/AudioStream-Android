package com.xiaofeishu.audiostream.di

import android.content.Context
import com.xiaofeishu.audiostream.network.discovery.MdnsDiscovery
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMdnsDiscovery(@ApplicationContext context: Context): MdnsDiscovery =
        MdnsDiscovery(context)
}
