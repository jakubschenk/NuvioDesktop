package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.CubicResampler
import org.jetbrains.skia.EncodedImageFormat
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

    nuvioScaleToFillBitmapWithJava2D(
        widthPx = widthPx,
        heightPx = heightPx,
        sourceRect = sourceRect,
    )?.let { return it }

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

private fun Bitmap.nuvioScaleToFillBitmapWithJava2D(
    widthPx: Int,
    heightPx: Int,
    sourceRect: Rect,
): Bitmap? {
    val source = nuvioToBufferedImage() ?: return null
    val cropX = sourceRect.left.roundToInt().coerceIn(0, source.width - 1)
    val cropY = sourceRect.top.roundToInt().coerceIn(0, source.height - 1)
    val cropRight = sourceRect.right.roundToInt().coerceIn(cropX + 1, source.width)
    val cropBottom = sourceRect.bottom.roundToInt().coerceIn(cropY + 1, source.height)
    val cropWidth = cropRight - cropX
    val cropHeight = cropBottom - cropY
    if (cropWidth <= 0 || cropHeight <= 0) return null

    val cropped = source.getSubimage(cropX, cropY, cropWidth, cropHeight)
    return cropped
        .nuvioProgressiveResize(widthPx, heightPx)
        .nuvioToSkiaBitmap()
}

private fun Bitmap.nuvioToBufferedImage(): BufferedImage? {
    val image = Image.makeFromBitmap(this)
    val data = try {
        image.encodeToData(EncodedImageFormat.PNG, 100)
    } finally {
        image.close()
    } ?: return null
    return try {
        ImageIO.read(ByteArrayInputStream(data.bytes))
    } finally {
        data.close()
    }
}

private fun BufferedImage.nuvioProgressiveResize(
    widthPx: Int,
    heightPx: Int,
): BufferedImage {
    if (width == widthPx && height == heightPx) return this

    var current = this
    while (current.width > widthPx * 2 || current.height > heightPx * 2) {
        val nextWidth = (current.width / 2).coerceAtLeast(widthPx)
        val nextHeight = (current.height / 2).coerceAtLeast(heightPx)
        current = current.nuvioResizeOnce(nextWidth, nextHeight)
    }
    return current.nuvioResizeOnce(widthPx, heightPx)
}

private fun BufferedImage.nuvioResizeOnce(
    widthPx: Int,
    heightPx: Int,
): BufferedImage {
    val output = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
    val graphics = output.createGraphics()
    try {
        graphics.composite = AlphaComposite.Src
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        graphics.drawImage(this, 0, 0, widthPx, heightPx, null)
    } finally {
        graphics.dispose()
    }
    return output
}

private fun BufferedImage.nuvioToSkiaBitmap(): Bitmap? {
    val output = ByteArrayOutputStream()
    if (!ImageIO.write(this, "png", output)) return null
    val image = Image.makeFromEncoded(output.toByteArray())
    return try {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(image.width, image.height)
        if (image.readPixels(bitmap)) bitmap else null
    } finally {
        image.close()
    }
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
