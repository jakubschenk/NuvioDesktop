package com.nuvio.app.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.awt.ComposeWindow
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val FullscreenDiagnosticsLogIntervalNanos = 2_000_000_000L

@Composable
internal fun ManageDesktopFullscreenDiagnostics(window: ComposeWindow) {
    val fullscreenRevision = DesktopBorderlessFullscreenController.revision

    LaunchedEffect(window, fullscreenRevision) {
        if (DesktopRuntimeLog.debugEnabled) {
            DesktopRuntimeLog.info(
                "FullscreenDiagnostics transition " +
                    DesktopBorderlessFullscreenController.diagnosticSummary(window),
            )
        }
    }

    LaunchedEffect(window) {
        val intervalsMs = ArrayList<Double>(260)
        var lastFrameNanos = 0L
        var lastLogNanos = 0L

        while (true) {
            if (!DesktopRuntimeLog.debugEnabled) {
                intervalsMs.clear()
                lastFrameNanos = 0L
                lastLogNanos = 0L
                delay(1_000)
                continue
            }

            val frameNanos = withFrameNanos { it }
            if (lastFrameNanos != 0L) {
                val deltaMs = (frameNanos - lastFrameNanos) / 1_000_000.0
                if (deltaMs in 0.1..1_000.0) {
                    intervalsMs += deltaMs
                }
            }

            if (lastLogNanos == 0L) {
                lastLogNanos = frameNanos
            } else if (frameNanos - lastLogNanos >= FullscreenDiagnosticsLogIntervalNanos && intervalsMs.isNotEmpty()) {
                DesktopRuntimeLog.info(
                    "FullscreenDiagnostics framePacing " +
                        DesktopBorderlessFullscreenController.diagnosticSummary(window) +
                        " ${intervalsMs.frameStats(frameNanos - lastLogNanos)}",
                )
                intervalsMs.clear()
                lastLogNanos = frameNanos
            }

            lastFrameNanos = frameNanos
        }
    }
}

private fun List<Double>.frameStats(elapsedNanos: Long): String {
    val sorted = sorted()
    val avgMs = average()
    val fps = size * 1_000_000_000.0 / elapsedNanos.coerceAtLeast(1L)
    val p50Ms = sorted.percentile(0.50)
    val p95Ms = sorted.percentile(0.95)
    val p99Ms = sorted.percentile(0.99)
    val maxMs = sorted.last()
    val over16 = count { it > 16.7 }
    val over25 = count { it > 25.0 }
    val over33 = count { it > 33.4 }
    return "frames=$size fps=${fps.formatOneDecimal()} avgMs=${avgMs.formatOneDecimal()} " +
        "p50Ms=${p50Ms.formatOneDecimal()} p95Ms=${p95Ms.formatOneDecimal()} " +
        "p99Ms=${p99Ms.formatOneDecimal()} maxMs=${maxMs.formatOneDecimal()} " +
        "over16ms=$over16 over25ms=$over25 over33ms=$over33"
}

private fun List<Double>.percentile(fraction: Double): Double {
    if (isEmpty()) return 0.0
    val index = ((size - 1) * fraction).roundToInt().coerceIn(0, size - 1)
    return this[index]
}

private fun Double.formatOneDecimal(): String =
    String.format(Locale.US, "%.1f", this)
