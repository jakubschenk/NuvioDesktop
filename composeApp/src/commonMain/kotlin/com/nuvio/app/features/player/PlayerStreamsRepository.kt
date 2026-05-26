package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.httpGetSourceText
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DebridStreamPresentation
import com.nuvio.app.features.debrid.DirectDebridStreamPreparer
import com.nuvio.app.features.debrid.LocalDebridAvailabilityService
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.pluginContentId
import com.nuvio.app.features.plugins.PluginRepositoryItem
import com.nuvio.app.features.plugins.PluginRuntimeResult
import com.nuvio.app.features.plugins.PluginScraper
import com.nuvio.app.features.streams.AddonStreamWarmupRepository
import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamAutoPlaySelector
import com.nuvio.app.features.streams.StreamsEmptyStateReason
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamParser
import com.nuvio.app.features.streams.StreamsUiState
import com.nuvio.app.features.streams.epochMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dedicated stream fetcher for use inside the player (sources & episodes panels).
 * Uses its own state so it doesn't interfere with the main [StreamsRepository].
 */
object PlayerStreamsRepository {
    private const val PlayerStreamsCacheTtlMs = 5L * 60L * 1000L

    private val log = Logger.withTag("PlayerStreamsRepo")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val streamCache = mutableMapOf<String, PlayerStreamsCacheEntry>()

    // source panel
    private val _sourceState = MutableStateFlow(StreamsUiState())
    val sourceState: StateFlow<StreamsUiState> = _sourceState.asStateFlow()
    private var sourceJob: Job? = null
    private var sourceRequestKey: String? = null

    // episode streams panel
    private val _episodeStreamsState = MutableStateFlow(StreamsUiState())
    val episodeStreamsState: StateFlow<StreamsUiState> = _episodeStreamsState.asStateFlow()
    private var episodeStreamsJob: Job? = null
    private var episodeStreamsRequestKey: String? = null

    fun loadSources(
        type: String,
        videoId: String,
        season: Int? = null,
        episode: Int? = null,
        forceRefresh: Boolean = false,
    ) {
        fetchStreams(
            type = type,
            videoId = videoId,
            season = season,
            episode = episode,
            forceRefresh = forceRefresh,
            stateFlow = _sourceState,
            requestKeyHolder = { sourceRequestKey },
            setRequestKey = { sourceRequestKey = it },
            jobHolder = { sourceJob },
            setJob = { sourceJob = it },
        )
    }

    fun loadEpisodeStreams(
        type: String,
        videoId: String,
        season: Int? = null,
        episode: Int? = null,
        forceRefresh: Boolean = false,
    ) {
        fetchStreams(
            type = type,
            videoId = videoId,
            season = season,
            episode = episode,
            forceRefresh = forceRefresh,
            stateFlow = _episodeStreamsState,
            requestKeyHolder = { episodeStreamsRequestKey },
            setRequestKey = { episodeStreamsRequestKey = it },
            jobHolder = { episodeStreamsJob },
            setJob = { episodeStreamsJob = it },
        )
    }

    fun selectSourceFilter(addonId: String?) {
        _sourceState.update { it.copy(selectedFilter = addonId) }
    }

    fun selectEpisodeStreamsFilter(addonId: String?) {
        _episodeStreamsState.update { it.copy(selectedFilter = addonId) }
    }

    fun clearEpisodeStreams() {
        episodeStreamsJob?.cancel()
        episodeStreamsRequestKey = null
        _episodeStreamsState.value = StreamsUiState()
    }

    fun clearAll() {
        sourceJob?.cancel()
        sourceRequestKey = null
        _sourceState.value = StreamsUiState()
        clearEpisodeStreams()
    }

