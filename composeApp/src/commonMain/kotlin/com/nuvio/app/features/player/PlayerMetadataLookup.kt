package com.nuvio.app.features.player

import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaVideo

internal fun String?.isSeriesLikePlayerType(): Boolean =
    equals("series", ignoreCase = true) || equals("tv", ignoreCase = true)

internal fun List<MetaVideo>.hasEpisodeMetadata(): Boolean =
    any { video -> video.season != null || video.episode != null }

internal fun playerMetaLookupTypes(parentMetaType: String, contentType: String?): List<String> =
    buildList {
        add(parentMetaType)
        contentType?.takeIf { type -> type.isNotBlank() }?.let(::add)
        if (parentMetaType.isSeriesLikePlayerType() || contentType.isSeriesLikePlayerType()) {
            add("series")
        }
    }.distinctBy { type -> type.lowercase() }

internal fun peekPlayerMetaVideos(parentMetaType: String, contentType: String?, parentMetaId: String): List<MetaVideo> {
    for (type in playerMetaLookupTypes(parentMetaType, contentType)) {
        val videos = MetaDetailsRepository.peek(type, parentMetaId)?.videos.orEmpty()
        if (videos.isNotEmpty()) return videos
    }
    return emptyList()
}

internal suspend fun fetchPlayerMetaVideos(
    parentMetaType: String,
    contentType: String?,
    parentMetaId: String,
): List<MetaVideo> {
    val cached = peekPlayerMetaVideos(parentMetaType, contentType, parentMetaId)
    if (cached.isNotEmpty()) return cached

    for (type in playerMetaLookupTypes(parentMetaType, contentType)) {
        val videos = MetaDetailsRepository.fetch(type, parentMetaId)?.videos.orEmpty()
        if (videos.isNotEmpty()) return videos
    }
    return emptyList()
}
