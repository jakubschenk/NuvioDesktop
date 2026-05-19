package com.nuvio.app.features.home.components

internal data class HomeHeroLogoDumpItem(
    val index: Int,
    val id: String,
    val type: String,
    val name: String,
    val originalLogoUrl: String,
    val heroLogoUrl: String,
)

internal expect suspend fun dumpHomeHeroLogos(items: List<HomeHeroLogoDumpItem>)
