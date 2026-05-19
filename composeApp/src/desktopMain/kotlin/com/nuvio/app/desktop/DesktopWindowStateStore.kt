package com.nuvio.app.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window as AwtWindow

/**
 * Persists main window size and optional display placement between sessions.
 */
internal object DesktopWindowStateStore {
    private const val namespace = "nuvio_desktop_window"
    private const val keyWidthDp = "width_dp"
    private const val keyHeightDp = "height_dp"
    private const val keyMaximized = "maximized"
    private const val keyX = "x"
    private const val keyY = "y"

    data class Saved(
        val widthDp: Int,
        val heightDp: Int,
        val maximized: Boolean,
        val x: Int?,
        val y: Int?,
    )

    fun load(rememberScreenPlacement: Boolean): Saved? {
        val w = DesktopPreferences.getInt(namespace, keyWidthDp) ?: return null
        val h = DesktopPreferences.getInt(namespace, keyHeightDp) ?: return null
        if (w < MinWidthDp || h < MinHeightDp) return null
        val maximized = DesktopPreferences.getBoolean(namespace, keyMaximized) ?: false
        val rememberedPosition = if (rememberScreenPlacement) {
            val x = DesktopPreferences.getInt(namespace, keyX)
            val y = DesktopPreferences.getInt(namespace, keyY)
            if (x != null && y != null && intersectsAnyScreen(Rectangle(x, y, w, h))) {
                x to y
            } else {
                null
            }
        } else {
            null
        }
        return Saved(
            widthDp = w,
            heightDp = h,
            maximized = maximized,
            x = rememberedPosition?.first,
            y = rememberedPosition?.second,
        )
    }

    /**
     * Skips fullscreen so we do not persist fullscreen bounds as the next floating size.
     */
    fun save(
        size: DpSize,
        placement: WindowPlacement,
        window: AwtWindow?,
        rememberScreenPlacement: Boolean,
    ) {
        if (placement == WindowPlacement.Fullscreen) return

        val maximized = placement == WindowPlacement.Maximized
        val w = size.width.value.toInt().coerceAtLeast(MinWidthDp)
        val h = size.height.value.toInt().coerceAtLeast(MinHeightDp)
        DesktopPreferences.putInt(namespace, keyWidthDp, w)
        DesktopPreferences.putInt(namespace, keyHeightDp, h)
        DesktopPreferences.putBoolean(namespace, keyMaximized, maximized)
        if (rememberScreenPlacement && window != null) {
            DesktopPreferences.putInt(namespace, keyX, window.x)
            DesktopPreferences.putInt(namespace, keyY, window.y)
        } else {
            clearRememberedScreenPlacement()
        }
    }

    fun clearRememberedScreenPlacement() {
        DesktopPreferences.remove(namespace, keyX)
        DesktopPreferences.remove(namespace, keyY)
    }

    private fun intersectsAnyScreen(bounds: Rectangle): Boolean {
        return GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .screenDevices
            .any { device -> device.defaultConfiguration.bounds.intersects(bounds) }
    }

    private const val MinWidthDp = 400
    private const val MinHeightDp = 300
}
