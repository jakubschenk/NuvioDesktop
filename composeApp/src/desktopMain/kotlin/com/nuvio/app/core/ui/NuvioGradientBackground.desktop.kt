package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.roundToInt

private const val DesktopGradientMaxRasterPixels = 18_000_000
private const val DesktopGradientDefaultSupersample = 2
private const val DesktopGradientDitherAmplitude = 0.70f / 255f

internal actual fun Modifier.nuvioPlatformLinearGradientBackground(
    colorStops: Array<Pair<Float, Color>>,
    axis: NuvioLinearGradientAxis,
): Modifier {
    if (colorStops.isEmpty()) return this

    val stops = colorStops.map { (position, color) ->
        DesktopGradientStop(
            position = position.coerceIn(0f, 1f),
            red = color.red,
            green = color.green,
            blue = color.blue,
            alpha = color.alpha,
        )
    }
    val stopKeys = stops.map { it.toKey() }
    val ditherMode = desktopGradientDitherMode()
    val useRaster = desktopGradientRasterEnabled()

    return drawWithCache {
        val widthPx = size.width.roundToInt().coerceAtLeast(1)
        val heightPx = size.height.roundToInt().coerceAtLeast(1)
        val rasterScale = desktopGradientRasterScale(widthPx, heightPx)
        val rasterWidthPx = widthPx * rasterScale
        val rasterHeightPx = heightPx * rasterScale
        val rasterPixels = rasterWidthPx.toLong() * rasterHeightPx.toLong()
        val rasterImage = if (useRaster && rasterPixels <= DesktopGradientMaxRasterPixels) {
            DesktopGradientRasterCache.getOrPut(
                width = rasterWidthPx,
                height = rasterHeightPx,
                axis = axis,
                stops = stops,
                stopKeys = stopKeys,
                ditherMode = ditherMode,
            )
        } else {
            null
        }

        onDrawBehind {
            if (rasterImage != null) {
                drawImage(
                    image = rasterImage,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(rasterImage.width, rasterImage.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(widthPx, heightPx),
                    filterQuality = FilterQuality.High,
                )
            } else {
                drawSkiaLinearGradient(colorStops, axis)
            }
        }
    }
}

private object DesktopGradientRasterCache {
    private const val MaxEntries = 8
    private const val MaxBytes = 96L * 1024L * 1024L

    private val cache = LinkedHashMap<DesktopGradientRasterKey, DesktopGradientRasterEntry>(
        16,
        0.75f,
        true,
    )
    private var totalBytes = 0L

    @Synchronized
    fun getOrPut(
        width: Int,
        height: Int,
        axis: NuvioLinearGradientAxis,
        stops: List<DesktopGradientStop>,
        stopKeys: List<DesktopGradientStopKey>,
        ditherMode: DesktopGradientDitherMode,
    ): ImageBitmap {
        val key = DesktopGradientRasterKey(
            width = width,
            height = height,
            axis = axis,
            stops = stopKeys,
            ditherMode = ditherMode,
        )
        cache[key]?.let { return it.image }

        val image = createGradientImage(
            width = width,
            height = height,
            axis = axis,
            stops = stops,
            ditherMode = ditherMode,
        )
        val bytes = width.toLong() * height.toLong() * 4L
        cache[key] = DesktopGradientRasterEntry(image = image, bytes = bytes)
        totalBytes += bytes
        trim()
        return image
    }

    private fun trim() {
        while ((cache.size > MaxEntries || totalBytes > MaxBytes) && cache.isNotEmpty()) {
            val eldest = cache.entries.first()
            totalBytes -= eldest.value.bytes
            cache.remove(eldest.key)
        }
    }
}

private fun createGradientImage(
    width: Int,
    height: Int,
    axis: NuvioLinearGradientAxis,
    stops: List<DesktopGradientStop>,
    ditherMode: DesktopGradientDitherMode,
): ImageBitmap {
    val bytes = ByteArray(width * height * 4)
    var byteIndex = 0

    for (y in 0 until height) {
        for (x in 0 until width) {
            val t = gradientPosition(
                x = x,
                y = y,
                width = width,
                height = height,
                axis = axis,
            )
            val color = interpolateGradient(stops, t)
            val noise = when (ditherMode) {
                DesktopGradientDitherMode.Off -> 0f
                DesktopGradientDitherMode.Smooth -> smoothDitherNoise(x, y)
                DesktopGradientDitherMode.Legacy -> legacyDitherNoise(x, y, 11)
            }

            bytes[byteIndex++] = quantizeColor(color.red + noise)
            bytes[byteIndex++] = quantizeColor(color.green + noise)
            bytes[byteIndex++] = quantizeColor(color.blue + noise)
            bytes[byteIndex++] = quantizeColor(color.alpha)
        }
    }

    val imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val image = Image.makeRaster(imageInfo, bytes, width * 4)
    return image.toComposeImageBitmap()
}

private fun gradientPosition(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    axis: NuvioLinearGradientAxis,
): Float =
    when (axis) {
        NuvioLinearGradientAxis.Vertical -> if (height <= 1) 0f else y.toFloat() / (height - 1).toFloat()
        NuvioLinearGradientAxis.Horizontal -> if (width <= 1) 0f else x.toFloat() / (width - 1).toFloat()
        NuvioLinearGradientAxis.DiagonalDown -> {
            val denominator = (width - 1 + height - 1).coerceAtLeast(1).toFloat()
            (x + y).toFloat() / denominator
        }
    }

private fun interpolateGradient(
    stops: List<DesktopGradientStop>,
    position: Float,
): DesktopGradientStop {
    if (stops.size == 1) return stops.first()
    val t = position.coerceIn(0f, 1f)
    var endIndex = stops.indexOfFirst { stop -> stop.position >= t }
    if (endIndex <= 0) {
        endIndex = 1
    } else if (endIndex == -1) {
        endIndex = stops.lastIndex
    }
    val start = stops[endIndex - 1]
    val end = stops[endIndex]
    val span = (end.position - start.position).takeIf { it > 0f } ?: 1f
    val localT = ((t - start.position) / span).coerceIn(0f, 1f)
    return DesktopGradientStop(
        position = t,
        red = start.red + (end.red - start.red) * localT,
        green = start.green + (end.green - start.green) * localT,
        blue = start.blue + (end.blue - start.blue) * localT,
        alpha = start.alpha + (end.alpha - start.alpha) * localT,
    )
}

private fun smoothDitherNoise(
    x: Int,
    y: Int,
): Float {
    val a = hashNoise01(x, y, 0)
    val b = hashNoise01(x, y, 1)
    return (a - b) * DesktopGradientDitherAmplitude
}

private fun legacyDitherNoise(
    x: Int,
    y: Int,
    salt: Int,
): Float {
    val a = hashNoise01(x, y, salt)
    val b = hashNoise01(x, y, salt + 101)
    return ((a + b) * 0.5f - 0.5f) * DesktopGradientDitherAmplitude
}

private fun hashNoise01(
    x: Int,
    y: Int,
    salt: Int,
): Float {
    var z = x.toLong() * 521_288_629L +
        y.toLong() * 1_318_419_923L +
        salt.toLong() * 2_654_435_761L
    z += -7_046_029_254_386_353_131L
    z = (z xor (z ushr 30)) * -4_658_895_280_553_007_687L
    z = (z xor (z ushr 27)) * -7_723_592_293_110_705_685L
    z = z xor (z ushr 31)
    return ((z ushr 40) and 0xFF_FFFFL).toFloat() / 0xFF_FFFFL.toFloat()
}

private fun quantizeColor(value: Float): Byte =
    ((value.coerceIn(0f, 1f) * 255f) + 0.5f)
        .toInt()
        .coerceIn(0, 255)
        .toByte()

private fun DrawScope.drawSkiaLinearGradient(
    colorStops: Array<Pair<Float, Color>>,
    axis: NuvioLinearGradientAxis,
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    val colors = Array(colorStops.size) { index ->
        val color = colorStops[index].second
        Color4f(color.red, color.green, color.blue, color.alpha)
    }
    val positions = FloatArray(colorStops.size) { index -> colorStops[index].first }
    val shader = when (axis) {
        NuvioLinearGradientAxis.Vertical -> Shader.makeLinearGradient(
            0f,
            0f,
            0f,
            height,
            colors,
            ColorSpace.sRGB,
            positions,
            GradientStyle.DEFAULT,
        )
        NuvioLinearGradientAxis.Horizontal -> Shader.makeLinearGradient(
            0f,
            0f,
            width,
            0f,
            colors,
            ColorSpace.sRGB,
            positions,
            GradientStyle.DEFAULT,
        )
        NuvioLinearGradientAxis.DiagonalDown -> Shader.makeLinearGradient(
            0f,
            0f,
            width,
            height,
            colors,
            ColorSpace.sRGB,
            positions,
            GradientStyle.DEFAULT,
        )
    }
    val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        this.shader = shader
    }
    try {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(Rect.makeWH(width, height), paint)
        }
    } finally {
        paint.close()
        shader.close()
    }
}

