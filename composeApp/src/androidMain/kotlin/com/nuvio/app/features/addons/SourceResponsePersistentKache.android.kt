package com.nuvio.app.features.addons

internal actual object ApiKacheClock {
    actual fun nowEpochMs(): Long = System.currentTimeMillis()
}

internal actual object SourceResponsePersistentKache {
    actual suspend fun read(cacheKey: String): ApiKacheEntry? = null

    actual suspend fun write(cacheKey: String, entry: ApiKacheEntry) = Unit

    actual suspend fun remove(cacheKey: String) = Unit
}