    private fun fetchStreams(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
        forceRefresh: Boolean,
        stateFlow: MutableStateFlow<StreamsUiState>,
        requestKeyHolder: () -> String?,
        setRequestKey: (String?) -> Unit,
        jobHolder: () -> Job?,
        setJob: (Job) -> Unit,
    ) {
        val requestKey = "$type::$videoId::$season::$episode"
        val current = stateFlow.value
        if (
            !forceRefresh &&
            requestKeyHolder() == requestKey &&
            (current.groups.isNotEmpty() || current.emptyStateReason != null || current.isAnyLoading)
        ) {
            return
        }

        setRequestKey(requestKey)
        jobHolder()?.cancel()
        stateFlow.value = StreamsUiState()

        val embeddedStreams = MetaDetailsRepository.findEmbeddedStreams(videoId)
        if (embeddedStreams.isNotEmpty()) {
            log.d { "Using ${embeddedStreams.size} embedded streams for type=$type id=$videoId" }
            val group = AddonStreamGroup(
                addonName = embeddedStreams.first().addonName,
                addonId = "embedded",
                streams = embeddedStreams,
                isLoading = false,
            )
            stateFlow.value = StreamsUiState(
                groups = listOf(group),
                activeAddonIds = setOf("embedded"),
                isAnyLoading = false,
            )
            return
        }

        val installedAddons = AddonRepository.uiState.value.addons.enabledAddons()
        val installedAddonNames = installedAddons
            .map { addon -> addon.displayTitle.ifBlank { addon.manifest?.name.orEmpty() } }
            .filter { it.isNotBlank() }
            .toSet()
        PlayerSettingsRepository.ensureLoaded()
        val playerSettings = PlayerSettingsRepository.uiState.value
        val debridSettings = DebridSettingsRepository.snapshot()
        val pluginUiState = if (AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.initialize()
            PluginRepository.uiState.value
        } else {
            null
        }
        val pluginScrapers = if (pluginUiState != null) {
            PluginRepository.getEnabledScrapersForType(type)
        } else {
            emptyList()
        }
        val pluginProviderGroups = pluginScrapers.toPlayerPluginProviderGroups(
            repositories = pluginUiState?.repositories.orEmpty(),
            groupByRepository = pluginUiState?.groupStreamsByRepository == true,
        )

        if (installedAddons.isEmpty() && pluginProviderGroups.isEmpty()) {
            stateFlow.value = StreamsUiState(
                isAnyLoading = false,
                emptyStateReason = com.nuvio.app.features.streams.StreamsEmptyStateReason.NoAddonsInstalled,
            )
            return
        }

        val streamAddons = installedAddons
            .mapNotNull { addon ->
                val manifest = addon.manifest ?: return@mapNotNull null
                val supportsRequestedStream = manifest.resources.any { resource ->
                    resource.name == "stream" &&
                        resource.types.contains(type) &&
                        (resource.idPrefixes.isEmpty() ||
                            resource.idPrefixes.any { videoId.startsWith(it) })
                }
                if (!supportsRequestedStream) return@mapNotNull null

                PlayerInstalledStreamAddonTarget(
                    addonName = addon.displayTitle.ifBlank { manifest.name },
                    addonId = addon.streamAddonInstanceId(manifest.id),
                    manifest = manifest,
                )
            }

        if (streamAddons.isEmpty() && pluginProviderGroups.isEmpty()) {
            stateFlow.value = StreamsUiState(
                isAnyLoading = false,
                emptyStateReason = com.nuvio.app.features.streams.StreamsEmptyStateReason.NoCompatibleAddons,
            )
            return
        }

        val installedAddonOrder = streamAddons.map { it.addonName }
        val cacheKey = playerStreamsCacheKey(
            type = type,
            videoId = videoId,
            season = season,
            episode = episode,
            groupByRepository = pluginUiState?.groupStreamsByRepository == true,
            streamAddons = streamAddons,
            pluginProviderGroups = pluginProviderGroups,
        )
        if (!forceRefresh) {
            val cached = getFreshStreamCache(cacheKey)
            if (cached != null) {
                jobHolder()?.cancel()
                stateFlow.value = StreamsUiState(
                    groups = cached.groups,
                    activeAddonIds = cached.activeAddonIds,
                    isAnyLoading = false,
                    emptyStateReason = cached.emptyStateReason,
                )
                log.d { "Using cached player streams for type=$type id=$videoId" }
                return
            }
        }

        val warmedAddonGroups = AddonStreamWarmupRepository
            .cachedGroups(type = type, videoId = videoId, season = season, episode = episode)
            .orEmpty()
            .associateBy { it.addonId }
        val warmedAddonIds = warmedAddonGroups.keys
        val initialGroups = StreamAutoPlaySelector.orderAddonStreams(
            streamAddons.map { addon ->
                warmedAddonGroups[addon.addonId] ?: AddonStreamGroup(
                    addonName = addon.addonName,
                    addonId = addon.addonId,
                    streams = emptyList(),
                    isLoading = true,
                )
            } + pluginProviderGroups.map { providerGroup ->
                AddonStreamGroup(
                    addonName = providerGroup.addonName,
                    addonId = providerGroup.addonId,
                    streams = emptyList(),
                    isLoading = true,
                )
            },
            installedAddonOrder,
        )
        val isInitiallyLoading = initialGroups.any { it.isLoading }
        stateFlow.value = StreamsUiState(
            groups = initialGroups,
            activeAddonIds = initialGroups.map { it.addonId }.toSet(),
            isAnyLoading = isInitiallyLoading,
        )

        val job = scope.launch {
            val pendingStreamAddons = streamAddons.filterNot { it.addonId in warmedAddonIds }
            val installedAddonIds = streamAddons.map { it.addonId }.toSet()
            val debridAvailabilityJobs = mutableListOf<Job>()
            fun emptyStateReason(groups: List<AddonStreamGroup>, anyLoading: Boolean) =
                if (!anyLoading && groups.all { it.streams.isEmpty() }) {
                    if (groups.all { !it.error.isNullOrBlank() }) {
                        com.nuvio.app.features.streams.StreamsEmptyStateReason.StreamFetchFailed
                    } else {
                        com.nuvio.app.features.streams.StreamsEmptyStateReason.NoStreamsFound
                    }
                } else {
                    null
                }

            fun presentDebridGroup(group: AddonStreamGroup): AddonStreamGroup =
                DebridStreamPresentation.apply(
                    groups = listOf(group),
                    settings = debridSettings,
                ).firstOrNull() ?: group

            fun publishStreamGroup(group: AddonStreamGroup) {
                stateFlow.update { current ->
                    val updated = StreamAutoPlaySelector.orderAddonStreams(
                        groups = current.groups.map { currentGroup ->
                            if (currentGroup.addonId == group.addonId) group else currentGroup
                        },
                        installedOrder = installedAddonOrder,
                    )
                    val anyLoading = updated.any { it.isLoading }
                    current.copy(
                        groups = updated,
                        isAnyLoading = anyLoading,
                        emptyStateReason = emptyStateReason(updated, anyLoading),
                    )
                }
            }

            fun launchDebridAvailability(group: AddonStreamGroup) {
                if (group.addonId !in installedAddonIds || group.streams.isEmpty()) return

                val eligibleGroupIds = setOf(group.addonId)
                val checkingGroup = LocalDebridAvailabilityService.markChecking(
                    groups = listOf(group),
                    eligibleGroupIds = eligibleGroupIds,
                ).firstOrNull() ?: group
                publishStreamGroup(checkingGroup)

                val availabilityJob = launch {
                    val availabilityGroup = LocalDebridAvailabilityService.annotateCachedAvailability(
                        groups = listOf(checkingGroup),
                        eligibleGroupIds = eligibleGroupIds,
                    ).firstOrNull() ?: checkingGroup
                    publishStreamGroup(presentDebridGroup(availabilityGroup))
                }
                debridAvailabilityJobs += availabilityJob
            }

            val addonJobs = pendingStreamAddons.map { addon ->
                async {
                    val url = buildAddonResourceUrl(
                        manifestUrl = addon.manifest.transportUrl,
                        resource = "stream",
                        type = type,
                        id = videoId,
                    )

                    val displayName = addon.addonName
                    runCatching {
                        val payload = httpGetSourceText(url, forceRefresh = forceRefresh)
                        StreamParser.parse(payload, displayName, addon.addonId)
                    }.fold(
                        onSuccess = { streams ->
                            AddonStreamGroup(displayName, addon.addonId, streams, isLoading = false)
                        },
                        onFailure = { err ->
                            log.w(err) { "Failed: ${displayName}" }
                            AddonStreamGroup(displayName, addon.addonId, emptyList(), isLoading = false, error = err.message)
                        },
                    )
                }
            }

            val pluginJobs = pluginProviderGroups.map { providerGroup ->
                async {
                    val scraperResults = providerGroup.scrapers.map { scraper ->
                        async {
                            PluginRepository.executeScraper(
                                scraper = scraper,
                                tmdbId = pluginContentId(
                                    videoId = videoId,
                                    season = season,
                                    episode = episode,
                                ),
                                mediaType = type,
                                season = season,
                                episode = episode,
                            ).fold(
                                onSuccess = { results ->
                                    PlayerPluginScraperResult(
                                        streams = results.map { result ->
                                            result.toStreamItem(
                                                scraper = scraper,
                                                addonName = providerGroup.addonName,
                                                addonId = providerGroup.addonId,
                                            )
                                        },
                                        error = null,
                                    )
                                },
                                onFailure = { err ->
                                    log.w(err) { "Plugin scraper failed: ${scraper.name}" }
                                    PlayerPluginScraperResult(
                                        streams = emptyList(),
                                        error = err.message,
                                    )
                                },
                            )
                        }
                    }.awaitAll()
                    val streams = scraperResults.flatMap { it.streams }.sortedForPlayerGroupedDisplay()
                    AddonStreamGroup(
                        addonName = providerGroup.addonName,
                        addonId = providerGroup.addonId,
                        streams = streams,
                        isLoading = false,
                        error = if (streams.isEmpty()) scraperResults.firstNotNullOfOrNull { it.error } else null,
                    )
                }
            }

            val jobs = addonJobs + pluginJobs
            val completions = Channel<AddonStreamGroup>(capacity = Channel.BUFFERED)
            jobs.forEach { deferred ->
                launch {
                    completions.send(deferred.await())
                }
            }
            repeat(jobs.size) {
                val result = completions.receive()
                publishStreamGroup(result)
                launchDebridAvailability(result)
            }
            completions.close()
            for (availabilityJob in debridAvailabilityJobs) {
                availabilityJob.join()
            }
            DirectDebridStreamPreparer.prepare(
                streams = stateFlow.value.groups
                    .filter { it.addonId in installedAddonIds }
                    .flatMap { it.streams },
                season = season,
                episode = episode,
                playerSettings = playerSettings,
                installedAddonNames = installedAddonNames,
            ) { original, prepared ->
                stateFlow.update { current ->
                    current.copy(
                        groups = DirectDebridStreamPreparer.replacePreparedStream(
                            groups = current.groups,
                            original = original,
                            prepared = prepared,
                            eligibleGroupIds = installedAddonIds,
                        ),
                    )
                }
            }

            saveStreamCache(cacheKey, stateFlow.value.groups)
        }
        setJob(job)
    }

