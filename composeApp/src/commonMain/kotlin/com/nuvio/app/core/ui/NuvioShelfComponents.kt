package com.nuvio.app.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import kotlin.math.roundToInt
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_view_all
import nuvio.composeapp.generated.resources.poster_logo_content_description
import org.jetbrains.compose.resources.stringResource

enum class NuvioPosterShape {
    Poster,
    Square,
    Landscape,
}

enum class NuvioViewAllPillSize {
    Default,
    Compact,
}

@Composable
fun <T> NuvioShelfSection(
    title: String,
    entries: List<T>,
    modifier: Modifier = Modifier,
    headerHorizontalPadding: Dp = 0.dp,
    rowContentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 10.dp,
    showHeaderAccent: Boolean = true,
    onViewAllClick: (() -> Unit)? = null,
    viewAllPillSize: NuvioViewAllPillSize = NuvioViewAllPillSize.Default,
    scrollWithoutShift: Boolean = false,
    maxItems: Int? = null,
    key: ((T) -> Any)? = null,
    contentType: ((T) -> Any?)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val rowState = rememberLazyListState()
    val renderedEntries = maxItems?.let { entries.take(it.coerceAtLeast(0)) } ?: entries
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title.isNotBlank()) {
            NuvioShelfSectionHeader(
                title = title,
                modifier = Modifier.padding(horizontal = headerHorizontalPadding),
                showAccent = showHeaderAccent,
                onViewAllClick = onViewAllClick,
                viewAllPillSize = viewAllPillSize,
            )
        }
        LazyRow(
            state = rowState,
            modifier = Modifier
                .fillMaxWidth()
                .desktopHorizontalLazyRowGestures(rowState, scrollWithoutShift = scrollWithoutShift),
            contentPadding = rowContentPadding,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            if (key != null) {
                items(
                    items = renderedEntries.withDuplicateSafeLazyKeys(key),
                    key = { entry -> entry.lazyKey },
                    contentType = { entry -> contentType?.invoke(entry.value) },
                ) { keyedEntry ->
                    itemContent(keyedEntry.value)
                }
            } else {
                items(
                    items = renderedEntries,
                    contentType = { entry -> contentType?.invoke(entry) },
                ) { entry ->
                    itemContent(entry)
                }
            }
        }
    }
}

@Composable
fun NuvioPosterCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    shape: NuvioPosterShape = NuvioPosterShape.Poster,
    posterCardStyle: PosterCardStyleUiState? = null,
    detailLine: String? = null,
    showTitleBelow: Boolean = true,
    bottomLeftLogoUrl: String? = null,
    bottomLeftText: String? = null,
    isWatched: Boolean = false,
    animateWatchedBadge: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val resolvedPosterCardStyle = posterCardStyle ?: rememberPosterCardStyleUiState()
    val cardWidth = shape.cardWidth(basePosterWidthDp = resolvedPosterCardStyle.widthDp)
    val cardShape = RoundedCornerShape(resolvedPosterCardStyle.cornerRadiusDp.dp)
    val platformContext = LocalPlatformContext.current
    val density = LocalDensity.current
    val decodeSizeMultiplier = nuvioImageDecodeSizeMultiplier.coerceAtLeast(1f)
    val resolvedImageUrl = remember(imageUrl) { imageUrl?.upgradeTmdbImageQuality() }
    val resolvedBottomLeftLogoUrl = remember(bottomLeftLogoUrl) { bottomLeftLogoUrl?.upgradeTmdbImageQuality() }
    val imageRequest = remember(resolvedImageUrl, cardWidth, shape, density, decodeSizeMultiplier) {
        resolvedImageUrl?.let {
            val widthPx = with(density) { cardWidth.roundToPx() }.coerceAtLeast(1)
            val heightPx = (widthPx / shape.aspectRatio).roundToInt().coerceAtLeast(1)
            val requestWidthPx = (widthPx * decodeSizeMultiplier).roundToInt().coerceAtLeast(widthPx)
            val requestHeightPx = (heightPx * decodeSizeMultiplier).roundToInt().coerceAtLeast(heightPx)
            ImageRequest.Builder(platformContext)
                .data(it)
                .size(Size(requestWidthPx, requestHeightPx))
                .scale(Scale.FILL)
                .precision(Precision.EXACT)
                .nuvioPrescaleToDrawSize(widthPx, heightPx, Scale.FILL, Alignment.Center)
                .build()
        }
    }
    val catalogLogoOverlaySize = catalogLogoOverlaySize(
        basePosterWidthDp = resolvedPosterCardStyle.widthDp,
        shape = shape,
    )
    val shouldShowTitleBelow = showTitleBelow && !resolvedPosterCardStyle.hideLabelsEnabled

    Column(
        modifier = modifier.width(cardWidth),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(shape.aspectRatio)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surface)
                .posterCardClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            if (resolvedImageUrl != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!resolvedBottomLeftLogoUrl.isNullOrBlank() || !bottomLeftText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    if (!resolvedBottomLeftLogoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = resolvedBottomLeftLogoUrl,
                            contentDescription = stringResource(Res.string.poster_logo_content_description, title),
                            modifier = Modifier
                                .width(catalogLogoOverlaySize.width)
                                .height(catalogLogoOverlaySize.height),
                            contentScale = ContentScale.Fit,
                            useDesktopImagePainterWorkaround = false,
                        )
                    } else {
                        Text(
                            text = bottomLeftText.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = catalogLogoOverlaySize.textMaxWidth),
                        )
                    }
                }
            }

            val watchedBadgeModifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
            if (animateWatchedBadge) {
                NuvioAnimatedWatchedBadge(
                    isVisible = isWatched,
                    modifier = watchedBadgeModifier,
                )
            } else if (isWatched) {
                NuvioWatchedBadge(modifier = watchedBadgeModifier)
            }
        }
        if (shouldShowTitleBelow) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detailLine.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detailLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(modifier = Modifier.height(0.dp))
            }
        } else {
            Box(modifier = Modifier.height(0.dp))
        }
    }
}

