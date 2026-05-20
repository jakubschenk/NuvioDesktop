package com.nuvio.app.desktop

import java.util.Locale

internal object DesktopSkikoRuntimeFlags {
    private const val DefaultRenderApi = "OPENGL"
    private const val PreferredWindowsRenderApi = "ANGLE"

    fun configure(): String {
        val applied = mutableListOf<String>()

        val renderApi = firstEnv("NUVIO_SKIKO_RENDER_API", "SKIKO_RENDER_API")
            ?.normalizeRenderApi()
            ?: System.getProperty("skiko.renderApi")?.normalizeRenderApi()
            ?: defaultRenderApi()
        setProperty("skiko.renderApi", renderApi, applied)

        val mpvSurface = firstEnv("NUVIO_MPV_SURFACE")
            ?.normalizeMpvSurfaceMode()
            ?: System.getProperty("nuvio.mpv.surface")?.normalizeMpvSurfaceMode()
            ?: defaultMpvSurfaceMode(renderApi)
        setProperty("nuvio.mpv.surface", mpvSurface, applied)

        val interopBlending = firstEnv("NUVIO_COMPOSE_INTEROP_BLENDING", "COMPOSE_INTEROP_BLENDING")
            ?.let(::normalizeBoolean)
            ?: System.getProperty("compose.interop.blending")?.let(::normalizeBoolean)
        if (interopBlending != null) {
            setProperty("compose.interop.blending", interopBlending, applied)
        }

        val composeLayersType = firstEnv("NUVIO_COMPOSE_LAYERS_TYPE", "COMPOSE_LAYERS_TYPE")
            ?.normalizeComposeLayersType()
            ?: System.getProperty("compose.layers.type")?.normalizeComposeLayersType()
        if (composeLayersType != null) {
            setProperty("compose.layers.type", composeLayersType, applied)
        }

        setPropertyFromEnv(
            property = "skiko.buffering",
            envNames = arrayOf("NUVIO_SKIKO_BUFFERING"),
            applied = applied,
            normalize = { it.uppercase(Locale.US) },
        )
        setPropertyFromEnv(
            property = "skiko.vsync.enabled",
            envNames = arrayOf("NUVIO_SKIKO_VSYNC_ENABLED", "NUVIO_SKIKO_VSYNC"),
            applied = applied,
            normalize = ::normalizeBoolean,
        )
        setPropertyFromEnv(
            property = "skiko.vsync.framelimit.fallback.enabled",
            envNames = arrayOf("NUVIO_SKIKO_VSYNC_FALLBACK_ENABLED", "NUVIO_SKIKO_VSYNC_FALLBACK"),
            applied = applied,
            normalize = ::normalizeBoolean,
        )
        setPropertyFromEnv(
            property = "skiko.rendering.windows.waitForFrameVsyncOnRedrawImmediately",
            envNames = arrayOf("NUVIO_SKIKO_WAIT_FOR_VSYNC_ON_REDRAW"),
            applied = applied,
            normalize = ::normalizeBoolean,
        )
        setPropertyFromEnv(
            property = "skiko.rendering.angle.enabled",
            envNames = arrayOf("NUVIO_SKIKO_ANGLE_ENABLED"),
            applied = applied,
            normalize = ::normalizeBoolean,
        )
        setPropertyFromEnv(
            property = "skiko.gpu.priority",
            envNames = arrayOf("NUVIO_SKIKO_GPU_PRIORITY"),
            applied = applied,
            normalize = { it },
        )
        setPropertyFromEnv(
            property = "skiko.gpu.resourceCacheLimit",
            envNames = arrayOf("NUVIO_SKIKO_GPU_RESOURCE_CACHE_LIMIT"),
            applied = applied,
            normalize = { it },
        )
        setPropertyFromEnv(
            property = "skiko.fps.enabled",
            envNames = arrayOf("NUVIO_SKIKO_FPS_ENABLED", "NUVIO_SKIKO_FPS"),
            applied = applied,
            normalize = ::normalizeBoolean,
        )

        return applied.joinToString(separator = " ")
    }

    private fun setPropertyFromEnv(
        property: String,
        envNames: Array<String>,
        applied: MutableList<String>,
        normalize: (String) -> String,
    ) {
        val value = firstEnv(*envNames)?.let(normalize) ?: return
        setProperty(property, value, applied)
    }

    private fun setProperty(
        property: String,
        value: String,
        applied: MutableList<String>,
    ) {
        if (value.isBlank()) return
        System.setProperty(property, value)
        applied += "$property=$value"
    }

    private fun firstEnv(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            System.getenv(name)?.trim()?.takeIf(String::isNotBlank)
        }

    private fun String.normalizeRenderApi(): String =
        when (trim().uppercase(Locale.US).replace('-', '_')) {
            "AUTO", "DEFAULT", "BEST" -> defaultRenderApi()
            "ANGLE_D3D", "ANGLE_D3D11" -> "ANGLE"
            "D3D", "D3D11", "DIRECTX", "DIRECTX11" -> "DIRECT3D"
            "GL" -> "OPENGL"
            "SW", "SOFTWARE" -> "SOFTWARE_FAST"
            else -> trim().uppercase(Locale.US)
        }

    private fun String.normalizeMpvSurfaceMode(): String =
        when (trim().lowercase(Locale.US).replace('_', '-')) {
            "auto", "best", "default" -> defaultMpvSurfaceMode(System.getProperty("skiko.renderApi") ?: defaultRenderApi())
            "native", "native-window", "hwnd", "window" -> "native-window"
            "opengl", "open-gl", "gl", "libmpv" -> "opengl"
            else -> trim().lowercase(Locale.US)
        }

    private fun String.normalizeComposeLayersType(): String =
        when (trim().uppercase(Locale.US).replace('-', '_')) {
            "WINDOW", "ON_WINDOW", "WINDOWED" -> "WINDOW"
            "CANVAS", "SAME_CANVAS", "ON_SAME_CANVAS" -> "SAME_CANVAS"
            "COMPONENT", "ON_COMPONENT" -> "COMPONENT"
            else -> trim().uppercase(Locale.US)
        }

    private fun defaultRenderApi(): String =
        if (isWindows()) PreferredWindowsRenderApi else DefaultRenderApi

    private fun defaultMpvSurfaceMode(renderApi: String): String =
        if (renderApi.uppercase(Locale.US).replace('-', '_') == "OPENGL") "opengl" else "native-window"

    private fun isWindows(): Boolean =
        System.getProperty("os.name")
            ?.lowercase(Locale.US)
            ?.contains("windows") == true

    private fun normalizeBoolean(value: String): String =
        when (value.trim().lowercase(Locale.US)) {
            "1", "true", "yes", "on" -> "true"
            "0", "false", "no", "off" -> "false"
            else -> value.trim()
        }
}
