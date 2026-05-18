package com.nuvio.app.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.BitmapImage
import coil3.compose.AsyncImagePainter
import kotlin.math.roundToInt
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode

internal actual fun AsyncImagePainter.State.withNuvioImagePainterWorkaround(
    filterQuality: FilterQuality,
): AsyncImagePainter.State {
    if (this !is AsyncImagePainter.State.Success) return this
    val bitmapImage = result.image as? BitmapImage ?: return this
    return copy(painter = DesktopScaledBitmapPainter(bitmapImage.bitmap))
}

private class DesktopScaledBitmapPainter(
    private val bitmap: Bitmap,
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
            filterQuality = FilterQuality.None,
        )
    }

    private fun imageFor(destinationSize: IntSize): ImageBitmap {
        cachedImage?.takeIf { cachedSize == destinationSize }?.let { return it }

        val image = if (destinationSize.width == bitmap.width && destinationSize.height == bitmap.height) {
            bitmap.asComposeImageBitmap()
        } else {
            bitmap.scaleToImageBitmap(destinationSize)
        }

        cachedSize = destinationSize
        cachedImage = image
        return image
    }
}

private fun Bitmap.scaleToImageBitmap(size: IntSize): ImageBitmap {
    val output = Bitmap()
    output.allocN32Pixels(size.width, size.height)
    val pixels = output.peekPixels() ?: return asComposeImageBitmap()
    val image = Image.makeFromBitmap(this)
    val scaled = image.scalePixels(
        dst = pixels,
        samplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
        cache = false,
    )
    return if (scaled) {
        Image.makeFromBitmap(output).toComposeImageBitmap()
    } else {
        asComposeImageBitmap()
    }
}
