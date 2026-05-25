package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class NuvioLinearGradientAxis {
    Vertical,
    Horizontal,
    DiagonalDown,
}

fun Modifier.nuvioVerticalGradientBackground(
    colors: List<Color>,
): Modifier =
    nuvioPlatformLinearGradientBackground(
        colorStops = nuvioEvenGradientStops(colors),
        axis = NuvioLinearGradientAxis.Vertical,
    )

fun Modifier.nuvioVerticalGradientBackground(
    colorStops: Array<Pair<Float, Color>>,
): Modifier =
    nuvioPlatformLinearGradientBackground(
        colorStops = nuvioNormalizedGradientStops(colorStops),
        axis = NuvioLinearGradientAxis.Vertical,
    )

fun Modifier.nuvioHorizontalGradientBackground(
    colors: List<Color>,
): Modifier =
    nuvioPlatformLinearGradientBackground(
        colorStops = nuvioEvenGradientStops(colors),
        axis = NuvioLinearGradientAxis.Horizontal,
    )

fun Modifier.nuvioHorizontalGradientBackground(
    colorStops: Array<Pair<Float, Color>>,
): Modifier =
    nuvioPlatformLinearGradientBackground(
        colorStops = nuvioNormalizedGradientStops(colorStops),
        axis = NuvioLinearGradientAxis.Horizontal,
    )

internal expect fun Modifier.nuvioPlatformLinearGradientBackground(
    colorStops: Array<Pair<Float, Color>>,
    axis: NuvioLinearGradientAxis,
): Modifier

internal fun nuvioEvenGradientStops(colors: List<Color>): Array<Pair<Float, Color>> =
    when (colors.size) {
        0 -> emptyArray()
        1 -> arrayOf(0f to colors.first(), 1f to colors.first())
        else -> Array(colors.size) { index ->
            index.toFloat() / colors.lastIndex.toFloat() to colors[index]
        }
    }

internal fun nuvioNormalizedGradientStops(
    colorStops: Array<Pair<Float, Color>>,
): Array<Pair<Float, Color>> {
    val sortedStops = colorStops
        .asSequence()
        .filter { (position, _) -> position.isFinite() }
        .map { (position, color) -> position.coerceIn(0f, 1f) to color }
        .sortedBy { (position, _) -> position }
        .toMutableList()

    if (sortedStops.isEmpty()) return emptyArray()
    if (sortedStops.size == 1) {
        val color = sortedStops.first().second
        return arrayOf(0f to color, 1f to color)
    }

    val first = sortedStops.first()
    if (first.first > 0f) {
        sortedStops.add(0, 0f to first.second)
    }

    val last = sortedStops.last()
    if (last.first < 1f) {
        sortedStops.add(1f to last.second)
    }

    return sortedStops.toTypedArray()
}
