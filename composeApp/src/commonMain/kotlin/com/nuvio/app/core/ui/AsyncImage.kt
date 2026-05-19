package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage as CoilAsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import kotlin.math.roundToInt

internal expect val nuvioImageFilterQuality: FilterQuality

@Composable
internal fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1.0f,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = nuvioImageFilterQuality,
    clipToBounds: Boolean = true,
) {
    val shouldMeasureStringModel = model is String
    var drawnSize by remember { mutableStateOf(IntSize.Zero) }
    val resolvedModel = rememberSizedAsyncImageModel(
        model = model,
        drawnSize = drawnSize,
        contentScale = contentScale,
    )
    val useFallbackPainter = resolvedModel == null
    val transform = remember(placeholder, error, fallback, filterQuality, useFallbackPainter) {
        { state: AsyncImagePainter.State ->
            state.withFallbackPainters(
                placeholder = placeholder,
                error = error,
                fallback = fallback,
                useFallbackPainter = useFallbackPainter,
            ).withNuvioImagePainterWorkaround(filterQuality)
        }
    }
    val onState = remember(onLoading, onSuccess, onError, useFallbackPainter) {
        { state: AsyncImagePainter.State ->
            when (state) {
                is AsyncImagePainter.State.Loading -> {
                    onLoading?.invoke(state)
                    Unit
                }
                is AsyncImagePainter.State.Success -> {
                    onSuccess?.invoke(state)
                    Unit
                }
                is AsyncImagePainter.State.Error -> {
                    if (!useFallbackPainter) {
                        onError?.invoke(state)
                    }
                    Unit
                }
                AsyncImagePainter.State.Empty -> Unit
            }
        }
    }

    CoilAsyncImage(
        model = resolvedModel,
        contentDescription = contentDescription,
        modifier = if (shouldMeasureStringModel) {
            modifier.onSizeChanged { size ->
                if (drawnSize != size) {
                    drawnSize = size
                }
            }
        } else {
            modifier
        },
        transform = transform,
        onState = onState,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
        clipToBounds = clipToBounds,
    )
}

@Composable
private fun rememberSizedAsyncImageModel(
    model: Any?,
    drawnSize: IntSize,
    contentScale: ContentScale,
): Any? {
    val platformContext = LocalPlatformContext.current
    val decodeSizeMultiplier = nuvioImageDecodeSizeMultiplier.coerceAtLeast(1f)
    val upgradedModel = remember(model) { model.upgradeTmdbImageModelQuality() }
    return remember(upgradedModel, drawnSize, contentScale, platformContext, decodeSizeMultiplier) {
        val url = upgradedModel as? String ?: return@remember upgradedModel
        if (url.isBlank()) return@remember null
        val widthPx = drawnSize.width.coerceAtLeast(1)
        val heightPx = drawnSize.height.coerceAtLeast(1)
        if (drawnSize == IntSize.Zero) return@remember null

        val requestWidthPx = (widthPx * decodeSizeMultiplier).roundToInt().coerceAtLeast(widthPx)
        val requestHeightPx = (heightPx * decodeSizeMultiplier).roundToInt().coerceAtLeast(heightPx)
        val coilScale = contentScale.toCoilScale()
        ImageRequest.Builder(platformContext)
            .data(url)
            .size(Size(requestWidthPx, requestHeightPx))
            .scale(coilScale)
            .precision(Precision.EXACT)
            .nuvioPrescaleToDrawSize(widthPx, heightPx, coilScale)
            .build()
    }
}

private fun ContentScale.toCoilScale(): Scale =
    if (
        this == ContentScale.Crop ||
        this == ContentScale.FillBounds ||
        this == ContentScale.FillHeight ||
        this == ContentScale.FillWidth
    ) {
        Scale.FILL
    } else {
        Scale.FIT
    }

private fun AsyncImagePainter.State.withFallbackPainters(
    placeholder: Painter?,
    error: Painter?,
    fallback: Painter?,
    useFallbackPainter: Boolean,
): AsyncImagePainter.State = when (this) {
    is AsyncImagePainter.State.Loading -> if (placeholder != null) copy(painter = placeholder) else this
    is AsyncImagePainter.State.Error -> {
        val errorPainter = if (useFallbackPainter) fallback else error
        if (errorPainter != null) copy(painter = errorPainter) else this
    }
    else -> this
}
