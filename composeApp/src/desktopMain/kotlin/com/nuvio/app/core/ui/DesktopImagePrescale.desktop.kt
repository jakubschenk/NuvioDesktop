package com.nuvio.app.core.ui

import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode

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
    override val cacheKey: String = "nuvio_desktop_prescale:$widthPx:$heightPx"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (input.width == widthPx && input.height == heightPx) return input

        val output = Bitmap()
        output.allocN32Pixels(widthPx, heightPx)
        val pixels = output.peekPixels() ?: return input
        val image = Image.makeFromBitmap(input)
        val scaled = image.scalePixels(
            dst = pixels,
            samplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
            cache = false,
        )
        image.close()
        return if (scaled) output else input
    }
}
