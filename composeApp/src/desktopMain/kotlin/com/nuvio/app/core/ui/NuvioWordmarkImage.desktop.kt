package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.toComposeImageBitmap

@Composable
internal actual fun PlatformNuvioWordmarkImage(
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val sourceBitmap by produceState<Bitmap?>(initialValue = null) {
        value = loadNuvioWordmarkBitmap()
    }
    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    val measuredModifier = modifier.onSizeChanged { size ->
        if (targetSize != size) {
            targetSize = size
        }
    }
    val wordmark = remember(sourceBitmap, targetSize, contentScale) {
        sourceBitmap?.toWordmarkImageBitmap(
            targetSize = targetSize,
            contentScale = contentScale,
        )
    }

    if (wordmark == null) {
        Box(modifier = measuredModifier)
        return
    }

    Image(
        bitmap = wordmark,
        contentDescription = contentDescription,
        modifier = measuredModifier,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
    )
}

private suspend fun loadNuvioWordmarkBitmap(): Bitmap? =
    withContext(Dispatchers.Default) {
        runCatching {
            val bytes = Res.readBytes("drawable/app_logo_wordmark.png")
            val image = SkiaImage.makeFromEncoded(bytes)
            try {
                val bitmap = Bitmap()
                if (!bitmap.allocN32Pixels(image.width, image.height)) {
                    bitmap.close()
                    return@withContext null
                }
                if (image.readPixels(bitmap)) {
                    bitmap
                } else {
                    bitmap.close()
                    null
                }
            } finally {
                image.close()
            }
        }.onFailure { error ->
            nuvioImageDebug("wordmark decode failed: ${error.message}")
        }.getOrNull()
    }

private fun Bitmap.toWordmarkImageBitmap(
    targetSize: IntSize,
    contentScale: ContentScale,
): ImageBitmap? {
    if (targetSize.width <= 0 || targetSize.height <= 0) return null
    val scaledBitmap = when (contentScale) {
        ContentScale.Crop -> nuvioScaleToFillBitmap(
            widthPx = targetSize.width,
            heightPx = targetSize.height,
            alignment = Alignment.Center,
        )
        ContentScale.FillBounds -> nuvioScaleToBitmap(
            widthPx = targetSize.width,
            heightPx = targetSize.height,
        )
        else -> nuvioScaleToFitBitmap(
            widthPx = targetSize.width,
            heightPx = targetSize.height,
            alignment = Alignment.Center,
        )
    } ?: return null

    nuvioImageDebug(
        "wordmark ${width}x$height -> ${targetSize.width}x${targetSize.height} " +
            "sampling=${nuvioDesktopImageSamplingCacheKey}",
    )
    return SkiaImage.makeFromBitmap(scaledBitmap).toComposeImageBitmap()
}
