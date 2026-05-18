package com.nuvio.app.desktop

internal object DesktopSkikoRenderer {
    private const val NuvioRenderApiEnv = "NUVIO_SKIKO_RENDER_API"
    private const val SkikoRenderApiEnv = "SKIKO_RENDER_API"
    private const val SkikoRenderApiProperty = "skiko.renderApi"

    fun configureFromEnvironment() {
        val skikoEnv = System.getenv(SkikoRenderApiEnv)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val nuvioEnv = System.getenv(NuvioRenderApiEnv)
            ?.trim()
            ?.takeIf(String::isNotBlank)

        if (skikoEnv == null && nuvioEnv != null) {
            System.setProperty(SkikoRenderApiProperty, nuvioEnv.uppercase())
        }

        DesktopRuntimeLog.info(
            "skiko.renderApi request effective=${requestedRenderApi() ?: "default"} " +
                "env:$SkikoRenderApiEnv=${skikoEnv ?: "unset"} " +
                "env:$NuvioRenderApiEnv=${nuvioEnv ?: "unset"} " +
                "property=${System.getProperty(SkikoRenderApiProperty) ?: "unset"}",
        )
    }

    fun requestedRenderApi(): String? {
        val envOverride = System.getenv(SkikoRenderApiEnv)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val property = System.getProperty(SkikoRenderApiProperty)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        return (envOverride ?: property)?.uppercase()
    }

    fun isOpenGlRequested(): Boolean = requestedRenderApi() == "OPENGL"
}
