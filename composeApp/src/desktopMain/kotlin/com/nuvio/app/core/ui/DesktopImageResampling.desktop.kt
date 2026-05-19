package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.CubicResampler
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SourceRectEpsilon = 0.5f

private val NuvioDesktopCropSampling = CubicResampler(1f / 3f, 1f / 3f)
private val NuvioDesktopDownsampleSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

internal fun Bitmap.nuvioScaleToBitmap(
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    if (width == widthPx && height == heightPx) return this

    return nuvioScalePixelsToBitmap(widthPx, heightPx)
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

    val cropped = if (sourceRect.isWholeBitmap(width, height)) {
        this
    } else {
        nuvioDrawToBitmap(
            widthPx = sourceRect.width.roundToInt().coerceAtLeast(1),
            heightPx = sourceRect.height.roundToInt().coerceAtLeast(1),
            sourceRect = sourceRect,
        ) ?: return null
    }
    val scaled = cropped.nuvioScalePixelsToBitmap(widthPx, heightPx)
    if (cropped !== this && scaled !== cropped) {
        cropped.close()
    }
    return scaled
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
            samplingMode = NuvioDesktopCropSampling,
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

private fun Bitmap.nuvioScalePixelsToBitmap(
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
            samplingMode = NuvioDesktopDownsampleSampling,
            cache = false,
        )
        if (scaled) output else null
    } finally {
        image.close()
    }
}

private fun Rect.isWholeBitmap(widthPx: Int, heightPx: Int): Boolean =
    abs(left) < SourceRectEpsilon &&
        abs(top) < SourceRectEpsilon &&
        abs(right - widthPx) < SourceRectEpsilon &&
        abs(bottom - heightPx) < SourceRectEpsilon

internal fun Bitmap.nuvioScaleToImageBitmap(size: IntSize): ImageBitmap {
    val scaled = nuvioScaleToBitmap(
        widthPx = size.width,
        heightPx = size.height,
    ) ?: return asComposeImageBitmap()

    if (scaled === this) return asComposeImageBitmap()
    return Image.makeFromBitmap(scaled).toComposeImageBitmap()
}
