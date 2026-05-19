package com.nuvio.app.core.ui

import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Scale
import coil3.size.Size
import coil3.transform.Transformation
import org.jetbrains.skia.Bitmap

internal actual fun ImageRequest.Builder.nuvioPrescaleToDrawSize(
    widthPx: Int,
    heightPx: Int,
    scale: Scale,
): ImageRequest.Builder {
    if (widthPx <= 0 || heightPx <= 0) return this
    if (scale != Scale.FILL) return this
    return transformations(NuvioDesktopPrescaleTransformation(widthPx, heightPx))
}

private class NuvioDesktopPrescaleTransformation(
    private val widthPx: Int,
    private val heightPx: Int,
) : Transformation() {
    override val cacheKey: String = "nuvio_desktop_prescale_progressive_thumb_v5:$widthPx:$heightPx"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return input.nuvioScaleToFillBitmap(widthPx, heightPx) ?: input
    }
}
