package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.RenderSettings
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.nuvio.app.LocalDesktopWindow
import com.nuvio.app.desktop.DesktopRuntimeLog
import com.nuvio.app.features.player.desktop.mpv.MpvDesktopSurfaceMode
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JWindow
import kotlin.math.roundToInt
import java.awt.Color as AwtColor

private const val DefaultPlayerOverlayRepaintHz = 120

@Composable
actual fun PlayerOverlayLayer(
    layoutSize: IntSize,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!usesOwnedPlayerOverlayWindow() || layoutSize.width <= 0 || layoutSize.height <= 0) {
        Box(
            modifier = modifier,
            content = content,
        )
        return
    }

    var boundsInWindow by remember { mutableStateOf<IntRect?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                boundsInWindow = IntRect(
                    left = bounds.left.roundToInt(),
                    top = bounds.top.roundToInt(),
                    right = bounds.right.roundToInt(),
                    bottom = bounds.bottom.roundToInt(),
                )
            },
    )

    DesktopOwnedPlayerOverlayWindow(
        boundsInWindow = boundsInWindow,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun DesktopOwnedPlayerOverlayWindow(
    boundsInWindow: IntRect?,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val owner = LocalDesktopWindow.current
    val compositionLocalContext = currentCompositionLocalContext
    val latestBounds by rememberUpdatedState(boundsInWindow)
    val latestModifier by rememberUpdatedState(modifier)
    val latestContent by rememberUpdatedState(content)
    val latestCompositionLocalContext by rememberUpdatedState(compositionLocalContext)

    val overlay = remember(owner) {
        owner?.let(::DesktopPlayerOverlayWindow)
    }

    DisposableEffect(overlay) {
        val currentOverlay = overlay ?: return@DisposableEffect onDispose {}
        currentOverlay.panel.setContent {
                    CompositionLocalProvider(latestCompositionLocalContext) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(latestModifier),
                            content = latestContent,
                        )
            }
        }
        currentOverlay.renderOnceIfNeeded()
        onDispose {
            currentOverlay.dispose()
        }
    }

    DisposableEffect(overlay, owner) {
        val currentOverlay = overlay ?: return@DisposableEffect onDispose {}
        val currentOwner = owner ?: return@DisposableEffect onDispose {}
        val listener = object : ComponentAdapter() {
            override fun componentMoved(event: ComponentEvent) {
                currentOverlay.updateBounds(latestBounds)
            }

            override fun componentResized(event: ComponentEvent) {
                currentOverlay.updateBounds(latestBounds)
            }

            override fun componentShown(event: ComponentEvent) {
                currentOverlay.updateBounds(latestBounds)
            }

            override fun componentHidden(event: ComponentEvent) {
                currentOverlay.hide()
            }
        }
        currentOwner.addComponentListener(listener)
        onDispose {
            currentOwner.removeComponentListener(listener)
        }
    }

    SideEffect {
        overlay?.updateBounds(boundsInWindow)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private class DesktopPlayerOverlayWindow(
    private val owner: Window,
) {
    @Volatile
    private var disposed: Boolean = false

    @Volatile
    private var pendingBounds: IntRect? = null

    private val updateQueued = AtomicBoolean(false)
    private val renderConfig = desktopPlayerOverlayRenderConfig(owner)
    private val overlayName = "player-overlay-${System.identityHashCode(this)}"

    val panel: ComposePanel = ComposePanel(
        renderSettings = renderConfig.renderSettings,
    ).apply {
        isOpaque = false
        background = OverlayHitTestAwtColor
        isFocusable = true
    }

    private val window = JWindow(owner).apply {
        type = renderConfig.windowType
        background = OverlayHitTestAwtColor
        rootPane.isOpaque = false
        rootPane.background = OverlayHitTestAwtColor
        contentPane = panel
        focusableWindowState = true
        isAutoRequestFocus = false
    }

    private val repaintPump: DesktopPlayerOverlayRepaintPump? = renderConfig.repaintHz?.let { repaintHz ->
        DesktopPlayerOverlayRepaintPump(
            name = overlayName,
            repaintHz = repaintHz,
            dispatchOnEventQueue = renderConfig.dispatchPaintOnEventQueue,
            shouldRepaint = { !disposed && window.isVisible && panel.isShowing },
            paintFrame = {
                when (renderConfig.paintMode) {
                    DesktopPlayerOverlayPaintMode.RenderImmediately -> panel.renderImmediately()
                    DesktopPlayerOverlayPaintMode.Immediate -> {
                        if (panel.width > 0 && panel.height > 0) {
                            panel.paintImmediately(0, 0, panel.width, panel.height)
                        } else {
                            panel.repaint()
                        }
                    }
                    DesktopPlayerOverlayPaintMode.Repaint -> panel.repaint()
                }
            },
        )
    }

    init {
        DesktopRuntimeLog.info(
            "playerOverlay renderer=${renderConfig.name} repaintHz=${renderConfig.repaintHz?.toString() ?: "off"} " +
                "paintMode=${renderConfig.paintMode.logName} " +
                "paintDispatch=${if (renderConfig.dispatchPaintOnEventQueue) "event-queue" else "direct"} " +
                "windowType=${renderConfig.windowType}",
        )
    }

    fun updateBounds(boundsInWindow: IntRect?) {
        if (disposed) return
        pendingBounds = boundsInWindow
        if (EventQueue.isDispatchThread()) {
            updateQueued.set(false)
            updateBoundsOnEventQueue(pendingBounds)
            return
        }
        if (updateQueued.compareAndSet(false, true)) {
            EventQueue.invokeLater {
                updateQueued.set(false)
                if (!disposed) {
                    updateBoundsOnEventQueue(pendingBounds)
                }
            }
        }
    }

    private fun updateBoundsOnEventQueue(boundsInWindow: IntRect?) {
        if (disposed) return
        if (boundsInWindow == null || boundsInWindow.width <= 0 || boundsInWindow.height <= 0 || !owner.isShowing) {
            hideOnEventQueue()
            return
        }

        val origin = runCatching {
            (owner as? ComposeWindow)?.contentPane?.locationOnScreen ?: owner.locationOnScreen
        }.getOrElse {
            hideOnEventQueue()
            return
        }

        val x = origin.x + boundsInWindow.left
        val y = origin.y + boundsInWindow.top
        val width = boundsInWindow.width.coerceAtLeast(1)
        val height = boundsInWindow.height.coerceAtLeast(1)
        if (
            window.x != x ||
            window.y != y ||
            window.width != width ||
            window.height != height
        ) {
            window.setBounds(x, y, width, height)
            panel.setBounds(0, 0, width, height)
            panel.revalidate()
        }
        if (!window.isVisible) {
            window.isVisible = true
            window.toFront()
            DesktopRuntimeLog.info("playerOverlay visible bounds=${width}x$height at $x,$y")
            renderOnceIfNeeded()
        }
        startRepaintPump()
    }

    fun renderOnceIfNeeded() {
        if (disposed || renderConfig.paintMode != DesktopPlayerOverlayPaintMode.RenderImmediately) return
        if (EventQueue.isDispatchThread()) {
            panel.renderImmediately()
        } else {
            EventQueue.invokeLater {
                if (!disposed && window.isVisible && panel.isShowing) {
                    panel.renderImmediately()
                }
            }
        }
    }

    fun hide() {
        if (EventQueue.isDispatchThread()) {
            hideOnEventQueue()
        } else {
            EventQueue.invokeLater {
                if (!disposed) {
                    hideOnEventQueue()
                }
            }
        }
    }

    private fun hideOnEventQueue() {
        stopRepaintPump()
        if (window.isVisible) {
            window.isVisible = false
            DesktopRuntimeLog.info("playerOverlay hidden")
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        pendingBounds = null
        val disposeOnEventQueue = {
            runCatching {
                hideOnEventQueue()
                panel.isVisible = false
            }.onFailure {
                DesktopRuntimeLog.error("playerOverlay hide before dispose failed", it)
            }
            runCatching {
                panel.dispose()
            }.onFailure {
                DesktopRuntimeLog.error("playerOverlay panel dispose failed", it)
            }
            runCatching {
                window.dispose()
            }.onFailure {
                DesktopRuntimeLog.error("playerOverlay window dispose failed", it)
            }
            DesktopRuntimeLog.info("playerOverlay disposed")
        }
        if (EventQueue.isDispatchThread()) {
            disposeOnEventQueue()
        } else {
            runCatching {
                EventQueue.invokeAndWait { disposeOnEventQueue() }
            }.onFailure {
                DesktopRuntimeLog.error("playerOverlay synchronous dispose failed", it)
                EventQueue.invokeLater { disposeOnEventQueue() }
            }
        }
    }

    private fun startRepaintPump() {
        repaintPump?.start()
    }

    private fun stopRepaintPump() {
        repaintPump?.stop()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun desktopPlayerOverlayRenderConfig(owner: Window): DesktopPlayerOverlayRenderConfig {
    val renderer = System.getenv("NUVIO_PLAYER_OVERLAY_RENDERER")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.lowercase(Locale.US)
        ?.replace('_', '-')
        ?.let { configured ->
            when (configured) {
                "auto", "default", "best" -> defaultDesktopPlayerOverlayRenderer()
                else -> configured
            }
        }
        ?: defaultDesktopPlayerOverlayRenderer()
    return when (renderer) {
        "skia-surface", "hardware-skia", "window-skia", "angle-surface" -> DesktopPlayerOverlayRenderConfig(
            name = "skia-surface",
            renderSettings = RenderSettings.SkiaSurface(skiaOverlayVsyncEnabled()),
            repaintHz = desktopPlayerOverlayRepaintHz(owner),
            paintMode = DesktopPlayerOverlayPaintMode.RenderImmediately,
            dispatchPaintOnEventQueue = false,
            windowType = desktopPlayerOverlayWindowType(default = Window.Type.NORMAL),
        )

        "skia", "skia-swing", "swing-direct", "direct" -> DesktopPlayerOverlayRenderConfig(
            name = "skia-swing",
            renderSettings = RenderSettings.SwingGraphics(),
            repaintHz = desktopPlayerOverlayRepaintHz(owner),
            paintMode = DesktopPlayerOverlayPaintMode.RenderImmediately,
            dispatchPaintOnEventQueue = false,
            windowType = desktopPlayerOverlayWindowType(default = Window.Type.POPUP),
        )

        "swing", "swing-graphics", "gdi" -> DesktopPlayerOverlayRenderConfig(
            name = "swing",
            renderSettings = RenderSettings.SwingGraphics(),
            repaintHz = desktopPlayerOverlayRepaintHz(owner),
            paintMode = desktopPlayerOverlayPaintMode(),
            dispatchPaintOnEventQueue = true,
            windowType = desktopPlayerOverlayWindowType(default = Window.Type.POPUP),
        )

        else -> DesktopPlayerOverlayRenderConfig(
            name = "skia-swing",
            renderSettings = RenderSettings.SwingGraphics(),
            repaintHz = desktopPlayerOverlayRepaintHz(owner),
            paintMode = DesktopPlayerOverlayPaintMode.RenderImmediately,
            dispatchPaintOnEventQueue = false,
            windowType = desktopPlayerOverlayWindowType(default = Window.Type.POPUP),
        )
    }
}

private fun defaultDesktopPlayerOverlayRenderer(): String =
    if (System.getProperty("skiko.renderApi").equals("ANGLE", ignoreCase = true)) {
        "skia-swing"
    } else {
        "swing"
    }

@OptIn(ExperimentalComposeUiApi::class)
private data class DesktopPlayerOverlayRenderConfig(
    val name: String,
    val renderSettings: RenderSettings,
    val repaintHz: Int?,
    val paintMode: DesktopPlayerOverlayPaintMode = DesktopPlayerOverlayPaintMode.Immediate,
    val dispatchPaintOnEventQueue: Boolean = true,
    val windowType: Window.Type = Window.Type.POPUP,
)

private enum class DesktopPlayerOverlayPaintMode(val logName: String) {
    RenderImmediately("render-immediately"),
    Immediate("immediate"),
    Repaint("repaint"),
}

private fun skiaOverlayVsyncEnabled(): Boolean? =
    System.getenv("NUVIO_PLAYER_OVERLAY_SKIA_VSYNC")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let(::parseBoolean)

private fun desktopPlayerOverlayRepaintHz(owner: Window): Int? {
    val configured = System.getenv("NUVIO_PLAYER_OVERLAY_REPAINT_HZ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
    if (configured != null) {
        return when (configured.lowercase(Locale.US)) {
            "0", "off", "false", "no", "disabled" -> null
            else -> configured.toIntOrNull()?.coerceIn(30, 240)
        }
    }
    val displayRefreshRate = owner
        .graphicsConfiguration
        ?.device
        ?.displayMode
        ?.refreshRate
        ?.takeIf { it > 0 }
    return (displayRefreshRate ?: DefaultPlayerOverlayRepaintHz).coerceIn(30, 240)
}

private fun desktopPlayerOverlayWindowType(default: Window.Type): Window.Type {
    val configured = System.getenv("NUVIO_PLAYER_OVERLAY_WINDOW_TYPE")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.lowercase(Locale.US)
        ?.replace('_', '-')
        ?: return default
    return when (configured) {
        "normal" -> Window.Type.NORMAL
        "popup", "pop-up" -> Window.Type.POPUP
        "utility" -> Window.Type.UTILITY
        else -> default
    }
}

private fun desktopPlayerOverlayPaintMode(): DesktopPlayerOverlayPaintMode {
    val configured = System.getenv("NUVIO_PLAYER_OVERLAY_PAINT_MODE")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.lowercase(Locale.US)
        ?.replace('_', '-')
    return when (configured) {
        "render", "render-immediately", "skia", "direct" -> DesktopPlayerOverlayPaintMode.RenderImmediately
        "immediate", "paint-immediately", "force" -> DesktopPlayerOverlayPaintMode.Immediate
        "repaint", "coalesced", "coalesce" -> DesktopPlayerOverlayPaintMode.Repaint
        else -> DesktopPlayerOverlayPaintMode.Immediate
    }
}

private fun parseBoolean(value: String): Boolean? =
    when (value.lowercase(Locale.US)) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> null
    }

private val OverlayHitTestAwtColor = AwtColor(0, 0, 0, 1)

private fun usesOwnedPlayerOverlayWindow(): Boolean {
    if (!isWindowsDesktopPlayerOverlay()) return false
    if (System.getProperty("compose.interop.blending").equals("true", ignoreCase = true)) return false
    return MpvDesktopSurfaceMode.resolve() == MpvDesktopSurfaceMode.NativeWindow
}

private fun isWindowsDesktopPlayerOverlay(): Boolean =
    System.getProperty("os.name")
        ?.lowercase(Locale.US)
        ?.contains("windows") == true
