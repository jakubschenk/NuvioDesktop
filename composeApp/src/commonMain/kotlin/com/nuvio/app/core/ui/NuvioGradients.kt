package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier

private fun nuvioOverlayGradientStops(): Array<Pair<Float, Color>> =
    arrayOf(
        0f to Color(0xFF21113B),
        0.12f to Color(0xFF21113B),
        0.24f to Color(0xFF1A0E2F),
        0.34f to Color(0xFF130A23),
        0.44f to Color(0xFF0A060F),
        0.58f to Color(0xFF050408),
        0.64f to Color.Black,
        1f to Color.Black,
    )

fun Modifier.nuvioOverlayGradientBackground(): Modifier =
    nuvioPlatformLinearGradientBackground(
        colorStops = nuvioOverlayGradientStops(),
        axis = NuvioLinearGradientAxis.DiagonalDown,
    )
