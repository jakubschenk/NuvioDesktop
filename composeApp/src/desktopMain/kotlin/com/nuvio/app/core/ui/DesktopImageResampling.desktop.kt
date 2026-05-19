package com.nuvio.app.core.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.mortennobel.imagescaling.ResampleFilters
import com.mortennobel.imagescaling.ResampleOp
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.CubicResampler
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SourceRectEpsilon = 0.5f
private const val MaxJvmLanczosTargetPixels = 5_500_000
private const val MaxJvmLanczosTargetDimensionPx = 4096

private val NuvioDesktopCropSampling = CubicResampler(1f / 3f, 1f / 3f)
private val NuvioDesktopDownsampleSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

internal fun Bitmap.nuvioScaleToBitmap(
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    if (width == widthPx && height == heightPx) return this

    nuvioScaleToBitmapWithJvmLanczos(widthPx, heightPx)?.let { return it }
    return nuvioScalePixelsToBitmap(widthPx, heightPx)
}

internal fun Bitmap.nuvioScaleToFillBitmap(
    widthPx: Int,
    heightPx: Int,
    alignment: Alignment,
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    if (width <= 0 || height <= 0) return null

    val sourceRect = fillSourceRect(widthPx, heightPx, alignment)

    nuvioScaleToFillBitmapWithJvmLanczos(
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

private fun Bitmap.fillSourceRect(
    widthPx: Int,
    heightPx: Int,
    alignment: Alignment,
): Rect {
    val sourceAspect = width.toFloat() / height.toFloat()
    val targetAspect = widthPx.toFloat() / heightPx.toFloat()
    val cropWidth: Int
    val cropHeight: Int
    if (sourceAspect > targetAspect) {
        cropWidth = (height * targetAspect).roundToInt().coerceIn(1, width)
        cropHeight = height
    } else {
        cropWidth = width
        cropHeight = (width / targetAspect).roundToInt().coerceIn(1, height)
    }

    val offset = alignment.align(
        size = IntSize(cropWidth, cropHeight),
        space = IntSize(width, height),
        layoutDirection = LayoutDirection.Ltr,
    )
    val left = offset.x.coerceIn(0, width - cropWidth).toFloat()
    val top = offset.y.coerceIn(0, height - cropHeight).toFloat()
    return Rect.makeLTRB(left, top, left + cropWidth, top + cropHeight)
}

private fun Bitmap.nuvioScaleToFillBitmapWithJvmLanczos(
    widthPx: Int,
    heightPx: Int,
    sourceRect: Rect,
): Bitmap? {
    if (!shouldUseJvmLanczosResize(widthPx, heightPx)) return null

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
        .nuvioLanczosResize(widthPx, heightPx)
        .nuvioToSkiaBitmap()
}

private fun Bitmap.nuvioScaleToBitmapWithJvmLanczos(
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    if (!shouldUseJvmLanczosResize(widthPx, heightPx)) return null
    return nuvioToBufferedImage()
        ?.nuvioLanczosResize(widthPx, heightPx)
        ?.nuvioToSkiaBitmap()
}

private fun shouldUseJvmLanczosResize(
    widthPx: Int,
    heightPx: Int,
): Boolean =
    widthPx <= MaxJvmLanczosTargetDimensionPx &&
        heightPx <= MaxJvmLanczosTargetDimensionPx &&
        widthPx.toLong() * heightPx.toLong() <= MaxJvmLanczosTargetPixels

private fun Bitmap.nuvioToBufferedImage(): BufferedImage? {
    if (width <= 0 || height <= 0) return null
    val imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val rowBytes = width * 4
    val bytes = readPixels(imageInfo, rowBytes, 0, 0) ?: return null
    val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val pixels = output.nuvioArgbPixels() ?: return null

    var sourceIndex = 0
    for (pixelIndex in pixels.indices) {
        val red = bytes[sourceIndex++].toInt() and 0xFF
        val green = bytes[sourceIndex++].toInt() and 0xFF
        val blue = bytes[sourceIndex++].toInt() and 0xFF
        val alpha = bytes[sourceIndex++].toInt() and 0xFF
        pixels[pixelIndex] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
    return output
}

private fun BufferedImage.nuvioLanczosResize(
    widthPx: Int,
    heightPx: Int,
): BufferedImage {
    if (width == widthPx && height == heightPx) return this

    val resample = ResampleOp(widthPx, heightPx)
    resample.setFilter(ResampleFilters.getLanczos3Filter())
    return resample.filter(nuvioToArgbImage(), null).nuvioToArgbImage()
}

private fun BufferedImage.nuvioToSkiaBitmap(): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val source = nuvioToArgbImage()
    val pixels = source.nuvioArgbPixels() ?: return null
    val bytes = ByteArray(source.width * source.height * 4)

    var targetIndex = 0
    for (argb in pixels) {
        bytes[targetIndex++] = ((argb ushr 16) and 0xFF).toByte()
        bytes[targetIndex++] = ((argb ushr 8) and 0xFF).toByte()
        bytes[targetIndex++] = (argb and 0xFF).toByte()
        bytes[targetIndex++] = ((argb ushr 24) and 0xFF).toByte()
    }

    val bitmap = Bitmap()
    val imageInfo = ImageInfo(source.width, source.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    return if (bitmap.installPixels(imageInfo, bytes, source.width * 4)) bitmap else null
}

private fun BufferedImage.nuvioToArgbImage(): BufferedImage {
    val pixels = (raster.dataBuffer as? DataBufferInt)?.data
    if (type == BufferedImage.TYPE_INT_ARGB && pixels?.size == width * height) return this

    val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = output.createGraphics()
    try {
        graphics.drawImage(this, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    return output
}

private fun BufferedImage.nuvioArgbPixels(): IntArray? =
    (raster.dataBuffer as? DataBufferInt)?.data

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