    private fun getFreshStreamCache(cacheKey: String): PlayerStreamsCacheEntry? {
        val cached = streamCache[cacheKey] ?: return null
        val ageMs = epochMs() - cached.cachedAtMs
        if (cached.cachedAtMs <= 0L || ageMs > PlayerStreamsCacheTtlMs) {
            streamCache.remove(cacheKey)
            return null
        }
        return cached
    }

    private fun saveStreamCache(cacheKey: String, groups: List<AddonStreamGroup>) {
        if (groups.none { it.streams.isNotEmpty() }) {
            log.d { "Skipping empty player stream cache entry" }
            return
        }
        val cachedGroups = groups.map { it.copy(isLoading = false) }
        streamCache[cacheKey] = PlayerStreamsCacheEntry(
            groups = cachedGroups,
            activeAddonIds = cachedGroups.map { it.addonId }.toSet(),
            emptyStateReason = cachedGroups.toPlayerEmptyStateReason(anyLoading = false),
            cachedAtMs = epochMs(),
        )
    }

    private fun playerStreamsCacheKey(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
        groupByRepository: Boolean,
        streamAddons: List<PlayerInstalledStreamAddonTarget>,
        pluginProviderGroups: List<PlayerPluginProviderGroup>,
    ): String = buildString {
        append(type.lowercase())
        append('|')
        append(videoId.trim())
        append('|')
        append(season ?: -1)
        append('|')
        append(episode ?: -1)
        append("|grouped=")
        append(groupByRepository)
        append("|addons=")
        append(streamAddons.joinToString(separator = ",") { it.addonId })
        append("|plugins=")
        append(
            pluginProviderGroups.joinToString(separator = ",") { group ->
                "${group.addonId}[${group.scrapers.joinToString(separator = "+") { it.id }}]"
            },
        )
    }
}

