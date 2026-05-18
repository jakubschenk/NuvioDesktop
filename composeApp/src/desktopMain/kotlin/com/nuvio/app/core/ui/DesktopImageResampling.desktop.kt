package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.CubicResampler
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect

private val NuvioDesktopImageSampling = CubicResampler(1f / 3f, 1f / 3f)

internal fun Bitmap.nuvioScaleToBitmap(
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    if (width == widthPx && height == heightPx) return this

    return nuvioDrawToBitmap(
        widthPx = widthPx,
        heightPx = heightPx,
        sourceRect = Rect.makeWH(width.toFloat(), height.toFloat()),
    )
}

internal fun Bitmap.nuvioScaleToFillBitmap(
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    if (width <= 0 || height <= 0) return null

    val sourceAspect = width.toFloat() / height.toFloat()
    val targetAspect = widthPx.toFloat() / heightPx.toFloat()
    val sourceRect = if (sourceAspect > targetAspect) {
        val cropWidth = height * targetAspect
        val left = (width - cropWidth) / 2f
        Rect.makeLTRB(left, 0f, left + cropWidth, height.toFloat())
    } else {
        val cropHeight = width / targetAspect
        val top = (height - cropHeight) / 2f
        Rect.makeLTRB(0f, top, width.toFloat(), top + cropHeight)
    }

    return nuvioDrawToBitmap(
        widthPx = widthPx,
        heightPx = heightPx,
        sourceRect = sourceRect,
    )
}

private fun Bitmap.nuvioDrawToBitmap(
    widthPx: Int,
    heightPx: Int,
    sourceRect: Rect,
): Bitmap? {
    val output = Bitmap()
    output.allocN32Pixels(widthPx, heightPx)
    if (output.peekPixels() == null) return null

    val canvas = Canvas(output)
    val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
    }
    val image = Image.makeFromBitmap(this)
    return try {
        output.erase(0x00000000)
        canvas.drawImageRect(
            image = image,
            src = sourceRect,
            dst = Rect.makeWH(widthPx.toFloat(), heightPx.toFloat()),
            samplingMode = NuvioDesktopImageSampling,
            paint = paint,
            strict = true,
        )
        output
    } finally {
        image.close()
        paint.close()
        canvas.close()
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
