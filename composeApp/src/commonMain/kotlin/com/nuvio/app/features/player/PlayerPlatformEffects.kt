package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

interface PlayerGestureController {
    fun currentBrightness(): Float?
    fun setBrightness(level: Float): Float?
    fun currentVolume(): PlayerAudioLevel?
    fun setVolume(level: Float): PlayerAudioLevel?
}

interface PlayerFullscreenController {
    val isFullscreenSupported: Boolean
    val isFullscreen: Boolean
    fun toggleFullscreen()
}

data class PlayerKeyboardShortcutHandlers(
    val toggleFullscreen: () -> Unit,
    val togglePlayback: () -> Unit,
    val seekForward: () -> Unit,
    val seekBackward: () -> Unit,
    val volumeUp: () -> Unit,
    val volumeDown: () -> Unit,
    val toggleMute: () -> Unit,
    val cycleResizeMode: () -> Unit,
    val playNextEpisode: () -> Unit,
    val openAudioTracks: () -> Unit,
    val openSubtitleTracks: () -> Unit,
    val openSources: () -> Unit,
    val openEpisodes: () -> Unit,
)

data class PlayerAudioLevel(
    val fraction: Float,
    val isMuted: Boolean,
)

@Composable
expect fun LockPlayerToLandscape()

@Composable
expect fun EnterImmersivePlayerMode(keepScreenAwake: Boolean)

@Composable
expect fun ManagePlayerPictureInPicture(
    isPlaying: Boolean,
    playerSize: IntSize,
)

@Composable
expect fun ManagePlayerCursorVisibility(visible: Boolean)

@Composable
expect fun rememberPlayerGestureController(): PlayerGestureController?

@Composable
expect fun rememberPlayerFullscreenController(): PlayerFullscreenController

@Composable
expect fun ManageFullscreenKeyboardShortcuts(isHomeRouteActive: Boolean)

@Composable
expect fun BindPlayerKeyboardShortcuts(
    enabled: Boolean,
    handlers: PlayerKeyboardShortcutHandlers,
)

@Composable
expect fun PlayerOverlayLayer(
    layoutSize: IntSize,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
)

expect val usesNativePlayerChrome: Boolean

expect val usesAnimatedPlayerChrome: Boolean

expect val usesPlatformPlayerKeyboardShortcuts: Boolean
