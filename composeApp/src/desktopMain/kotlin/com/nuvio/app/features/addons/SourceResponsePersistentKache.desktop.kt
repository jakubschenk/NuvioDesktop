package com.nuvio.app.features.addons

import com.mayakapps.kache.FileKache
import com.mayakapps.kache.KacheStrategy
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal actual object ApiKacheClock {
    actual fun nowEpochMs(): Long = System.currentTimeMillis()
}

internal actual object ApiRequestTraceLog {
    private const val maxLogBytes = 5L * 1024L * 1024L
    private val logLock = Any()

    actual fun append(line: String) {
        synchronized(logLock) {
            runCatching {
                val file = traceLogFile()
                if (file.exists() && file.fileSize() > maxLogBytes) {
                    file.writeText("", StandardCharsets.UTF_8)
                }
                java.nio.file.Files.writeString(
                    file,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND,
                )
            }
        }
    }

    private fun traceLogFile(): Path =
        Paths.get(
            System.getProperty("user.home"),
            "Library",
            "Application Support",
            "Nuvio",
            "logs",
            "network-requests.log",
        ).apply {
            parent.createDirectories()
        }
}

internal actual object SourceResponsePersistentKache {
    private const val maxCacheBytes = 30L * 1024L * 1024L
    private const val cacheVersion = 1
    private val cacheMutex = Mutex()
    private var cacheInstance: FileKache? = null

    actual suspend fun read(cacheKey: String): ApiKacheEntry? {
        val file = cache().get(cacheKey) ?: return null
        return runCatching {
            decodeCacheEntry(Paths.get(file).readText(StandardCharsets.UTF_8))
        }.getOrElse {
            remove(cacheKey)
            null
        }
    }

    actual suspend fun write(cacheKey: String, entry: ApiKacheEntry) {
        cache().put(cacheKey) { file ->
            runCatching {
                Paths.get(file).writeText(encodeCacheEntry(entry), StandardCharsets.UTF_8)
                true
            }.getOrDefault(false)
        }
    }

    actual suspend fun remove(cacheKey: String) {
        cache().remove(cacheKey)
    }

    private suspend fun cache(): FileKache =
        cacheMutex.withLock {
            cacheInstance ?: FileKache(
                directory = sourceCacheDirectory().toString(),
                maxSize = maxCacheBytes,
            ) {
                strategy = KacheStrategy.LRU
                cacheVersion = SourceResponsePersistentKache.cacheVersion
            }.also { cacheInstance = it }
        }

    private fun sourceCacheDirectory(): Path =
        Paths.get(
            System.getProperty("user.home"),
            "Library",
            "Application Support",
            "Nuvio",
            "cache",
            "source-responses",
        ).apply {
            createDirectories()
        }

    private fun encodeCacheEntry(entry: ApiKacheEntry): String =
        "${entry.cachedAtMs}\n${entry.body}"

    private fun decodeCacheEntry(raw: String): ApiKacheEntry? {
        val separatorIndex = raw.indexOf('\n')
        if (separatorIndex <= 0) return null
        val cachedAtMs = raw.substring(0, separatorIndex).toLongOrNull() ?: return null
        return ApiKacheEntry(
            cachedAtMs = cachedAtMs,
            body = raw.substring(separatorIndex + 1),
        )
    }
}
