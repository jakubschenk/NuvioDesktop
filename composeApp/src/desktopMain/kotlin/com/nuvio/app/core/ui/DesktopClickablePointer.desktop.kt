package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

actual fun Modifier.desktopClickablePointer(): Modifier =
    pointerHoverIcon(PointerIcon.Hand)
