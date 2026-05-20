package com.nuvio.app.features.addons

import platform.Foundation.NSDate

internal actual object ApiKacheClock {
    actual fun nowEpochMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}

internal actual object ApiRequestTraceLog {
    actual fun append(line: String) = Unit
}

internal actual object SourceResponsePersistentKache {
    actual suspend fun read(cacheKey: String): ApiKacheEntry? = null

    actual suspend fun write(cacheKey: String, entry: ApiKacheEntry) = Unit

    actual suspend fun remove(cacheKey: String) = Unit
}
