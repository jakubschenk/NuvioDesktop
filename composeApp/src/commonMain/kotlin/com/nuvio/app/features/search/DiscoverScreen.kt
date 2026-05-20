package com.nuvio.app.features.search

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    topPadding: Dp? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        WatchedRepository.ensureLoaded()
    }

    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val discoverUiState by SearchRepository.discoverUiState.collectAsStateWithLifecycle()
    val homeCatalogSettingsUiState by HomeCatalogSettingsRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val networkStatusUiState by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    var observedOfflineState by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val addonRefreshKey = remember(addonsUiState.addons) {
        buildAddonCatalogRefreshKey(addonsUiState.addons)
    }

    LaunchedEffect(addonRefreshKey, homeCatalogSettingsUiState.hideUnreleasedContent) {
        SearchRepository.refreshDiscover(addonsUiState.addons)
    }

    LaunchedEffect(listState, discoverUiState.canLoadMore, discoverUiState.isLoading) {
        snapshotFlow { listState.layoutInfo }
            .map { layoutInfo ->
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= layoutInfo.totalItemsCount - 4
            }
            .distinctUntilChanged()
            .filter { it && discoverUiState.canLoadMore && !discoverUiState.isLoading }
            .collect {
                SearchRepository.loadMoreDiscover()
            }
    }

    LaunchedEffect(networkStatusUiState.condition, addonRefreshKey) {
        when (networkStatusUiState.condition) {
            NetworkCondition.NoInternet,
            NetworkCondition.ServersUnreachable,
            -> {
                observedOfflineState = true
            }

            NetworkCondition.Online -> {
                if (!observedOfflineState) return@LaunchedEffect
                observedOfflineState = false
                SearchRepository.refreshDiscover(addonsUiState.addons)
            }

            NetworkCondition.Unknown,
            NetworkCondition.Checking,
            -> Unit
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val discoverColumns = remember(maxWidth) {
            discoverColumnCountForWidth(maxWidth)
        }

        NuvioScreen(
            horizontalPadding = 0.dp,
            topPadding = topPadding,
            listState = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            discoverContent(
                state = discoverUiState,
                columns = discoverColumns,
                networkCondition = networkStatusUiState.condition,
                onTypeSelected = SearchRepository::selectDiscoverType,
                onCatalogSelected = SearchRepository::selectDiscoverCatalog,
                onGenreSelected = SearchRepository::selectDiscoverGenre,
                onRetry = {
                    NetworkStatusRepository.requestRefresh(force = true)
                    SearchRepository.refreshDiscover(addonsUiState.addons)
                },
                watchedKeys = watchedUiState.watchedKeys,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
            )
        }
    }
}
