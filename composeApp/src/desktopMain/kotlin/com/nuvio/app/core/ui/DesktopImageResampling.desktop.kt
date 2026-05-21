package com.nuvio.app.core.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.mortennobel.imagescaling.ResampleFilters
import com.mortennobel.imagescaling.ResampleFilter
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
import org.jetbrains.skia.SamplingMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private const val SourceRectEpsilon = 0.5f
private const val MaxJvmResizeTargetPixels = 5_500_000
private const val MaxJvmResizeTargetDimensionPx = 4096
private const val PiFloat = Math.PI.toFloat()
private const val FilterEpsilon = 1.0e-6f

internal enum class NuvioDesktopImageSamplingMode(
    val cacheKey: String,
    private val jvmFilter: ResampleFilter?,
    val scaleSampling: SamplingMode,
    val cropSampling: SamplingMode,
) {
    Lanczos3(
        cacheKey = "lanczos3",
        jvmFilter = ResampleFilters.getLanczos3Filter(),
        scaleSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
        cropSampling = CubicResampler(1f / 3f, 1f / 3f),
    ),
    Chrome(
        cacheKey = "chrome",
        jvmFilter = null,
        scaleSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NEAREST),
        cropSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NEAREST),
    ),
    SkiaLinearNearestMip(
        cacheKey = "skia_linear_nearest_mip",
        jvmFilter = null,
        scaleSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NEAREST),
        cropSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NEAREST),
    ),
    SkiaLinearLinearMip(
        cacheKey = "skia_linear_linear_mip",
        jvmFilter = null,
        scaleSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
        cropSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
    ),
    SkiaCatmullRom(
        cacheKey = "skia_catmull_rom",
        jvmFilter = null,
        scaleSampling = CubicResampler(0f, 0.5f),
        cropSampling = CubicResampler(0f, 0.5f),
    ),
    SkiaMitchell(
        cacheKey = "skia_mitchell",
        jvmFilter = null,
        scaleSampling = CubicResampler(1f / 3f, 1f / 3f),
        cropSampling = CubicResampler(1f / 3f, 1f / 3f),
    ),
    JvmMitchell(
        cacheKey = "jvm_mitchell",
        jvmFilter = ResampleFilters.getMitchellFilter(),
        scaleSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
        cropSampling = CubicResampler(1f / 3f, 1f / 3f),
    ),
    JvmTriangle(
        cacheKey = "jvm_triangle",
        jvmFilter = ResampleFilters.getTriangleFilter(),
        scaleSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
        cropSampling = CubicResampler(1f / 3f, 1f / 3f),
    ),
    JvmHamming(
        cacheKey = "jvm_hamming",
        jvmFilter = ChromiumHamming1Filter,
        scaleSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
        cropSampling = CubicResampler(1f / 3f, 1f / 3f),
    );

    val usesJvmResize: Boolean
        get() = jvmFilter != null

    fun jvmFilterOrNull(): ResampleFilter? = jvmFilter
}

internal val nuvioDesktopImageSamplingMode: NuvioDesktopImageSamplingMode by lazy {
    val raw = sequenceOf(
        System.getProperty("nuvio.image.sampling"),
        System.getProperty("nuvio.image.resampler"),
        System.getenv("NUVIO_IMAGE_SAMPLING"),
        System.getenv("NUVIO_IMAGE_RESAMPLER"),
    ).firstOrNull { !it.isNullOrBlank() }

    when (raw?.trim()?.lowercase()?.replace('-', '_')) {
        null, "", "default", "current", "lanczos", "lanczos3" -> NuvioDesktopImageSamplingMode.Lanczos3
        "chrome", "chromium", "browser" -> NuvioDesktopImageSamplingMode.Chrome
        "skia_linear_nearest_mip", "linear_nearest_mip", "medium" -> NuvioDesktopImageSamplingMode.SkiaLinearNearestMip
        "skia_linear_linear_mip", "linear_linear_mip" -> NuvioDesktopImageSamplingMode.SkiaLinearLinearMip
        "skia_catmull_rom", "catmull_rom", "catmullrom" -> NuvioDesktopImageSamplingMode.SkiaCatmullRom
        "skia_mitchell", "mitchell" -> NuvioDesktopImageSamplingMode.SkiaMitchell
        "jvm_mitchell" -> NuvioDesktopImageSamplingMode.JvmMitchell
        "jvm_triangle", "triangle" -> NuvioDesktopImageSamplingMode.JvmTriangle
        "jvm_hamming", "hamming", "chromium_hamming", "hamming1" -> NuvioDesktopImageSamplingMode.JvmHamming
        else -> {
            println("NuvioImage: unknown sampling mode '$raw', falling back to lanczos3")
            NuvioDesktopImageSamplingMode.Lanczos3
        }
    }.also { mode ->
        nuvioImageDebug("sampling mode ${mode.cacheKey}")
    }
}

