package com.nuvio.app.features.watchprogress

import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CachedNextUpItem(
    val contentId: String,
    val contentType: String,
    val name: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val pauseDescription: String? = null,
    val released: String? = null,
    val hasAired: Boolean = true,
    val lastWatched: Long,
    val sortTimestamp: Long,
    val seedSeason: Int? = null,
    val seedEpisode: Int? = null,
)

@Serializable
data class CachedInProgressItem(
    val contentId: String,
    val contentType: String,
    val name: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val pauseDescription: String? = null,
    val position: Long,
    val duration: Long,
    val lastWatched: Long,
    val progressPercent: Float? = null,
)

internal data class ContinueWatchingEnrichmentSnapshot(
    val nextUp: List<CachedNextUpItem> = emptyList(),
    val inProgress: List<CachedInProgressItem> = emptyList(),
    val nextUpMissKeys: Set<String> = emptySet(),
)

@Serializable
private data class CachedNextUpMiss(
    val key: String,
    val cachedAtEpochMs: Long,
)

@Serializable
private data class CachedEnrichmentPayload(
    val nextUp: List<CachedNextUpItem> = emptyList(),
    val inProgress: List<CachedInProgressItem> = emptyList(),
    val nextUpMisses: List<CachedNextUpMiss> = emptyList(),
)

internal object ContinueWatchingEnrichmentCache {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val storageKey = "cw_enrichment_cache"
    private const val nextUpMissTtlMs = 5L * 60L * 1000L

    fun getNextUpSnapshot(): List<CachedNextUpItem> =
        loadPayload()?.nextUp ?: emptyList()

    fun getInProgressSnapshot(): List<CachedInProgressItem> =
        loadPayload()?.inProgress ?: emptyList()

    fun getNextUpMissSnapshot(nowEpochMs: Long = WatchProgressClock.nowEpochMs()): Set<String> =
        loadPayload()?.nextUpMisses.activeMissKeys(nowEpochMs)

    fun getSnapshot(nowEpochMs: Long = WatchProgressClock.nowEpochMs()): ContinueWatchingEnrichmentSnapshot {
        val payload = loadPayload()
        return ContinueWatchingEnrichmentSnapshot(
            nextUp = payload?.nextUp ?: emptyList(),
            inProgress = payload?.inProgress ?: emptyList(),
            nextUpMissKeys = payload?.nextUpMisses.activeMissKeys(nowEpochMs),
        )
    }

    fun getSnapshots(): Pair<List<CachedNextUpItem>, List<CachedInProgressItem>> {
        val snapshot = getSnapshot()
        return snapshot.nextUp to snapshot.inProgress
    }

    fun saveSnapshots(
        nextUp: List<CachedNextUpItem>,
        inProgress: List<CachedInProgressItem>,
        nextUpMissKeys: Set<String> = getNextUpMissSnapshot(),
        nowEpochMs: Long = WatchProgressClock.nowEpochMs(),
    ) {
        val payload = loadPayload()
        val activeExistingMisses = payload?.nextUpMisses
            .orEmpty()
            .activeMissesByKey(nowEpochMs)
        val nextUpMisses = nextUpMissKeys
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .map { key -> activeExistingMisses[key] ?: CachedNextUpMiss(key = key, cachedAtEpochMs = nowEpochMs) }
            .toList()
        val encoded = runCatching {
            json.encodeToString(
                CachedEnrichmentPayload(
                    nextUp = nextUp,
                    inProgress = inProgress,
                    nextUpMisses = nextUpMisses,
                ),
            )
        }.getOrNull() ?: return
        ContinueWatchingEnrichmentStorage.savePayload(ProfileScopedKey.of(storageKey), encoded)
    }

    private fun loadPayload(): CachedEnrichmentPayload? {
        val raw = ContinueWatchingEnrichmentStorage.loadPayload(ProfileScopedKey.of(storageKey))
            ?: return null
        return runCatching {
            json.decodeFromString<CachedEnrichmentPayload>(raw)
        }.getOrNull()
    }

    private fun List<CachedNextUpMiss>?.activeMissKeys(nowEpochMs: Long): Set<String> =
        activeMissesByKey(nowEpochMs).keys

    private fun List<CachedNextUpMiss>?.activeMissesByKey(nowEpochMs: Long): Map<String, CachedNextUpMiss> =
        orEmpty()
            .asSequence()
            .filter { miss -> miss.key.isNotBlank() }
            .filter { miss -> nowEpochMs - miss.cachedAtEpochMs in 0..nextUpMissTtlMs }
            .associateBy { miss -> miss.key }
}
