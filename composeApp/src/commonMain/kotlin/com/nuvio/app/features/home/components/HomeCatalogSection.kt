package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.PosterCardStyleUiState
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState

@Composable
fun HomeCatalogRowSection(
    section: HomeCatalogSection,
    modifier: Modifier = Modifier,
    entries: List<MetaPreview> = section.items,
    watchedKeys: Set<String> = emptySet(),
    sectionPadding: Dp? = null,
    posterCardStyle: PosterCardStyleUiState? = null,
    showHeaderAccent: Boolean = true,
    onViewAllClick: (() -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    if (sectionPadding != null) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            HomeCatalogRowSectionContent(
                section = section,
                entries = entries,
                watchedKeys = watchedKeys,
                modifier = Modifier.fillMaxWidth(),
                sectionPadding = sectionPadding,
                availableWidth = maxWidth,
                posterCardStyle = posterCardStyle,
                showHeaderAccent = showHeaderAccent,
                onViewAllClick = onViewAllClick,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
            )
        }
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            HomeCatalogRowSectionContent(
                section = section,
                entries = entries,
                watchedKeys = watchedKeys,
                modifier = Modifier.fillMaxWidth(),
                sectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value),
                availableWidth = maxWidth,
                posterCardStyle = posterCardStyle,
                showHeaderAccent = showHeaderAccent,
                onViewAllClick = onViewAllClick,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
            )
        }
    }
}

@Composable
private fun HomeCatalogRowSectionContent(
    section: HomeCatalogSection,
    entries: List<MetaPreview>,
    watchedKeys: Set<String>,
    modifier: Modifier,
    sectionPadding: Dp,
    availableWidth: Dp,
    posterCardStyle: PosterCardStyleUiState?,
    showHeaderAccent: Boolean,
    onViewAllClick: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    val resolvedPosterCardStyle = posterCardStyle ?: rememberPosterCardStyleUiState()
    val maxVisibleItems = remember(availableWidth, sectionPadding, entries, resolvedPosterCardStyle) {
        maxHomeCatalogItemsForViewport(
            availableWidth = availableWidth,
            sectionPadding = sectionPadding,
            entries = entries,
            posterCardStyle = resolvedPosterCardStyle,
        )
    }

    NuvioShelfSection(
        title = section.title,
        entries = entries,
        modifier = modifier,
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        showHeaderAccent = showHeaderAccent,
        onViewAllClick = onViewAllClick,
        viewAllPillSize = NuvioViewAllPillSize.Compact,
        maxItems = maxVisibleItems,
        key = { item -> item.stableKey() },
        contentType = { item -> item.posterShape },
    ) { item ->
        HomePosterCard(
            item = item,
            useLandscapeBackdropMode = resolvedPosterCardStyle.catalogLandscapeModeEnabled,
            posterCardStyle = resolvedPosterCardStyle,
            isWatched = WatchingState.isPosterWatched(
                watchedKeys = watchedKeys,
                item = item,
            ),
            onClick = onPosterClick?.let { { it(item) } },
            onLongClick = onPosterLongClick?.let { { it(item) } },
        )
    }
}

private fun maxHomeCatalogItemsForViewport(
    availableWidth: Dp,
    sectionPadding: Dp,
    entries: List<MetaPreview>,
    posterCardStyle: PosterCardStyleUiState,
): Int {
    if (entries.isEmpty()) return 0
    val contentWidth = (availableWidth.value - sectionPadding.value * 2f).coerceAtLeast(0f)
    val shape = if (posterCardStyle.catalogLandscapeModeEnabled) {
        PosterShape.Landscape
    } else {
        entries.first().posterShape
    }
    val cardWidth = when (shape) {
        PosterShape.Poster,
        PosterShape.Square -> posterCardStyle.widthDp.toFloat()
        PosterShape.Landscape -> landscapePosterWidth(posterCardStyle.widthDp).value
    }
    val spacing = 10f
    return ((contentWidth + spacing) / (cardWidth + spacing))
        .toInt()
        .coerceAtLeast(1)
        .coerceAtMost(entries.size)
}
