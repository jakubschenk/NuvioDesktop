package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.FilterQuality
import coil3.compose.AsyncImagePainter

internal actual fun AsyncImagePainter.State.withNuvioImagePainterWorkaround(
    filterQuality: FilterQuality,
    preferDirectDraw: Boolean,
): AsyncImagePainter.State = this
