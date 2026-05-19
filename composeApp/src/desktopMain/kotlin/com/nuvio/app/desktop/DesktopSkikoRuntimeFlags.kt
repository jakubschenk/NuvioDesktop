package com.nuvio.app.desktop

import java.util.Locale

internal object DesktopSkikoRuntimeFlags {
    private const val DefaultRenderApi = "OPENGL"

    fun configure(): String {
        val applied = mutableListOf<String>()

        val renderApi = firstEnv("NUVIO_SKIKO_RENDER_API", "SKIKO_RENDER_API")
            ?.normalizeRenderApi()
            ?: System.getProperty("skiko.renderApi")
            ?: DefaultRenderApi
        setProperty("skiko.renderApi", renderApi, applied)

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
            "D3D", "D3D11", "DIRECTX", "DIRECTX11" -> "DIRECT3D"
            "GL" -> "OPENGL"
            "SW", "SOFTWARE" -> "SOFTWARE_FAST"
            else -> trim().uppercase(Locale.US)
        }

    private fun normalizeBoolean(value: String): String =
        when (value.trim().lowercase(Locale.US)) {
            "1", "true", "yes", "on" -> "true"
            "0", "false", "no", "off" -> "false"
            else -> value.trim()
        }
}