private data class PlayerStreamsCacheEntry(
    val groups: List<AddonStreamGroup>,
    val activeAddonIds: Set<String>,
    val emptyStateReason: StreamsEmptyStateReason?,
    val cachedAtMs: Long,
)

private data class PlayerInstalledStreamAddonTarget(
    val addonName: String,
    val addonId: String,
    val manifest: com.nuvio.app.features.addons.AddonManifest,
)

private data class PlayerPluginProviderGroup(
    val addonId: String,
    val addonName: String,
    val scrapers: List<PluginScraper>,
)

private data class PlayerPluginScraperResult(
    val streams: List<StreamItem>,
    val error: String?,
)

private fun List<AddonStreamGroup>.toPlayerEmptyStateReason(anyLoading: Boolean): StreamsEmptyStateReason? {
    if (anyLoading || any { it.streams.isNotEmpty() }) {
        return null
    }

    return if (isNotEmpty() && all { !it.error.isNullOrBlank() }) {
        StreamsEmptyStateReason.StreamFetchFailed
    } else {
        StreamsEmptyStateReason.NoStreamsFound
    }
}

private fun com.nuvio.app.features.addons.ManagedAddon.streamAddonInstanceId(manifestId: String): String =
    "addon:$manifestId:$manifestUrl"

private fun List<PluginScraper>.toPlayerPluginProviderGroups(
    repositories: List<PluginRepositoryItem>,
    groupByRepository: Boolean,
): List<PlayerPluginProviderGroup> {
    if (!groupByRepository) {
        return map { scraper ->
            PlayerPluginProviderGroup(
                addonId = "plugin:${scraper.id}",
                addonName = scraper.name,
                scrapers = listOf(scraper),
            )
        }
    }

    val repoNameByUrl = repositories.associate { it.manifestUrl to it.name }
    return groupBy { it.repositoryUrl }
        .map { (repositoryUrl, scrapers) ->
            PlayerPluginProviderGroup(
                addonId = "plugin-repo:${repositoryUrl.lowercase()}",
                addonName = repoNameByUrl[repositoryUrl].orEmpty().ifBlank { repositoryUrl.fallbackRepositoryLabel() },
                scrapers = scrapers.sortedBy { it.name.lowercase() },
            )
        }
        .sortedBy { it.addonName.lowercase() }
}

