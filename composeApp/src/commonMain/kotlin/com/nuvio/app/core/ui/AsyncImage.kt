package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage as CoilAsyncImage
import coil3.compose.AsyncImagePainter

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
    val resolvedModel = remember(model) { model.upgradeTmdbImageModelQuality() }
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
    val onState = remember(onLoading, onSuccess, onError) {
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
                    onError?.invoke(state)
                    Unit
                }
                AsyncImagePainter.State.Empty -> Unit
            }
        }
    }

    CoilAsyncImage(
        model = resolvedModel,
        contentDescription = contentDescription,
        modifier = modifier,
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
