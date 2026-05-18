package com.nuvio.app.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.BitmapImage
import coil3.compose.AsyncImagePainter
import kotlin.math.roundToInt
import org.jetbrains.skia.Bitmap

internal actual fun AsyncImagePainter.State.withNuvioImagePainterWorkaround(
    filterQuality: FilterQuality,
): AsyncImagePainter.State {
    if (this !is AsyncImagePainter.State.Success) return this
    val bitmapImage = result.image as? BitmapImage ?: return this
    return copy(painter = DesktopScaledBitmapPainter(bitmapImage.bitmap, filterQuality))
}

private class DesktopScaledBitmapPainter(
    private val bitmap: Bitmap,
    private val filterQuality: FilterQuality,
) : Painter() {
    override val intrinsicSize: Size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

    private var cachedSize: IntSize? = null
    private var cachedImage: ImageBitmap? = null

    override fun DrawScope.onDraw() {
        val destinationSize = IntSize(
            width = size.width.roundToInt().coerceAtLeast(1),
            height = size.height.roundToInt().coerceAtLeast(1),
        )
        val image = imageFor(destinationSize)
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = destinationSize,
            filterQuality = filterQuality,
        )
    }

    private fun imageFor(destinationSize: IntSize): ImageBitmap {
        cachedImage?.takeIf { cachedSize == destinationSize }?.let { return it }

        val image = bitmap.nuvioScaleToImageBitmap(destinationSize)

        cachedSize = destinationSize
        cachedImage = image
        return image
    }
}
