package com.xiaofeishu.audiostream.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 全局 DataStore（Preferences）。store 名沿用旧版 "settings" 以便迁移旧数据。
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
