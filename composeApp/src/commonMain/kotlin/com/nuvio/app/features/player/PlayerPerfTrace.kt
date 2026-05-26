package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val PlayerPerfLogIntervalMs = 2_000L
private const val PlayerPerfLogIntervalNanos = PlayerPerfLogIntervalMs * 1_000_000L

internal class PlayerPerfFrameRateCounter(
    private val name: String,
) {
    private var lastLogNanos: Long = 0L
    private var frameCount: Int = 0

    fun record(frameNanos: Long, details: () -> String = { "" }) {
        if (!PlayerRuntimeTrace.perfEnabled) return
        if (lastLogNanos == 0L) {
            lastLogNanos = frameNanos
            frameCount = 0
            return
        }

        frameCount += 1
        val elapsedNanos = frameNanos - lastLogNanos
        if (elapsedNanos < PlayerPerfLogIntervalNanos) return

        val fps = frameCount / (elapsedNanos.toDouble() / 1_000_000_000.0)
        PlayerRuntimeTrace.perf(
            "composeFrame name=$name fps=${playerPerfOneDecimal(fps)} frames=$frameCount ${details()}",
        )
        frameCount = 0
        lastLogNanos = frameNanos
    }
}

internal class PlayerPerfEventRateCounter(
    private val name: String,
) {
    private var lastLogMs: Long = 0L
    private var eventCount: Int = 0

    fun record(details: () -> String = { "" }) {
        if (!PlayerRuntimeTrace.perfEnabled) return
        val nowMs = WatchProgressClock.nowEpochMs()
        if (lastLogMs == 0L) {
            lastLogMs = nowMs
            eventCount = 0
        }

        eventCount += 1
        val elapsedMs = nowMs - lastLogMs
        if (elapsedMs < PlayerPerfLogIntervalMs) return

        val rate = eventCount / (elapsedMs.toDouble() / 1_000.0)
        PlayerRuntimeTrace.perf(
            "inputEvents name=$name rate=${playerPerfOneDecimal(rate)} count=$eventCount ${details()}",
        )
        eventCount = 0
        lastLogMs = nowMs
    }
}

@Composable
internal fun PlayerPerfCompositionProbe(name: String) {
    if (!PlayerRuntimeTrace.perfEnabled) return

    val counter = remember(name) { PlayerPerfCompositionCounter() }
    SideEffect {
        counter.count += 1
    }

    LaunchedEffect(name) {
        while (true) {
            delay(PlayerPerfLogIntervalMs)
            val count = counter.count
            counter.count = 0
            PlayerRuntimeTrace.perf(
                "composeRecompose name=$name rate=${playerPerfOneDecimal(count / (PlayerPerfLogIntervalMs / 1_000.0))} count=$count",
            )
        }
    }
}

private class PlayerPerfCompositionCounter {
    var count: Int = 0
}

internal fun playerPerfOneDecimal(value: Double): String =
    ((value * 10.0).roundToInt() / 10.0).toString()
