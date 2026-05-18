package com.nuvio.app.core.ui

import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import org.jetbrains.skia.Bitmap

internal actual fun ImageRequest.Builder.nuvioPrescaleToDrawSize(
    widthPx: Int,
    heightPx: Int,
): ImageRequest.Builder {
    if (widthPx <= 0 || heightPx <= 0) return this
    return transformations(NuvioDesktopPrescaleTransformation(widthPx, heightPx))
}

private class NuvioDesktopPrescaleTransformation(
    private val widthPx: Int,
    private val heightPx: Int,
) : Transformation() {
    override val cacheKey: String = "nuvio_desktop_prescale_catmull_rom_v1:$widthPx:$heightPx"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return input.nuvioScaleToBitmap(widthPx, heightPx) ?: input
    }
}
