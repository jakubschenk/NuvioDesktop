package com.nuvio.app.features.search

import com.nuvio.app.features.addons.ManagedAddon

internal fun buildAddonCatalogRefreshKey(addons: List<ManagedAddon>): List<String> =
    addons.mapNotNull { addon ->
        val manifest = addon.manifest ?: return@mapNotNull null
        buildString {
            append(manifest.transportUrl)
            append(':')
            append(manifest.catalogs.joinToString(separator = ",") { catalog ->
                val extra = catalog.extra.joinToString(separator = "&") { property ->
                    buildString {
                        append(property.name)
                        append(':')
                        append(property.isRequired)
                        append(':')
                        append(property.options.joinToString(separator = "|"))
                    }
                }
                "${catalog.type}:${catalog.id}:$extra"
            })
        }
    }
