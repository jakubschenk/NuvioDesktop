package com.nuvio.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_wordmark
import org.jetbrains.compose.resources.painterResource

@Composable
internal actual fun PlatformNuvioWordmarkImage(
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    Image(
        painter = painterResource(Res.drawable.app_logo_wordmark),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
