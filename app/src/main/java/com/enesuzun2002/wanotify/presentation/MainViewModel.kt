package com.enesuzun2002.wanotify.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.enesuzun2002.wanotify.core.utils.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainUiState(
    val isNotificationAccessGranted: Boolean = false,
    val isBatteryExemptionGranted: Boolean = false
) {
    val isSystemReady: Boolean
        get() = isNotificationAccessGranted && isBatteryExemptionGranted
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    init {
        updatePermissions()
    }

    fun updatePermissions() {
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(
                isNotificationAccessGranted = PermissionUtils.isNotificationListenerEnabled(context),
                isBatteryExemptionGranted = PermissionUtils.isIgnoringBatteryOptimizations(context)
            )
        }
    }
}