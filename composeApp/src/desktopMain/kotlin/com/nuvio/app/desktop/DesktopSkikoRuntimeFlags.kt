package com.nuvio.app.desktop

import java.io.File
import java.util.Locale

internal object DesktopSkikoRuntimeFlags {
    private const val DefaultRenderApi = "OPENGL"
    // ANGLE is the preferred Windows baseline because it maps to D3D11 and has
    // wider legacy-driver coverage than the D3D12 Direct3D backend.
    private const val PreferredWindowsRenderApi = "ANGLE"
    private const val DefaultWindowsGpuResourceCacheLimit = "256M"

    fun configure(): String {
        val applied = mutableListOf<String>()

        val renderApi = firstEnv("NUVIO_SKIKO_RENDER_API", "SKIKO_RENDER_API")
            ?.normalizeRenderApi()
            ?: System.getProperty("skiko.renderApi")?.normalizeRenderApi()
            ?: DesktopRendererSettings.loadPreferredRenderApi()?.propertyValue
            ?: defaultRenderApi()
        setProperty("skiko.renderApi", renderApi, applied)
        setNoEraseBackgroundFlags(applied)
        configureSkikoLibraryPath(applied)
        if (renderApi == "ANGLE") {
            val explicitAngleEnabled = firstEnv("NUVIO_SKIKO_ANGLE_ENABLED")
                ?.let(::normalizeBoolean)
                ?: System.getProperty("skiko.rendering.angle.enabled")?.let(::normalizeBoolean)
            setProperty("skiko.rendering.angle.enabled", explicitAngleEnabled ?: "true", applied)
        }

        val mpvSurface = firstEnv("NUVIO_MPV_SURFACE")
            ?.normalizeMpvSurfaceMode()
            ?: System.getProperty("nuvio.mpv.surface")?.normalizeMpvSurfaceMode()
            ?: defaultMpvSurfaceMode(renderApi)
        setProperty("nuvio.mpv.surface", mpvSurface, applied)

        val interopBlending = firstEnv("NUVIO_COMPOSE_INTEROP_BLENDING", "COMPOSE_INTEROP_BLENDING")
            ?.let(::normalizeBoolean)
            ?: System.getProperty("compose.interop.blending")?.let(::normalizeBoolean)
            ?: defaultInteropBlending(renderApi = renderApi, mpvSurface = mpvSurface)
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
        val explicitVsync = firstEnv("NUVIO_SKIKO_VSYNC_ENABLED", "NUVIO_SKIKO_VSYNC")
            ?.let(::normalizeBoolean)
            ?: System.getProperty("skiko.vsync.enabled")?.let(::normalizeBoolean)
        when {
            explicitVsync != null -> setProperty("skiko.vsync.enabled", explicitVsync, applied)
            isWindowsGpuRenderApi(renderApi) -> setProperty("skiko.vsync.enabled", "true", applied)
        }
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
        if (renderApi != "ANGLE") {
            setPropertyFromEnv(
                property = "skiko.rendering.angle.enabled",
                envNames = arrayOf("NUVIO_SKIKO_ANGLE_ENABLED"),
                applied = applied,
                normalize = ::normalizeBoolean,
            )
        }
        setPropertyFromEnv(
            property = "skiko.gpu.priority",
            envNames = arrayOf("NUVIO_SKIKO_GPU_PRIORITY"),
            applied = applied,
            normalize = { it },
        )
        val gpuResourceCacheLimit = firstEnv("NUVIO_SKIKO_GPU_RESOURCE_CACHE_LIMIT")
            ?: System.getProperty("skiko.gpu.resourceCacheLimit")
            ?: defaultGpuResourceCacheLimit(renderApi)
        if (gpuResourceCacheLimit != null) {
            setProperty("skiko.gpu.resourceCacheLimit", gpuResourceCacheLimit, applied)
        }
        setPropertyFromEnv(
            property = "skiko.fps.enabled",
            envNames = arrayOf("NUVIO_SKIKO_FPS_ENABLED", "NUVIO_SKIKO_FPS"),
            applied = applied,
            normalize = ::normalizeBoolean,
        )

        return applied.joinToString(separator = " ")
    }

    private fun setNoEraseBackgroundFlags(applied: MutableList<String>) {
        val skikoNoErase = firstEnv("NUVIO_SKIKO_NO_ERASE_BACKGROUND")
            ?.let(::normalizeBoolean)
            ?: System.getProperty("skiko.rendering.noerasebackground")?.let(::normalizeBoolean)
            ?: "true"
        setProperty("skiko.rendering.noerasebackground", skikoNoErase, applied)

        val awtNoErase = firstEnv("NUVIO_AWT_NO_ERASE_BACKGROUND")
            ?.let(::normalizeBoolean)
            ?: System.getProperty("sun.awt.noerasebackground")?.let(::normalizeBoolean)
            ?: "true"
        setProperty("sun.awt.noerasebackground", awtNoErase, applied)
    }

    private fun configureSkikoLibraryPath(applied: MutableList<String>) {
        val envPath = firstEnv("NUVIO_SKIKO_LIBRARY_PATH")
        if (envPath != null) {
            setProperty("skiko.library.path", envPath, applied)
            return
        }

        val existingPath = System.getProperty("skiko.library.path")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { value -> value.contains('$') }
        if (existingPath != null) {
            setProperty("skiko.library.path", existingPath, applied)
            return
        }

        if (!isWindows()) return

        val nativeDir = skikoNativeDirCandidates()
            .firstOrNull { candidate ->
                candidate.resolve("skiko-windows-x64.dll").isFile &&
                    candidate.resolve("libEGL.dll").isFile &&
                    candidate.resolve("libGLESv2.dll").isFile
            }
            ?: return
        setProperty("skiko.library.path", nativeDir.absolutePath, applied)
    }

    private fun skikoNativeDirCandidates(): List<File> =
        buildList {
            System.getProperty("compose.application.resources.dir")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::File)
                ?.parentFile
                ?.resolve("native")
                ?.let(::add)

            javaLibraryPathEntries().map(::File).forEach { entry ->
                add(entry)
                add(entry.resolve("native"))
            }

            System.getProperty("user.dir")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::File)
                ?.let { userDir ->
                    add(userDir.resolve("app/native"))
                    add(userDir.resolve("native"))
                }
        }.distinctBy { it.absolutePath.lowercase(Locale.US) }

    private fun javaLibraryPathEntries(): List<String> =
        System.getProperty("java.library.path")
            ?.split(File.pathSeparatorChar)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

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
            "ANGLE_D3D", "ANGLE_D3D11", "D3D11", "DIRECTX11", "DIRECT3D11" -> "ANGLE"
            "D3D", "D3D12", "DIRECTX", "DIRECTX12", "DIRECT3D12" -> "DIRECT3D"
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

    private fun defaultInteropBlending(
        renderApi: String,
        mpvSurface: String,
    ): String? =
        if (isWindows() && mpvSurface == "native-window" && renderApi != "OPENGL") {
            "false"
        } else {
            null
        }

    private fun defaultGpuResourceCacheLimit(renderApi: String): String? =
        if (isWindows() && renderApi != "SOFTWARE_FAST" && renderApi != "SOFTWARE_COMPAT") {
            DefaultWindowsGpuResourceCacheLimit
        } else {
            null
        }

    private fun isWindowsGpuRenderApi(renderApi: String): Boolean =
        isWindows() && renderApi.uppercase(Locale.US).replace('-', '_') in setOf("ANGLE", "DIRECT3D")

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
