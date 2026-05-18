package com.nuvio.app.core.ui

import coil3.request.ImageRequest

internal expect fun ImageRequest.Builder.nuvioPrescaleToDrawSize(
    widthPx: Int,
    heightPx: Int,
): ImageRequest.Builder
