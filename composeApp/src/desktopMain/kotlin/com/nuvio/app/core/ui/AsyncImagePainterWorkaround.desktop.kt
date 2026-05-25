package com.nuvio.app.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.BitmapImage
import coil3.compose.AsyncImagePainter
import kotlin.math.roundToInt
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint as SkiaPaint
import org.jetbrains.skia.Rect

internal actual fun AsyncImagePainter.State.withNuvioImagePainterWorkaround(
    filterQuality: FilterQuality,
    preferDirectDraw: Boolean,
): AsyncImagePainter.State {
    if (this !is AsyncImagePainter.State.Success) return this
    val bitmapImage = result.image as? BitmapImage ?: return this
    return copy(
        painter = DesktopScaledBitmapPainter(
            bitmap = bitmapImage.bitmap,
            sourceKey = result.request.stableDesktopImageCacheKey(),
            filterQuality = filterQuality,
            preferDirectDraw = preferDirectDraw,
        ),
    )
}

private class DesktopScaledBitmapPainter(
    private val bitmap: Bitmap,
    private val sourceKey: String,
    private val filterQuality: FilterQuality,
    private val preferDirectDraw: Boolean,
) : Painter() {
    override val intrinsicSize: Size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

    private var cachedSize: IntSize? = null
    private var cachedImage: ImageBitmap? = null
    private var lastDrawLogKey: String? = null

    override fun DrawScope.onDraw() {
        if (preferDirectDraw && !nuvioDesktopImageSamplingMode.usesJvmResize) {
            drawBitmapDirectly(size)
            return
        }

        val destinationSize = IntSize(
            width = size.width.roundToInt().coerceAtLeast(1),
            height = size.height.roundToInt().coerceAtLeast(1),
        )

        val image = imageFor(destinationSize)
        logScaledDraw(destinationSize)
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = destinationSize,
            filterQuality = filterQuality,
        )
    }

    private fun DrawScope.drawBitmapDirectly(destinationSize: Size) {
        val width = destinationSize.width.coerceAtLeast(1f)
        val height = destinationSize.height.coerceAtLeast(1f)
        logDirectDraw(width, height)

        val image = Image.makeFromBitmap(bitmap)
        val paint = SkiaPaint().apply {
            isAntiAlias = true
            isDither = true
        }
        try {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawImageRect(
                    image = image,
                    src = Rect.makeWH(bitmap.width.toFloat(), bitmap.height.toFloat()),
                    dst = Rect.makeWH(width, height),
                    samplingMode = nuvioDesktopImageSamplingMode.scaleSampling,
                    paint = paint,
                    strict = true,
                )
            }
        } finally {
            paint.close()
            image.close()
        }
    }

    private fun logDirectDraw(
        width: Float,
        height: Float,
    ) {
        val roundedWidth = width.roundToInt()
        val roundedHeight = height.roundToInt()
        val key = "direct:${bitmap.width}x${bitmap.height}:$roundedWidth:$roundedHeight"
        if (lastDrawLogKey == key) return
        lastDrawLogKey = key
        nuvioImageDebug(
            "direct draw ${bitmap.width}x${bitmap.height} -> " +
                "${"%.1f".format(width)}x${"%.1f".format(height)} " +
                "sampling=${nuvioDesktopImageSamplingCacheKey}",
        )
    }

    private fun logScaledDraw(destinationSize: IntSize) {
        val key = "scaled:${bitmap.width}x${bitmap.height}:${destinationSize.width}:${destinationSize.height}"
        if (lastDrawLogKey == key) return
        lastDrawLogKey = key
        nuvioImageDebug(
            "software draw ${bitmap.width}x${bitmap.height} -> " +
                "${destinationSize.width}x${destinationSize.height} " +
                "sampling=${nuvioDesktopImageSamplingCacheKey}",
        )
    }

    private fun imageFor(destinationSize: IntSize): ImageBitmap {
        cachedImage?.takeIf { cachedSize == destinationSize }?.let { return it }

        val image = DesktopScaledImageBitmapCache.getOrPut(sourceKey, bitmap, destinationSize) {
            bitmap.nuvioScaleToImageBitmap(destinationSize)
        }

        cachedSize = destinationSize
        cachedImage = image
        return image
    }
}

private fun coil3.request.ImageRequest.stableDesktopImageCacheKey(): String =
    memoryCacheKey
        ?: diskCacheKey
        ?: data?.toString()
        ?: "bitmap:${System.identityHashCode(this)}"
