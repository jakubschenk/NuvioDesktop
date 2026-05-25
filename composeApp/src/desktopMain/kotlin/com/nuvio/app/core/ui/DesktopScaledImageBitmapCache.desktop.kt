package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import java.util.LinkedHashMap
import org.jetbrains.skia.Bitmap

internal object DesktopScaledImageBitmapCache {
    private const val MaxEntries = 96
    private const val MaxBytes = 96L * 1024L * 1024L

    private var totalBytes = 0L
    private val map = LinkedHashMap<DesktopScaledImageBitmapCacheKey, CacheEntry>(128, 0.75f, true)

    @Synchronized
    fun getOrPut(
        sourceKey: String,
        bitmap: Bitmap,
        destinationSize: IntSize,
        create: () -> ImageBitmap,
    ): ImageBitmap {
        val key = DesktopScaledImageBitmapCacheKey(
            sourceKey = sourceKey,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            width = destinationSize.width,
            height = destinationSize.height,
            samplingMode = nuvioDesktopImageSamplingCacheKey,
        )
        map[key]?.let { return it.image }

        val image = create()
        val bytes = image.width.toLong() * image.height.toLong() * 4L
        if (bytes <= MaxBytes) {
            map[key] = CacheEntry(image = image, bytes = bytes)
            totalBytes += bytes
            trim()
        }
        return image
    }

    private fun trim() {
        while ((map.size > MaxEntries || totalBytes > MaxBytes) && map.isNotEmpty()) {
            val eldest = map.entries.first()
            totalBytes -= eldest.value.bytes
            map.remove(eldest.key)
        }
    }

    private data class CacheEntry(
        val image: ImageBitmap,
        val bytes: Long,
    )
}

private data class DesktopScaledImageBitmapCacheKey(
    val sourceKey: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val width: Int,
    val height: Int,
    val samplingMode: String,
)