internal val nuvioDesktopImageSamplingCacheKey: String
    get() = nuvioDesktopImageSamplingMode.cacheKey

private object ChromiumHamming1Filter : ResampleFilter {
    override fun getSamplingRadius(): Float = 1f

    override fun apply(value: Float): Float {
        if (value <= -1f || value >= 1f) return 0f
        if (abs(value) < FilterEpsilon) return 1f
        val xpi = value * PiFloat
        return (sin(xpi) / xpi) * (0.54f + 0.46f * cos(xpi))
    }

    override fun getName(): String = "Chromium Hamming1"
}

private val NuvioImageDebugLogging: Boolean by lazy {
    System.getProperty("nuvio.image.debug").equals("true", ignoreCase = true) ||
        System.getenv("NUVIO_IMAGE_DEBUG").equals("1", ignoreCase = true) ||
        System.getenv("NUVIO_IMAGE_DEBUG").equals("true", ignoreCase = true)
}

internal fun Bitmap.nuvioScaleToBitmap(
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    if (width == widthPx && height == heightPx) return this

    nuvioScaleToBitmapWithJvmFilter(widthPx, heightPx)?.let { return it }
    nuvioImageDebug("Skia ${nuvioDesktopImageSamplingCacheKey} fit ${width}x$height -> ${widthPx}x$heightPx")
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

    nuvioScaleToFillBitmapWithJvmFilter(
        widthPx = widthPx,
        heightPx = heightPx,
        sourceRect = sourceRect,
    )?.let { return it }
    nuvioImageDebug("Skia ${nuvioDesktopImageSamplingCacheKey} fill ${width}x$height -> ${widthPx}x$heightPx")

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

internal fun Bitmap.nuvioScaleToFitBitmap(
    widthPx: Int,
    heightPx: Int,
    alignment: Alignment,
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    if (width <= 0 || height <= 0) return null
    if (width == widthPx && height == heightPx) return this

    val targetScale = minOf(
        widthPx.toFloat() / width.toFloat(),
        heightPx.toFloat() / height.toFloat(),
    )
    val scaledWidth = (width * targetScale).roundToInt().coerceIn(1, widthPx)
    val scaledHeight = (height * targetScale).roundToInt().coerceIn(1, heightPx)
    if (scaledWidth == widthPx && scaledHeight == heightPx) {
        return nuvioScaleToBitmap(widthPx, heightPx)
    }

    val scaled = nuvioScaleToBitmap(scaledWidth, scaledHeight) ?: return null
    val output = Bitmap()
    output.allocN32Pixels(widthPx, heightPx)
    if (output.peekPixels() == null) {
        if (scaled !== this) scaled.close()
        return null
    }

    val offset = alignment.align(
        size = IntSize(scaledWidth, scaledHeight),
        space = IntSize(widthPx, heightPx),
        layoutDirection = LayoutDirection.Ltr,
    )
    val canvas = Canvas(output)
    val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
    }
    val image = Image.makeFromBitmap(scaled)
    return try {
        output.erase(0x00000000)
        canvas.drawImageRect(
            image = image,
            src = Rect.makeWH(scaledWidth.toFloat(), scaledHeight.toFloat()),
            dst = Rect.makeXYWH(
                offset.x.toFloat(),
                offset.y.toFloat(),
                scaledWidth.toFloat(),
                scaledHeight.toFloat(),
            ),
            samplingMode = nuvioDesktopImageSamplingMode.scaleSampling,
            paint = paint,
            strict = true,
        )
        output
    } finally {
        image.close()
        paint.close()
        canvas.close()
        if (scaled !== this) scaled.close()
    }
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

private fun Bitmap.nuvioScaleToFillBitmapWithJvmFilter(
    widthPx: Int,
    heightPx: Int,
    sourceRect: Rect,
): Bitmap? {
    val filter = nuvioDesktopImageSamplingMode.jvmFilterOrNull() ?: return null
    if (!shouldUseJvmResize(widthPx, heightPx)) return null

    val source = nuvioToBufferedImage() ?: return null
    val cropX = sourceRect.left.roundToInt().coerceIn(0, source.width - 1)
    val cropY = sourceRect.top.roundToInt().coerceIn(0, source.height - 1)
    val cropRight = sourceRect.right.roundToInt().coerceIn(cropX + 1, source.width)
    val cropBottom = sourceRect.bottom.roundToInt().coerceIn(cropY + 1, source.height)
    val cropWidth = cropRight - cropX
    val cropHeight = cropBottom - cropY
    if (cropWidth <= 0 || cropHeight <= 0) return null

    return runCatching {
        val cropped = source.getSubimage(cropX, cropY, cropWidth, cropHeight)
        cropped
            .nuvioResize(widthPx, heightPx, filter)
            .nuvioToSkiaBitmap()
            ?.also {
                nuvioImageDebug(
                    "${nuvioDesktopImageSamplingCacheKey} fill ${width}x$height " +
                        "crop ${cropWidth}x$cropHeight@$cropX,$cropY -> ${widthPx}x$heightPx",
                )
            }
    }.onFailure { error ->
        nuvioImageDebug(
            "${nuvioDesktopImageSamplingCacheKey} fill failed ${width}x$height -> " +
                "${widthPx}x$heightPx: ${error.message}",
        )
    }.getOrNull()
}

private fun Bitmap.nuvioScaleToBitmapWithJvmFilter(
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    val filter = nuvioDesktopImageSamplingMode.jvmFilterOrNull() ?: return null
    if (!shouldUseJvmResize(widthPx, heightPx)) return null
    return runCatching {
        nuvioToBufferedImage()
            ?.nuvioResize(widthPx, heightPx, filter)
            ?.nuvioToSkiaBitmap()
            ?.also {
                nuvioImageDebug("${nuvioDesktopImageSamplingCacheKey} fit ${width}x$height -> ${widthPx}x$heightPx")
            }
    }.onFailure { error ->
        nuvioImageDebug(
            "${nuvioDesktopImageSamplingCacheKey} fit failed ${width}x$height -> " +
                "${widthPx}x$heightPx: ${error.message}",
        )
    }.getOrNull()
}

private fun shouldUseJvmResize(
    widthPx: Int,
    heightPx: Int,
): Boolean =
    widthPx <= MaxJvmResizeTargetDimensionPx &&
        heightPx <= MaxJvmResizeTargetDimensionPx &&
        widthPx.toLong() * heightPx.toLong() <= MaxJvmResizeTargetPixels

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

private fun BufferedImage.nuvioResize(
    widthPx: Int,
    heightPx: Int,
    filter: ResampleFilter,
): BufferedImage {
    if (width == widthPx && height == heightPx) return this

    val resample = ResampleOp(widthPx, heightPx)
    resample.setFilter(filter)
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

    val imageInfo = ImageInfo(source.width, source.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val image = Image.makeRaster(imageInfo, bytes, source.width * 4)
    val bitmap = Bitmap()
    return try {
        if (!bitmap.allocN32Pixels(source.width, source.height)) return null
        if (image.readPixels(bitmap)) bitmap else null
    } finally {
        image.close()
    }
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
            samplingMode = nuvioDesktopImageSamplingMode.cropSampling,
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
    if (!output.allocN32Pixels(widthPx, heightPx)) return null
    val image = Image.makeFromBitmap(this)
    val canvas = Canvas(output)
    val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
    }
    return try {
        output.erase(0x00000000)
        canvas.drawImageRect(
            image = image,
            src = Rect.makeWH(width.toFloat(), height.toFloat()),
            dst = Rect.makeWH(widthPx.toFloat(), heightPx.toFloat()),
            samplingMode = nuvioDesktopImageSamplingMode.scaleSampling,
            paint = paint,
            strict = true,
        )
        output
    } finally {
        paint.close()
        canvas.close()
        image.close()
    }
}

private fun Rect.isWholeBitmap(widthPx: Int, heightPx: Int): Boolean =
    abs(left) < SourceRectEpsilon &&
        abs(top) < SourceRectEpsilon &&
        abs(right - widthPx) < SourceRectEpsilon &&
        abs(bottom - heightPx) < SourceRectEpsilon

internal fun nuvioImageDebug(message: String) {
    if (NuvioImageDebugLogging) {
        println("NuvioImage: $message")
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
