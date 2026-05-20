package com.nuvio.app.features.player.desktop.mpv

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import com.nuvio.app.desktop.DesktopRuntimeLog
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.EventQueue
import java.awt.Graphics
import javax.swing.JPanel
import java.awt.Color as AwtColor

@OptIn(InternalMediampApi::class)
@Composable
internal fun MpvDesktopPlayerSurface(
    player: MpvMediampPlayer,
    modifier: Modifier,
    surfaceMode: MpvDesktopSurfaceMode,
    onSurfaceReady: () -> Unit,
    attachNativeSurface: (Long) -> Boolean = { windowPtr -> player.attachRenderSurface(windowPtr) },
    detachNativeSurface: () -> Boolean = { player.detachRenderSurface() },
) {
    when (surfaceMode) {
        MpvDesktopSurfaceMode.OpenGlInterop -> {
            onSurfaceReady()
            MpvMediampPlayerSurface(
                player = player,
                modifier = modifier,
            )
        }

        MpvDesktopSurfaceMode.NativeWindow -> {
            val latestOnSurfaceReady = rememberUpdatedState(onSurfaceReady)
            DisposableEffect(player) {
                onDispose {
                    runCatching { detachNativeSurface() }
                        .onFailure { DesktopRuntimeLog.warn("MPV native surface detach failed: ${it.message}") }
                }
            }
            SwingPanel(
                modifier = modifier,
                background = Color.Black,
                factory = {
                    MpvNativeWindowPanel(
                        attachNativeSurface = attachNativeSurface,
                        detachNativeSurface = detachNativeSurface,
                        onSurfaceReady = { latestOnSurfaceReady.value() },
                    )
                },
                update = { panel ->
                    panel.attachIfReady()
                },
            )
        }
    }
}

@OptIn(InternalMediampApi::class)
private class MpvNativeWindowPanel(
    private val attachNativeSurface: (Long) -> Boolean,
    private val detachNativeSurface: () -> Boolean,
    private val onSurfaceReady: () -> Unit,
) : JPanel(BorderLayout()) {
    private val canvas = object : Canvas() {
        override fun update(graphics: Graphics) {
            paint(graphics)
        }

        override fun paint(graphics: Graphics) {
            graphics.color = AwtColor.BLACK
            graphics.fillRect(0, 0, width, height)
        }
    }
    private var attachedWindowPtr: Long = 0L

    init {
        isOpaque = true
        isDoubleBuffered = false
        isFocusable = false
        background = AwtColor.BLACK
        canvas.background = AwtColor.BLACK
        canvas.foreground = AwtColor.BLACK
        canvas.isFocusable = false
        add(canvas, BorderLayout.CENTER)
    }

    override fun addNotify() {
        super.addNotify()
        attachLater()
    }

    override fun removeNotify() {
        detachIfNeeded()
        super.removeNotify()
    }

    fun attachIfReady() {
        if (!canvas.isDisplayable) return
        val windowPtr = Native.getComponentPointer(canvas)
            ?.let(Pointer::nativeValue)
            ?.takeIf { it != 0L }
            ?: return
        if (attachedWindowPtr == windowPtr) return
        runCatching {
            val attached = attachNativeSurface(windowPtr)
            if (attached) {
                attachedWindowPtr = windowPtr
                DesktopRuntimeLog.info("MPV native HWND surface attached hwnd=0x${windowPtr.toString(16)}")
                onSurfaceReady()
            } else {
                DesktopRuntimeLog.warn("MPV native HWND surface attach returned false hwnd=0x${windowPtr.toString(16)}")
            }
        }.onFailure {
            DesktopRuntimeLog.error("MPV native HWND surface attach failed hwnd=0x${windowPtr.toString(16)}", it)
        }
    }

    private fun attachLater() {
        EventQueue.invokeLater {
            attachIfReady()
        }
    }

    private fun detachIfNeeded() {
        val previousWindowPtr = attachedWindowPtr
        if (previousWindowPtr == 0L) return
        attachedWindowPtr = 0L
        runCatching { detachNativeSurface() }
            .onSuccess {
                DesktopRuntimeLog.info("MPV native HWND surface detached hwnd=0x${previousWindowPtr.toString(16)}")
            }
            .onFailure {
                DesktopRuntimeLog.warn("MPV native HWND surface detach failed hwnd=0x${previousWindowPtr.toString(16)} message=${it.message}")
            }
    }
}
