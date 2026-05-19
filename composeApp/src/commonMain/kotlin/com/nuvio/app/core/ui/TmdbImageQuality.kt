package com.nuvio.app.core.ui

import coil3.request.ImageRequest

internal expect val nuvioPreferredTmdbImageSize: String?
internal expect val nuvioPreferredMetahubImageSize: String?
internal expect val nuvioPreferredMetahubEpisodeImageSize: String?
internal expect val nuvioImageDecodeSizeMultiplier: Float

private const val TmdbImageBasePath = "image.tmdb.org/t/p/"
private const val TmdbImageSizeCacheKey = "nuvio_tmdb_image_size"
private const val MetahubImageBasePath = "images.metahub.space/"
private const val MetahubEpisodeImageBasePath = "episodes.metahub.space/"
private const val MetahubImageSizeCacheKey = "nuvio_metahub_image_size"
private const val MetahubEpisodeImageSizeCacheKey = "nuvio_metahub_episode_image_size"
private val MetahubImageKinds = setOf("poster", "background", "logo")
private val MetahubEpisodeImageSizePattern = Regex("w\\d+|original")

internal fun Any?.upgradeTmdbImageModelQuality(): Any? = when (this) {
    is String -> upgradeTmdbImageQuality()
    is ImageRequest -> upgradeTmdbImageQuality()
    else -> this
}

internal fun String.upgradeTmdbImageQuality(): String {
    var result = this
    nuvioPreferredTmdbImageSize?.let { preferredSize ->
        result = result.withTmdbImageSize(preferredSize)
    }
    nuvioPreferredMetahubImageSize?.let { preferredSize ->
        result = result.withMetahubImageSize(preferredSize)
    }
    nuvioPreferredMetahubEpisodeImageSize?.let { preferredSize ->
        result = result.withMetahubEpisodeImageSize(preferredSize)
    }
    return result
}

internal fun String.withTmdbImageSize(size: String): String {
    val basePathStart = indexOf(TmdbImageBasePath, ignoreCase = true)
    if (basePathStart == -1) return this

    val sizeStart = basePathStart + TmdbImageBasePath.length
    val sizeEnd = indexOf('/', startIndex = sizeStart)
    if (sizeEnd == -1) return this
    if (substring(sizeStart, sizeEnd) == size) return this

    return replaceRange(sizeStart, sizeEnd, size)
}

internal fun String.withMetahubImageSize(size: String): String {
    val basePathStart = indexOf(MetahubImageBasePath, ignoreCase = true)
    if (basePathStart == -1) return this

    val kindStart = basePathStart + MetahubImageBasePath.length
    val kindEnd = indexOf('/', startIndex = kindStart)
    if (kindEnd == -1) return this
    val kind = substring(kindStart, kindEnd).lowercase()
    if (kind !in MetahubImageKinds) return this

    val sizeStart = kindEnd + 1
    val sizeEnd = indexOf('/', startIndex = sizeStart)
    if (sizeEnd == -1) return this
    if (substring(sizeStart, sizeEnd).equals(size, ignoreCase = true)) return this

    return replaceRange(sizeStart, sizeEnd, size)
}

internal fun String.withMetahubEpisodeImageSize(size: String): String {
    val basePathStart = indexOf(MetahubEpisodeImageBasePath, ignoreCase = true)
    if (basePathStart == -1) return this

    val queryStart = indexOf('?', startIndex = basePathStart).takeIf { it != -1 }
    val pathEnd = queryStart ?: length
    val fileStart = lastIndexOf('/', startIndex = pathEnd - 1)
    if (fileStart < basePathStart + MetahubEpisodeImageBasePath.length) return this

    val fileName = substring(fileStart + 1, pathEnd)
    val extensionStart = fileName.lastIndexOf('.')
    if (extensionStart <= 0) return this
    val currentSize = fileName.substring(0, extensionStart)
    if (!MetahubEpisodeImageSizePattern.matches(currentSize)) return this
    if (currentSize.equals(size, ignoreCase = true)) return this

    return replaceRange(fileStart + 1, fileStart + 1 + currentSize.length, size)
}

private fun ImageRequest.upgradeTmdbImageQuality(): ImageRequest {
    val sourceData = data
    if (sourceData !is String) return this

    val upgradedData = sourceData.upgradeTmdbImageQuality()
    if (upgradedData == sourceData) return this

    val preferredSize = nuvioPreferredTmdbImageSize
    val preferredMetahubImageSize = nuvioPreferredMetahubImageSize
    val preferredMetahubEpisodeImageSize = nuvioPreferredMetahubEpisodeImageSize
    val builder = newBuilder()
        .data(upgradedData)

    val upgradedMemoryCacheKey = memoryCacheKey?.upgradeTmdbImageQuality()
    if (upgradedMemoryCacheKey != memoryCacheKey) {
        builder.memoryCacheKey(upgradedMemoryCacheKey)
    }
    if (preferredSize != null) {
        builder.memoryCacheKeyExtra(TmdbImageSizeCacheKey, preferredSize)
    }
    if (preferredMetahubImageSize != null) {
        builder.memoryCacheKeyExtra(MetahubImageSizeCacheKey, preferredMetahubImageSize)
    }
    if (preferredMetahubEpisodeImageSize != null) {
        builder.memoryCacheKeyExtra(MetahubEpisodeImageSizeCacheKey, preferredMetahubEpisodeImageSize)
    }

    val upgradedDiskCacheKey = diskCacheKey?.upgradeTmdbImageQuality()
    if (upgradedDiskCacheKey != diskCacheKey) {
        builder.diskCacheKey(upgradedDiskCacheKey)
    }

    return builder.build()
}
