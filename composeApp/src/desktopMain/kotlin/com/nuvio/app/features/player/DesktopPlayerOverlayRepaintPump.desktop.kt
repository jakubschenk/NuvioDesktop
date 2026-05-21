package com.nuvio.app.features.player

import com.nuvio.app.desktop.DesktopRuntimeLog
import java.awt.EventQueue
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

internal class DesktopPlayerOverlayRepaintPump(
    private val name: String,
    repaintHz: Int,
    private val dispatchOnEventQueue: Boolean = true,
    private val shouldRepaint: () -> Boolean,
    private val paintFrame: () -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val paintQueued = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val queuedCount = AtomicLong(0L)
    private val coalescedCount = AtomicLong(0L)
    private val skippedCount = AtomicLong(0L)
    private val eventQueueLatencyCount = AtomicLong(0L)
    private val eventQueueLatencyTotalNanos = AtomicLong(0L)
    private val eventQueueLatencyMaxNanos = AtomicLong(0L)
    private val paintCallCount = AtomicLong(0L)
    private val paintCallTotalNanos = AtomicLong(0L)
    private val paintCallMaxNanos = AtomicLong(0L)
    private val intervalNanos = (1_000_000_000L / repaintHz.coerceIn(30, 240)).coerceAtLeast(1L)

    @Volatile
    private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val runGeneration = generation.incrementAndGet()
        thread = Thread({ runLoop(runGeneration) }, "nuvio-$name-repaint").apply {
            isDaemon = true
            start()
        }
        DesktopRuntimeLog.debug("playerOverlay repaintPump started name=$name intervalNs=$intervalNanos")
        DesktopPlayerPerfTrace.info("overlayPump start name=$name targetHz=${formatOneDecimal(1_000_000_000.0 / intervalNanos)}")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        generation.incrementAndGet()
        thread?.let(LockSupport::unpark)
        paintQueued.set(false)
        DesktopRuntimeLog.debug("playerOverlay repaintPump stopped name=$name")
        DesktopPlayerPerfTrace.info("overlayPump stop name=$name")
    }

    private fun runLoop(runGeneration: Int) {
        try {
            var nextFrameNanos = System.nanoTime()
            var paintedFrameCount = 0
            var lastLogNanos = nextFrameNanos
            while (running.get() && generation.get() == runGeneration) {
                nextFrameNanos += intervalNanos
                if (queuePaintIfNeeded()) {
                    paintedFrameCount += 1
                }

                val now = System.nanoTime()
                if (DesktopRuntimeLog.debugEnabled && now - lastLogNanos >= 2_000_000_000L) {
                    val elapsedSeconds = (now - lastLogNanos).toDouble() / 1_000_000_000.0
                    DesktopRuntimeLog.debug(
                        "playerOverlay repaintPump rate name=$name fps=${formatOneDecimal(paintedFrameCount / elapsedSeconds)}",
                    )
                    logPerfSnapshot(now, lastLogNanos, paintedFrameCount)
                    paintedFrameCount = 0
                    lastLogNanos = now
                } else if (DesktopPlayerPerfTrace.enabled && now - lastLogNanos >= 2_000_000_000L) {
                    logPerfSnapshot(now, lastLogNanos, paintedFrameCount)
                    paintedFrameCount = 0
                    lastLogNanos = now
                }

                val sleepNanos = nextFrameNanos - System.nanoTime()
                if (sleepNanos > 0) {
                    LockSupport.parkNanos(this, sleepNanos)
                } else {
                    nextFrameNanos = System.nanoTime()
                }
            }
        } finally {
            if (thread === Thread.currentThread() && generation.get() == runGeneration) {
                thread = null
            }
        }
    }

    private fun queuePaintIfNeeded(): Boolean {
        if (!shouldRepaint()) {
            skippedCount.incrementAndGet()
            return false
        }
        if (!paintQueued.compareAndSet(false, true)) {
            coalescedCount.incrementAndGet()
            return false
        }
        queuedCount.incrementAndGet()
        val queuedAtNanos = System.nanoTime()
        if (dispatchOnEventQueue) {
            EventQueue.invokeLater {
                paintQueuedFrame(queuedAtNanos = queuedAtNanos, measureEventLatency = true)
            }
        } else {
            paintQueuedFrame(queuedAtNanos = queuedAtNanos, measureEventLatency = false)
        }
        return true
    }

    private fun paintQueuedFrame(
        queuedAtNanos: Long,
        measureEventLatency: Boolean,
    ) {
        try {
            if (measureEventLatency) {
                val eventLatencyNanos = System.nanoTime() - queuedAtNanos
                eventQueueLatencyCount.incrementAndGet()
                eventQueueLatencyTotalNanos.addAndGet(eventLatencyNanos)
                eventQueueLatencyMaxNanos.updateMax(eventLatencyNanos)
            }
            if (running.get() && shouldRepaint()) {
                val startNanos = System.nanoTime()
                paintFrame()
                val elapsedNanos = System.nanoTime() - startNanos
                paintCallCount.incrementAndGet()
                paintCallTotalNanos.addAndGet(elapsedNanos)
                paintCallMaxNanos.updateMax(elapsedNanos)
            } else {
                skippedCount.incrementAndGet()
            }
        } catch (throwable: Throwable) {
            DesktopRuntimeLog.error("playerOverlay repaintPump frame failed name=$name", throwable)
        } finally {
            paintQueued.set(false)
        }
    }

    private fun logPerfSnapshot(
        nowNanos: Long,
        previousLogNanos: Long,
        queuedFrames: Int,
    ) {
        if (!DesktopPlayerPerfTrace.enabled) return
        val elapsedSeconds = (nowNanos - previousLogNanos).toDouble() / 1_000_000_000.0
        val paintCalls = paintCallCount.getAndSet(0L)
        val paintTotalNanos = paintCallTotalNanos.getAndSet(0L)
        val paintMaxNanos = paintCallMaxNanos.getAndSet(0L)
        val eventQueueCalls = eventQueueLatencyCount.getAndSet(0L)
        val eventQueueTotalNanos = eventQueueLatencyTotalNanos.getAndSet(0L)
        val eventQueueMaxNanos = eventQueueLatencyMaxNanos.getAndSet(0L)
        DesktopPlayerPerfTrace.info(
            "overlayPump name=$name queueFps=${formatOneDecimal(queuedFrames / elapsedSeconds)} " +
                "queued=${queuedCount.getAndSet(0L)} coalesced=${coalescedCount.getAndSet(0L)} skipped=${skippedCount.getAndSet(0L)} " +
                "eventAvgMs=${formatMillisAverage(eventQueueTotalNanos, eventQueueCalls)} " +
                "eventMaxMs=${formatNanosAsMillis(eventQueueMaxNanos)} " +
                "paintCalls=$paintCalls paintAvgMs=${formatMillisAverage(paintTotalNanos, paintCalls)} " +
                "paintMaxMs=${formatNanosAsMillis(paintMaxNanos)}",
        )
    }

    private fun formatOneDecimal(value: Double): String =
        String.format(Locale.US, "%.1f", value)

    private fun formatMillisAverage(totalNanos: Long, count: Long): String =
        if (count <= 0L) "-" else formatNanosAsMillis(totalNanos / count)

    private fun formatNanosAsMillis(nanos: Long): String =
        if (nanos <= 0L) "-" else String.format(Locale.US, "%.3f", nanos / 1_000_000.0)
}

private fun AtomicLong.updateMax(candidate: Long) {
    while (true) {
        val current = get()
        if (candidate <= current) return
        if (compareAndSet(current, candidate)) return
    }
}