private fun desktopGradientDitherMode(): DesktopGradientDitherMode {
    val configured = System.getenv("NUVIO_GRADIENT_DITHER")
        ?: System.getProperty("nuvio.gradient.dither")
        ?: return DesktopGradientDitherMode.Smooth
    return when (configured.trim().lowercase(Locale.US)) {
        "0", "false", "no", "off", "disabled" -> DesktopGradientDitherMode.Off
        "legacy", "hash" -> DesktopGradientDitherMode.Legacy
        else -> DesktopGradientDitherMode.Smooth
    }
}

private fun desktopGradientRasterScale(width: Int, height: Int): Int {
    val configured = System.getenv("NUVIO_GRADIENT_SUPERSAMPLE")
        ?: System.getProperty("nuvio.gradient.supersample")
    val requestedScale = configured
        ?.trim()
        ?.toIntOrNull()
        ?.coerceIn(1, 3)
        ?: DesktopGradientDefaultSupersample
    val basePixels = width.toLong() * height.toLong()
    var scale = requestedScale
    while (scale > 1 && basePixels * scale.toLong() * scale.toLong() > DesktopGradientMaxRasterPixels) {
        scale--
    }
    return scale.coerceAtLeast(1)
}

private fun desktopGradientRasterEnabled(): Boolean {
    val configured = System.getenv("NUVIO_GRADIENT_RENDERER")
        ?: System.getProperty("nuvio.gradient.renderer")
        ?: return true
    return when (configured.trim().lowercase(Locale.US)) {
        "shader", "gpu", "skia" -> false
        else -> true
    }
}

private data class DesktopGradientStop(
    val position: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
) {
    fun toKey(): DesktopGradientStopKey =
        DesktopGradientStopKey(
            position = (position.coerceIn(0f, 1f) * 10_000f).roundToInt(),
            red = (red.coerceIn(0f, 1f) * 255f).roundToInt(),
            green = (green.coerceIn(0f, 1f) * 255f).roundToInt(),
            blue = (blue.coerceIn(0f, 1f) * 255f).roundToInt(),
            alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt(),
        )
}

private data class DesktopGradientStopKey(
    val position: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int,
)

private data class DesktopGradientRasterKey(
    val width: Int,
    val height: Int,
    val axis: NuvioLinearGradientAxis,
    val stops: List<DesktopGradientStopKey>,
    val ditherMode: DesktopGradientDitherMode,
)

private data class DesktopGradientRasterEntry(
    val image: ImageBitmap,
    val bytes: Long,
)

private enum class DesktopGradientDitherMode {
    Off,
    Smooth,
    Legacy,
}
