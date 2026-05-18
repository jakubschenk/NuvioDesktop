package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.FilterQuality
import coil3.compose.AsyncImagePainter

internal expect fun AsyncImagePainter.State.withNuvioImagePainterWorkaround(
    filterQuality: FilterQuality,
): AsyncImagePainter.State