@Composable
private fun NuvioShelfSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    showAccent: Boolean = true,
    onViewAllClick: (() -> Unit)? = null,
    viewAllPillSize: NuvioViewAllPillSize = NuvioViewAllPillSize.Default,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAccent) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(60.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp),
                    ),
                )
            }
        }
        if (onViewAllClick != null) {
            NuvioViewAllPill(
                onClick = onViewAllClick,
                size = viewAllPillSize,
            )
        }
    }
}

@Composable
private fun NuvioViewAllPill(
    onClick: (() -> Unit)?,
    size: NuvioViewAllPillSize,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isAmoled = colorScheme.background == androidx.compose.ui.graphics.Color.Black && colorScheme.surface == androidx.compose.ui.graphics.Color(0xFF050505)
    val horizontalPadding = if (size == NuvioViewAllPillSize.Compact) 12.dp else 18.dp
    val verticalPadding = if (size == NuvioViewAllPillSize.Compact) 7.dp else 12.dp
    val textStyle = if (size == NuvioViewAllPillSize.Compact) {
        MaterialTheme.typography.labelLarge
    } else {
        MaterialTheme.typography.titleMedium
    }
    val iconSpacing = if (size == NuvioViewAllPillSize.Compact) 2.dp else 4.dp

    Row(
        modifier = Modifier
            .background(
                color = if (isAmoled) androidx.compose.ui.graphics.Color(0xFF0D0D0D) else colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
            )
            .then(
                if (onClick != null) {
                    Modifier
                        .desktopClickablePointer()
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(iconSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.home_view_all),
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(if (size == NuvioViewAllPillSize.Compact) 16.dp else 20.dp)
                .height(if (size == NuvioViewAllPillSize.Compact) 16.dp else 20.dp),
        )
    }
}

private val NuvioPosterShape.aspectRatio: Float
    get() = when (this) {
        NuvioPosterShape.Poster -> 0.675f
        NuvioPosterShape.Square -> 1f
        NuvioPosterShape.Landscape -> PosterLandscapeAspectRatio
    }

private data class CatalogLogoOverlaySize(
    val width: Dp,
    val height: Dp,
    val textMaxWidth: Dp,
)

private fun catalogLogoOverlaySize(
    basePosterWidthDp: Int,
    shape: NuvioPosterShape,
): CatalogLogoOverlaySize {
    fun scaledDp(value: Float, min: Int, max: Int): Dp =
        value.roundToInt().coerceIn(min, max).dp

    return if (shape == NuvioPosterShape.Landscape) {
        val landscapeWidth = landscapePosterWidth(basePosterWidthDp).value
        val landscapeHeight = landscapeWidth / PosterLandscapeAspectRatio
        CatalogLogoOverlaySize(
            width = scaledDp(landscapeWidth * 0.58f, min = 112, max = 300),
            height = scaledDp(landscapeHeight * 0.46f, min = 42, max = 110),
            textMaxWidth = scaledDp(landscapeWidth * 0.72f, min = 140, max = 340),
        )
    } else {
        val logoWidth = scaledDp(basePosterWidthDp * 0.68f, min = 72, max = 140)
        CatalogLogoOverlaySize(
            width = logoWidth,
            height = scaledDp(logoWidth.value * 0.25f, min = 18, max = 36),
            textMaxWidth = scaledDp(basePosterWidthDp * 0.86f, min = 92, max = 170),
        )
    }
}

private fun NuvioPosterShape.cardWidth(basePosterWidthDp: Int): Dp =
    when (this) {
        NuvioPosterShape.Poster -> basePosterWidthDp.dp
        NuvioPosterShape.Square -> basePosterWidthDp.dp
        NuvioPosterShape.Landscape -> landscapePosterWidth(basePosterWidthDp)
    }

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.posterCardClickable(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
): Modifier {
    val withPrimaryGestures =
        if (onClick != null || onLongClick != null) {
            this.combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            ).desktopClickablePointer()
        } else {
            this
        }
    return withPrimaryGestures.desktopContextMenuPointer(onLongClick)
}
