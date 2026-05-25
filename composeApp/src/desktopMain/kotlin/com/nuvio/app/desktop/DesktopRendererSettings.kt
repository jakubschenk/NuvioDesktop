package com.nuvio.app.desktop

import java.util.Locale

internal enum class DesktopRendererApi(
    val propertyValue: String,
    val displayName: String,
    val description: String,
) {
    OpenGl(
        propertyValue = "OPENGL",
        displayName = "OpenGL",
        description = "Legacy OpenGL path for compatibility.",
    ),
    Angle(
        propertyValue = "ANGLE",
        displayName = "ANGLE",
        description = "D3D11 through ANGLE. Recommended on Windows.",
    ),
    Direct3D(
        propertyValue = "DIRECT3D",
        displayName = "Direct3D",
        description = "Skiko Direct3D renderer.",
    );

    companion object {
        fun from(value: String?): DesktopRendererApi? =
            when (value?.trim()?.uppercase(Locale.US)?.replace('-', '_')) {
                "OPENGL", "GL" -> OpenGl
                "ANGLE", "ANGLE_D3D", "ANGLE_D3D11", "D3D11", "DIRECTX11", "DIRECT3D11" -> Angle
                "DIRECT3D", "D3D", "D3D12", "DIRECTX", "DIRECTX12", "DIRECT3D12" -> Direct3D
                else -> null
            }
    }
}

internal object DesktopRendererSettings {
    private const val preferencesName = "nuvio_desktop_renderer"
    private const val renderApiKey = "skiko_render_api"

    val options: List<DesktopRendererApi> = listOf(
        DesktopRendererApi.OpenGl,
        DesktopRendererApi.Angle,
        DesktopRendererApi.Direct3D,
    )

    fun loadPreferredRenderApi(): DesktopRendererApi? =
        DesktopRendererApi.from(DesktopPreferences.getString(preferencesName, renderApiKey))

    fun savePreferredRenderApi(api: DesktopRendererApi) {
        DesktopPreferences.putString(preferencesName, renderApiKey, api.propertyValue)
    }

    fun selectedOrDefault(): DesktopRendererApi =
        loadPreferredRenderApi() ?: defaultRenderApi()

    fun runningRenderApi(): DesktopRendererApi? =
        DesktopRendererApi.from(System.getProperty("skiko.renderApi"))

    fun defaultRenderApi(): DesktopRendererApi =
        if (isWindows()) DesktopRendererApi.Angle else DesktopRendererApi.OpenGl

    private fun isWindows(): Boolean =
        System.getProperty("os.name")
            ?.lowercase(Locale.US)
            ?.contains("windows") == true
}
