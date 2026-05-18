package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.CubicResampler
import org.jetbrains.skia.Image

private val NuvioDesktopImageSampling = CubicResampler(0f, 0.5f)

internal fun Bitmap.nuvioScaleToBitmap(
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    if (width == widthPx && height == heightPx) return this

    val output = Bitmap()
    output.allocN32Pixels(widthPx, heightPx)
    val pixels = output.peekPixels() ?: return null
    val image = Image.makeFromBitmap(this)
    return try {
        val scaled = image.scalePixels(
            dst = pixels,
            samplingMode = NuvioDesktopImageSampling,
            cache = false,
        )
        if (scaled) output else null
    } finally {
        image.close()
    }
}

internal fun Bitmap.nuvioScaleToImageBitmap(size: IntSize): ImageBitmap {
    val scaled = nuvioScaleToBitmap(
        widthPx = size.width,
        heightPx = size.height,
    ) ?: return asComposeImageBitmap()

    if (scaled === this) return asComposeImageBitmap()
    return Image.makeFromBitmap(scaled).toComposeImageBitmap()
}
