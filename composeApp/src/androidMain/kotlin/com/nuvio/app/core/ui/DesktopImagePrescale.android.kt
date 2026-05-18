package com.nuvio.app.core.ui

import coil3.request.ImageRequest
import coil3.size.Scale

internal actual fun ImageRequest.Builder.nuvioPrescaleToDrawSize(
    widthPx: Int,
    heightPx: Int,
    scale: Scale,
): ImageRequest.Builder = this
