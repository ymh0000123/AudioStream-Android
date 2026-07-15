package com.xiaofeishu.audiostream.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaofeishu.audiostream.BuildConfig
import com.xiaofeishu.audiostream.data.update.UpdateCheckResult
import com.xiaofeishu.audiostream.data.update.UpdateChecker
import com.xiaofeishu.audiostream.data.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class UpToDate(val latestVersion: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker
) : ViewModel() {
    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates() {
        if (_uiState.value == UpdateUiState.Checking) return
        _uiState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _uiState.value = try {
                when (val result = updateChecker.check(BuildConfig.VERSION_NAME)) {
                    is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                    is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(result.latestVersion)
                }
            } catch (_: UnknownHostException) {
                UpdateUiState.Error("无法连接网络，请检查网络后重试")
            } catch (_: SocketTimeoutException) {
                UpdateUiState.Error("连接更新服务器超时，请稍后重试")
            } catch (error: Exception) {
                UpdateUiState.Error(error.message ?: "检查更新失败")
            }
        }
    }

    fun dismissResult() {
        if (_uiState.value != UpdateUiState.Checking) {
            _uiState.value = UpdateUiState.Idle
        }
    }
}
