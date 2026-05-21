package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.FilterQuality

internal actual val nuvioImageFilterQuality: FilterQuality =
    when (nuvioDesktopImageSamplingMode) {
        NuvioDesktopImageSamplingMode.Chrome,
        NuvioDesktopImageSamplingMode.SkiaLinearNearestMip,
        NuvioDesktopImageSamplingMode.SkiaLinearLinearMip -> FilterQuality.Medium
        else -> FilterQuality.High
    }

internal actual val nuvioPreferredTmdbImageSize: String? = "original"

internal actual val nuvioPreferredMetahubImageSize: String? = "large"

internal actual val nuvioPreferredMetahubEpisodeImageSize: String? = "original"

internal actual val nuvioImageDecodeSizeMultiplier: Float = 2f
