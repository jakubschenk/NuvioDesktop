package com.nuvio.app.features.player

internal expect object PlayerRuntimeTrace {
    val perfEnabled: Boolean

    fun info(message: String)
    fun warn(message: String)
    fun perf(message: String)
}
