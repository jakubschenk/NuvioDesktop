package com.nuvio.app.features.addons

import co.touchlab.kermit.Logger
import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import kotlin.time.Duration.Companion.minutes

internal data class ApiKacheEntry(
    val cachedAtMs: Long,
    val body: String,
)

internal expect object ApiKacheClock {
    fun nowEpochMs(): Long
}

internal expect object SourceResponsePersistentKache {
    suspend fun read(cacheKey: String): ApiKacheEntry?
    suspend fun write(cacheKey: String, entry: ApiKacheEntry)
    suspend fun remove(cacheKey: String)
}

internal object ApiKacheClient {
    private const val GenericGetTtlMs = 60_000L
    const val SourceResponseTtlMs = 5L * 60L * 1000L

    private val log = Logger.withTag("ApiKache")
    private val textCache = InMemoryKache<String, ApiKacheEntry>(
        maxSize = 32L * 1024L,
    ) {
        strategy = KacheStrategy.LRU
        expireAfterWriteDuration = 5.minutes
        sizeCalculator = { key, value ->
            ((key.length + value.body.length) / 1024L).coerceAtLeast(1L)
        }
    }

    suspend fun getText(
        url: String,
        fetch: suspend () -> String,
    ): String =
        cachedText(
            cacheKey = "GET|$url",
            ttlMs = GenericGetTtlMs,
            persistentSourceCache = false,
            fetch = fetch,
        )

    suspend fun getSourceText(
        url: String,
        fetch: suspend () -> String,
    ): String =
        cachedText(
            cacheKey = "SOURCE|GET|$url",
            ttlMs = SourceResponseTtlMs,
            persistentSourceCache = true,
            fetch = fetch,
        )

    suspend fun <T> noStore(
        fetch: suspend () -> T,
    ): T = fetch()

    private suspend fun cachedText(
        cacheKey: String,
        ttlMs: Long,
        persistentSourceCache: Boolean,
        fetch: suspend () -> String,
    ): String {
        val memoryEntry = freshMemoryEntry(cacheKey, ttlMs)
        if (memoryEntry != null) {
            return memoryEntry.body
        }

        if (persistentSourceCache) {
            val diskEntry = SourceResponsePersistentKache.read(cacheKey)
            if (diskEntry != null && diskEntry.isFresh(ttlMs)) {
                textCache.put(cacheKey, diskEntry)
                log.d { "Source response disk cache hit" }
                return diskEntry.body
            }
            if (diskEntry != null) {
                SourceResponsePersistentKache.remove(cacheKey)
            }
        }

        val entry = textCache.getOrPut(cacheKey) {
            ApiKacheEntry(
                cachedAtMs = ApiKacheClock.nowEpochMs(),
                body = fetch(),
            )
        } ?: ApiKacheEntry(
            cachedAtMs = ApiKacheClock.nowEpochMs(),
            body = fetch(),
        )

        if (!entry.isFresh(ttlMs)) {
            textCache.remove(cacheKey)
            return cachedText(
                cacheKey = cacheKey,
                ttlMs = ttlMs,
                persistentSourceCache = persistentSourceCache,
                fetch = fetch,
            )
        }

        if (persistentSourceCache) {
            SourceResponsePersistentKache.write(cacheKey, entry)
        }

        return entry.body
    }

    private suspend fun freshMemoryEntry(cacheKey: String, ttlMs: Long): ApiKacheEntry? {
        val entry = textCache.get(cacheKey) ?: return null
        if (entry.isFresh(ttlMs)) {
            return entry
        }
        textCache.remove(cacheKey)
        return null
    }

    private fun ApiKacheEntry.isFresh(ttlMs: Long): Boolean =
        cachedAtMs > 0L && ApiKacheClock.nowEpochMs() - cachedAtMs <= ttlMs
}
