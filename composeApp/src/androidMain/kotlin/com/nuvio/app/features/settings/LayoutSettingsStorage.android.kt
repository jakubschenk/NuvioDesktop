package com.nuvio.app.features.settings

internal actual object LayoutSettingsStorage {
    actual fun loadRememberScreen(): Boolean? = null
    actual fun saveRememberScreen(enabled: Boolean) = Unit
    actual fun loadRememberFullscreen(): Boolean? = null
    actual fun saveRememberFullscreen(enabled: Boolean) = Unit
    actual fun clearRememberedScreenPlacement() = Unit
    actual fun loadLastFullscreen(): Boolean? = null
    actual fun saveLastFullscreen(fullscreen: Boolean) = Unit
    actual fun clearLastFullscreen() = Unit
}
