package com.nuvio.app.core.ui

import androidx.compose.ui.Alignment
import coil3.request.ImageRequest
import coil3.size.Scale

internal expect fun ImageRequest.Builder.nuvioPrescaleToDrawSize(
    widthPx: Int,
    heightPx: Int,
    scale: Scale,
    alignment: Alignment,
): ImageRequest.Builder
