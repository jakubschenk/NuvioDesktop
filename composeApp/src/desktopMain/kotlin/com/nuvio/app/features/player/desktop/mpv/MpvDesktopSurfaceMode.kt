package com.nuvio.app.features.player.desktop.mpv

import java.util.Locale

internal enum class MpvDesktopSurfaceMode {
    OpenGlInterop,
    NativeWindow,
    ;

    val requiresSurfaceBeforeLoad: Boolean
        get() = this == NativeWindow

    companion object {
        fun resolve(): MpvDesktopSurfaceMode {
            val configured = firstEnv("NUVIO_MPV_SURFACE")
                ?: System.getProperty("nuvio.mpv.surface")
            return when (configured?.normalizeMode()) {
                "native", "native-window", "hwnd", "window" -> NativeWindow
                "opengl", "open-gl", "gl", "libmpv" -> OpenGlInterop
                else -> {
                    val renderApi = System.getProperty("skiko.renderApi")
                        ?.uppercase(Locale.US)
                        ?.replace('-', '_')
                    if (renderApi == "OPENGL") OpenGlInterop else NativeWindow
                }
            }
        }

        private fun firstEnv(vararg names: String): String? =
            names.firstNotNullOfOrNull { name ->
                System.getenv(name)?.trim()?.takeIf(String::isNotBlank)
            }

        private fun String.normalizeMode(): String =
            trim().lowercase(Locale.US).replace('_', '-')
    }
}
