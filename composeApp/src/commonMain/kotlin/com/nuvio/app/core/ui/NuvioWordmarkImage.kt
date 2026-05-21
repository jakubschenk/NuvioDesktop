package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
internal fun NuvioWordmarkImage(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    PlatformNuvioWordmarkImage(
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
internal expect fun PlatformNuvioWordmarkImage(
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
)
