package com.nuvio.app.features.player

import com.nuvio.app.desktop.DesktopRuntimeLog

internal object DesktopPlayerPerfTrace {
    val enabled: Boolean
        get() = System.getProperty("nuvio.player.perfLogs").isTruthy() ||
            System.getProperty("nuvio.player.perfTrace").isTruthy() ||
            System.getenv("NUVIO_PLAYER_PERF_LOGS").isTruthy() ||
            System.getenv("NUVIO_PLAYER_PERF_TRACE").isTruthy()

    fun info(message: String) {
        if (enabled) {
            DesktopRuntimeLog.info("PlayerPerf $message")
        }
    }

    private fun String?.isTruthy(): Boolean =
        equals("true", ignoreCase = true) ||
            equals("1") ||
            equals("yes", ignoreCase = true) ||
            equals("on", ignoreCase = true)
}
