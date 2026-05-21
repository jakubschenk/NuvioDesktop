package com.nuvio.app.core.ui

import androidx.compose.ui.Alignment
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Scale
import coil3.size.Size
import coil3.transform.Transformation
import org.jetbrains.skia.Bitmap

internal actual fun ImageRequest.Builder.nuvioPrescaleToDrawSize(
    widthPx: Int,
    heightPx: Int,
    scale: Scale,
    alignment: Alignment,
): ImageRequest.Builder {
    if (widthPx <= 0 || heightPx <= 0) return this
    return transformations(NuvioDesktopPrescaleTransformation(widthPx, heightPx, scale, alignment))
}

private class NuvioDesktopPrescaleTransformation(
    private val widthPx: Int,
    private val heightPx: Int,
    private val scale: Scale,
    private val alignment: Alignment,
) : Transformation() {
    override val cacheKey: String =
        "nuvio_desktop_prescale_v9:${nuvioDesktopImageSamplingCacheKey}:$widthPx:$heightPx:" +
            "${scale.name}:${alignment.nuvioDesktopAlignmentCacheKey()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return when (scale) {
            Scale.FILL -> input.nuvioScaleToFillBitmap(widthPx, heightPx, alignment)
            Scale.FIT -> input.nuvioScaleToFitBitmap(widthPx, heightPx, alignment)
        } ?: input
    }
}

private fun Alignment.nuvioDesktopAlignmentCacheKey(): String =
    when (this) {
        Alignment.TopStart -> "top_start"
        Alignment.TopCenter -> "top_center"
        Alignment.TopEnd -> "top_end"
        Alignment.CenterStart -> "center_start"
        Alignment.Center -> "center"
        Alignment.CenterEnd -> "center_end"
        Alignment.BottomStart -> "bottom_start"
        Alignment.BottomCenter -> "bottom_center"
        Alignment.BottomEnd -> "bottom_end"
        else -> toString()
    }