private fun PluginRuntimeResult.toStreamItem(
    scraper: PluginScraper,
    addonName: String,
    addonId: String,
): StreamItem {
    val subtitleParts = listOfNotNull(
        quality?.takeIf { it.isNotBlank() },
        size?.takeIf { it.isNotBlank() },
        language?.takeIf { it.isNotBlank() },
    )
    val requestHeaders = headers
        .orEmpty()
        .mapNotNull { (key, value) ->
            val headerName = key.trim()
            val headerValue = value.trim()
            if (headerName.isBlank() || headerValue.isBlank() || headerName.equals("Range", ignoreCase = true)) {
                null
            } else {
                headerName to headerValue
            }
        }
        .toMap()

    return StreamItem(
        name = name ?: title,
        description = subtitleParts.joinToString(" • ").ifBlank { null },
        url = url,
        infoHash = infoHash,
        sourceName = scraper.name,
        addonName = addonName,
        addonId = addonId,
        behaviorHints = if (requestHeaders.isEmpty()) {
            com.nuvio.app.features.streams.StreamBehaviorHints()
        } else {
            com.nuvio.app.features.streams.StreamBehaviorHints(
                notWebReady = true,
                proxyHeaders = com.nuvio.app.features.streams.StreamProxyHeaders(request = requestHeaders),
            )
        },
    )
}

private fun List<StreamItem>.sortedForPlayerGroupedDisplay(): List<StreamItem> =
    sortedWith(
        compareBy<StreamItem>(
            { it.sourceName.orEmpty().lowercase() },
            { it.streamLabel.lowercase() },
            { it.streamSubtitle.orEmpty().lowercase() },
        ),
    )

private fun String.fallbackRepositoryLabel(): String {
    val withoutQuery = substringBefore("?")
    val withoutManifest = withoutQuery.removeSuffix("/manifest.json")
    val host = withoutManifest.substringAfter("://", withoutManifest).substringBefore('/')
    return host.ifBlank {
        withoutManifest.substringAfterLast('/').ifBlank { "Plugin repository" }
    }
}
