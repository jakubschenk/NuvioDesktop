package com.nuvio.app.core.ui

import coil3.request.ImageRequest

internal expect val nuvioPreferredTmdbImageSize: String?
internal expect val nuvioImageDecodeSizeMultiplier: Float

private const val TmdbImageBasePath = "image.tmdb.org/t/p/"
private const val TmdbImageSizeCacheKey = "nuvio_tmdb_image_size"

internal fun Any?.upgradeTmdbImageModelQuality(): Any? = when (this) {
    is String -> upgradeTmdbImageQuality()
    is ImageRequest -> upgradeTmdbImageQuality()
    else -> this
}

internal fun String.upgradeTmdbImageQuality(): String {
    val preferredSize = nuvioPreferredTmdbImageSize ?: return this
    return withTmdbImageSize(preferredSize)
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

private fun ImageRequest.upgradeTmdbImageQuality(): ImageRequest {
    val sourceData = data
    if (sourceData !is String) return this

    val upgradedData = sourceData.upgradeTmdbImageQuality()
    if (upgradedData == sourceData) return this

    val preferredSize = nuvioPreferredTmdbImageSize
    val builder = newBuilder()
        .data(upgradedData)

    val upgradedMemoryCacheKey = memoryCacheKey?.upgradeTmdbImageQuality()
    if (upgradedMemoryCacheKey != memoryCacheKey) {
        builder.memoryCacheKey(upgradedMemoryCacheKey)
    }
    if (preferredSize != null) {
        builder.memoryCacheKeyExtra(TmdbImageSizeCacheKey, preferredSize)
    }

    val upgradedDiskCacheKey = diskCacheKey?.upgradeTmdbImageQuality()
    if (upgradedDiskCacheKey != diskCacheKey) {
        builder.diskCacheKey(upgradedDiskCacheKey)
    }

    return builder.build()
}
