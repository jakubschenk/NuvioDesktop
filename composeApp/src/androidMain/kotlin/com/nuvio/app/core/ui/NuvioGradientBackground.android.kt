package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal actual fun Modifier.nuvioPlatformLinearGradientBackground(
    colorStops: Array<Pair<Float, Color>>,
    axis: NuvioLinearGradientAxis,
): Modifier {
    if (colorStops.isEmpty()) return this
    val brush = when (axis) {
        NuvioLinearGradientAxis.Vertical -> Brush.verticalGradient(colorStops = colorStops)
        NuvioLinearGradientAxis.Horizontal -> Brush.horizontalGradient(colorStops = colorStops)
        NuvioLinearGradientAxis.DiagonalDown -> Brush.linearGradient(
            colorStops = colorStops,
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    }
    return background(brush)
}
