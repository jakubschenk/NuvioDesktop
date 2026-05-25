package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier

expect fun Modifier.desktopClickablePointer(): Modifier

fun Modifier.desktopClickablePointer(enabled: Boolean): Modifier =
    if (enabled) desktopClickablePointer() else this
