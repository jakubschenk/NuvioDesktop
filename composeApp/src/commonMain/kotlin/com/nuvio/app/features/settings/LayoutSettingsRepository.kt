package com.nuvio.app.features.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LayoutSettingsUiState(
    val rememberScreen: Boolean = false,
    val rememberFullscreen: Boolean = false,
)

object LayoutSettingsRepository {
    private val _uiState = MutableStateFlow(LayoutSettingsUiState())
    val uiState: StateFlow<LayoutSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _uiState.value = LayoutSettingsUiState(
            rememberScreen = LayoutSettingsStorage.loadRememberScreen() ?: false,
            rememberFullscreen = LayoutSettingsStorage.loadRememberFullscreen() ?: false,
        )
    }

    fun setRememberScreen(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.rememberScreen == enabled) return
        _uiState.value = _uiState.value.copy(rememberScreen = enabled)
        LayoutSettingsStorage.saveRememberScreen(enabled)
        if (!enabled) {
            LayoutSettingsStorage.clearLastScreen()
        }
    }

    fun setRememberFullscreen(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.rememberFullscreen == enabled) return
        _uiState.value = _uiState.value.copy(rememberFullscreen = enabled)
        LayoutSettingsStorage.saveRememberFullscreen(enabled)
        if (!enabled) {
            LayoutSettingsStorage.clearLastFullscreen()
        }
    }

    fun loadRememberedScreenName(): String? {
        ensureLoaded()
        return if (_uiState.value.rememberScreen) {
            LayoutSettingsStorage.loadLastScreen()
        } else {
            null
        }
    }

    fun recordScreen(screenName: String) {
        ensureLoaded()
        if (_uiState.value.rememberScreen) {
            LayoutSettingsStorage.saveLastScreen(screenName)
        }
    }

    fun loadRememberedFullscreen(): Boolean {
        ensureLoaded()
        return _uiState.value.rememberFullscreen &&
            LayoutSettingsStorage.loadLastFullscreen() == true
    }

    fun recordFullscreen(isFullscreen: Boolean) {
        ensureLoaded()
        if (_uiState.value.rememberFullscreen) {
            LayoutSettingsStorage.saveLastFullscreen(isFullscreen)
        }
    }
}
