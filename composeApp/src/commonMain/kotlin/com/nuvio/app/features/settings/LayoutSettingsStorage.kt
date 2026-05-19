package com.nuvio.app.features.settings

internal expect object LayoutSettingsStorage {
    fun loadRememberScreen(): Boolean?
    fun saveRememberScreen(enabled: Boolean)
    fun loadRememberFullscreen(): Boolean?
    fun saveRememberFullscreen(enabled: Boolean)
    fun clearRememberedScreenPlacement()
    fun loadLastFullscreen(): Boolean?
    fun saveLastFullscreen(fullscreen: Boolean)
    fun clearLastFullscreen()
}
