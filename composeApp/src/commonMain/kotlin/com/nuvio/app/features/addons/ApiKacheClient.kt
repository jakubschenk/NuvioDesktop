package com.nuvio.app.features.addons

import co.touchlab.kermit.Logger
import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import com.nuvio.app.core.logging.redactedUrlForLog
import kotlin.time.Duration.Companion.minutes

internal data class ApiKacheEntry(
    val cachedAtMs: Long,
    val body: String,
)

internal expect object ApiKacheClock {
    fun nowEpochMs(): Long
}

internal expect object ApiRequestTraceLog {
    fun append(line: String)
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
            label = "generic-get",
            url = url,
            cacheKey = "GET|$url",
            ttlMs = GenericGetTtlMs,
            persistentSourceCache = false,
            fetch = fetch,
        )

    suspend fun getSourceText(
        url: String,
        forceRefresh: Boolean = false,
        fetch: suspend () -> String,
    ): String =
        cachedText(
            label = "source-get",
            url = url,
            cacheKey = "SOURCE|GET|$url",
            ttlMs = SourceResponseTtlMs,
            persistentSourceCache = true,
            forceRefresh = forceRefresh,
            fetch = fetch,
        )

    suspend fun <T> noStore(
        label: String,
        method: String,
        url: String,
        fetch: suspend () -> T,
    ): T {
        trace("NO_STORE_START label=$label method=${method.uppercase()} ${url.redactedUrlForLog()}")
        return runCatching { fetch() }
            .onSuccess {
                trace("NO_STORE_END label=$label method=${method.uppercase()} ${url.redactedUrlForLog()}")
            }
            .onFailure { error ->
                trace("NO_STORE_ERROR label=$label method=${method.uppercase()} ${url.redactedUrlForLog()} error=${error.safeMessage()}")
            }
            .getOrThrow()
    }

    fun traceNetworkStart(label: String, method: String, url: String) {
        trace("NETWORK_START label=$label method=${method.uppercase()} ${url.redactedUrlForLog()}")
    }

    fun traceNetworkEnd(label: String, method: String, url: String, status: Int, bytes: Int) {
        trace("NETWORK_END label=$label method=${method.uppercase()} status=$status bytes=$bytes ${url.redactedUrlForLog()}")
    }

    fun traceNetworkError(label: String, method: String, url: String, error: Throwable) {
        trace("NETWORK_ERROR label=$label method=${method.uppercase()} ${url.redactedUrlForLog()} error=${error.safeMessage()}")
    }

    private suspend fun cachedText(
        label: String,
        url: String,
        cacheKey: String,
        ttlMs: Long,
        persistentSourceCache: Boolean,
        forceRefresh: Boolean = false,
        fetch: suspend () -> String,
    ): String {
        if (forceRefresh) {
            textCache.remove(cacheKey)
            if (persistentSourceCache) {
                SourceResponsePersistentKache.remove(cacheKey)
            }
        }

        val memoryEntry = freshMemoryEntry(cacheKey, ttlMs)
        if (memoryEntry != null) {
            trace("CACHE_HIT_MEMORY label=$label ttlMs=$ttlMs ${url.redactedUrlForLog()}")
            return memoryEntry.body
        }

        if (persistentSourceCache) {
            val diskEntry = SourceResponsePersistentKache.read(cacheKey)
            if (diskEntry != null && diskEntry.isFresh(ttlMs)) {
                textCache.put(cacheKey, diskEntry)
                log.d { "Source response disk cache hit" }
                trace("CACHE_HIT_DISK label=$label ttlMs=$ttlMs ${url.redactedUrlForLog()}")
                return diskEntry.body
            }
            if (diskEntry != null) {
                trace("CACHE_EXPIRED_DISK label=$label ttlMs=$ttlMs ${url.redactedUrlForLog()}")
                SourceResponsePersistentKache.remove(cacheKey)
            }
        }

        trace("CACHE_MISS label=$label ttlMs=$ttlMs ${url.redactedUrlForLog()}")
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
                label = label,
                url = url,
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

    private fun trace(message: String) {
        ApiRequestTraceLog.append("${ApiKacheClock.nowEpochMs()} $message")
    }

    private fun Throwable.safeMessage(): String =
        "${this::class.simpleName}:${message?.take(180).orEmpty()}"
}
