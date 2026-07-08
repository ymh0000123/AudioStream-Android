package com.xiaofeishu.audiostream.di

import com.xiaofeishu.audiostream.data.DiscoveryRepositoryImpl
import com.xiaofeishu.audiostream.data.SettingsRepositoryImpl
import com.xiaofeishu.audiostream.data.StreamRepositoryImpl
import com.xiaofeishu.audiostream.domain.repository.DiscoveryRepository
import com.xiaofeishu.audiostream.domain.repository.SettingsRepository
import com.xiaofeishu.audiostream.domain.repository.StreamRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStreamRepository(impl: StreamRepositoryImpl): StreamRepository

    @Binds
    @Singleton
    abstract fun bindDiscoveryRepository(impl: DiscoveryRepositoryImpl): DiscoveryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
