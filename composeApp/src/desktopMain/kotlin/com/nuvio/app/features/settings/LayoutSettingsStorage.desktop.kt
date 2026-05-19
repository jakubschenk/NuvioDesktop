package com.nuvio.app.features.settings

import com.nuvio.app.desktop.DesktopPreferences

internal actual object LayoutSettingsStorage {
    private const val preferencesName = "nuvio_layout_settings"
    private const val rememberScreenKey = "remember_screen"
    private const val rememberFullscreenKey = "remember_fullscreen"
    private const val lastScreenKey = "last_screen"
    private const val lastFullscreenKey = "last_fullscreen"

    actual fun loadRememberScreen(): Boolean? =
        DesktopPreferences.getBoolean(preferencesName, rememberScreenKey)

    actual fun saveRememberScreen(enabled: Boolean) {
        DesktopPreferences.putBoolean(preferencesName, rememberScreenKey, enabled)
    }

    actual fun loadRememberFullscreen(): Boolean? =
        DesktopPreferences.getBoolean(preferencesName, rememberFullscreenKey)

    actual fun saveRememberFullscreen(enabled: Boolean) {
        DesktopPreferences.putBoolean(preferencesName, rememberFullscreenKey, enabled)
    }

    actual fun loadLastScreen(): String? =
        DesktopPreferences.getString(preferencesName, lastScreenKey)

    actual fun saveLastScreen(screenName: String) {
        DesktopPreferences.putString(preferencesName, lastScreenKey, screenName)
    }

    actual fun clearLastScreen() {
        DesktopPreferences.remove(preferencesName, lastScreenKey)
    }

    actual fun loadLastFullscreen(): Boolean? =
        DesktopPreferences.getBoolean(preferencesName, lastFullscreenKey)

    actual fun saveLastFullscreen(fullscreen: Boolean) {
        DesktopPreferences.putBoolean(preferencesName, lastFullscreenKey, fullscreen)
    }

    actual fun clearLastFullscreen() {
        DesktopPreferences.remove(preferencesName, lastFullscreenKey)
    }
}
