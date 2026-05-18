package com.nuvio.app.features.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.core.ui.desktopClickablePointer
import com.nuvio.app.core.ui.nuvioTypeScale
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

@Composable
internal fun PlayerControlsShell(
    title: String,
    streamTitle: String,
    providerName: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    isLocked: Boolean,
    isFullscreenSupported: Boolean,
    isFullscreen: Boolean,
    onLockToggle: () -> Unit,
    onFullscreenClick: () -> Unit,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onResizeModeClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onVolumeClick: (() -> Unit)? = null,
    isVolumeMuted: Boolean = false,
    onNextEpisodeClick: (() -> Unit)? = null,
    onSourcesClick: (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
    onSubmitIntroClick: (() -> Unit)? = null,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    horizontalSafePadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalSafePadding),
        ) {
            PlayerHeader(
                title = title,
                streamTitle = streamTitle,
                providerName = providerName,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                metrics = metrics,
                isFullscreenSupported = isFullscreenSupported,
                isFullscreen = isFullscreen,
                onSubmitIntroClick = onSubmitIntroClick,
                onFullscreenClick = onFullscreenClick,
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Top))
                    .padding(
                        start = metrics.horizontalPadding,
                        end = metrics.horizontalPadding,
                        top = metrics.verticalPadding / 4,
                    ),
            )

            ProgressControls(
                playbackSnapshot = playbackSnapshot,
                displayedPositionMs = displayedPositionMs,
                metrics = metrics,
                resizeMode = resizeMode,
                isLocked = isLocked,
                onScrubChange = onScrubChange,
                onScrubFinished = onScrubFinished,
                onTogglePlayback = onTogglePlayback,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onResizeModeClick = onResizeModeClick,
                onSpeedClick = onSpeedClick,
                onSubtitleClick = onSubtitleClick,
                onAudioClick = onAudioClick,
                onVolumeClick = onVolumeClick,
                isVolumeMuted = isVolumeMuted,
                onNextEpisodeClick = onNextEpisodeClick,
                onLockToggle = onLockToggle,
                onSourcesClick = onSourcesClick,
                onEpisodesClick = onEpisodesClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = metrics.horizontalPadding)
                    .padding(bottom = metrics.sliderBottomOffset),
            )
        }
    }
}

@Composable
private fun PlayerHeader(
    title: String,
    streamTitle: String,
    providerName: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    metrics: PlayerLayoutMetrics,
    isFullscreenSupported: Boolean,
    isFullscreen: Boolean,
    onSubmitIntroClick: (() -> Unit)?,
    onFullscreenClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeScale = MaterialTheme.nuvioTypeScale
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                NuvioBackButton(
                    onClick = onBack,
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = Color.White,
                    buttonSize = metrics.headerIconSize + 16.dp,
                    iconSize = metrics.headerIconSize,
                    contentDescription = stringResource(Res.string.compose_player_close),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = typeScale.titleLg.copy(
                            fontSize = metrics.titleSize,
                            lineHeight = metrics.titleSize * 1.16f,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (seasonNumber != null && episodeNumber != null && !episodeTitle.isNullOrBlank()) {
                        Text(
                            text = stringResource(
                                Res.string.compose_player_episode_title_format,
                                seasonNumber,
                                episodeNumber,
                                episodeTitle,
                            ),
                            style = typeScale.bodyMd.copy(
                                fontSize = metrics.episodeInfoSize,
                                lineHeight = metrics.episodeInfoSize * 1.3f,
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = streamTitle,
                            style = typeScale.labelSm.copy(
                                fontSize = metrics.metadataSize,
                                lineHeight = metrics.metadataSize * 1.25f,
                            ),
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = providerName,
                            style = typeScale.labelSm.copy(
                                fontSize = metrics.metadataSize,
                                lineHeight = metrics.metadataSize * 1.25f,
                                fontStyle = FontStyle.Italic,
                            ),
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isFullscreenSupported) {
                    PlayerHeaderIconButton(
                        icon = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = if (isFullscreen) {
                            stringResource(Res.string.compose_player_exit_fullscreen)
                        } else {
                            stringResource(Res.string.compose_player_enter_fullscreen)
                        },
                        buttonSize = metrics.headerIconSize + 16.dp,
                        iconSize = metrics.headerIconSize,
                        onClick = onFullscreenClick,
                    )
                }
                if (onSubmitIntroClick != null) {
                    PlayerHeaderIconButton(
                        icon = Icons.Rounded.Flag,
                        contentDescription = "Submit Intro",
                        buttonSize = metrics.headerIconSize + 16.dp,
                        iconSize = metrics.headerIconSize,
                        onClick = onSubmitIntroClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .desktopClickablePointer()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ProgressControls(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    isLocked: Boolean,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onResizeModeClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onVolumeClick: (() -> Unit)? = null,
    isVolumeMuted: Boolean = false,
    onNextEpisodeClick: (() -> Unit)? = null,
    onLockToggle: () -> Unit,
    onSourcesClick: (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val durationMs = playbackSnapshot.durationMs.coerceAtLeast(1L)
    val aspectRatioPainter = appIconPainter(AppIconResource.PlayerAspectRatio)
    val subtitlesPainter = appIconPainter(AppIconResource.PlayerSubtitles)
    val audioPainter = appIconPainter(AppIconResource.PlayerAudioFilled)

    Column(modifier = modifier) {
        PlayerSeekBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.sliderTouchHeight),
            positionMs = displayedPositionMs,
            durationMs = durationMs,
            idleScaleY = metrics.sliderScaleY,
            onScrubChange = onScrubChange,
            onScrubFinished = onScrubFinished,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimePill(text = formatPlaybackTime(displayedPositionMs), fontSize = metrics.timeSize)
            TimePill(text = formatPlaybackTime(durationMs), fontSize = metrics.timeSize)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerControlCluster {
                PlayerToolbarPlayPauseButton(
                    isPlaying = playbackSnapshot.isPlaying,
                    isBuffering = playbackSnapshot.isLoading,
                    onClick = onTogglePlayback,
                )
                PlayerToolbarIconButton(
                    icon = Icons.Rounded.Replay10,
                    contentDescription = stringResource(Res.string.compose_player_seek_back_10),
                    onClick = onSeekBack,
                )
                PlayerToolbarIconButton(
                    icon = Icons.Rounded.Forward10,
                    contentDescription = stringResource(Res.string.compose_player_seek_forward_10),
                    onClick = onSeekForward,
                )
                if (onNextEpisodeClick != null) {
                    PlayerToolbarIconButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(Res.string.player_next_episode),
                        onClick = onNextEpisodeClick,
                    )
                }
                if (onVolumeClick != null) {
                    PlayerToolbarIconButton(
                        icon = if (isVolumeMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = stringResource(Res.string.compose_player_audio),
                        onClick = onVolumeClick,
                    )
                }
            }

            PlayerControlCluster {
                PlayerToolbarIconButton(
                    painter = subtitlesPainter,
                    contentDescription = stringResource(Res.string.compose_player_subtitles),
                    onClick = onSubtitleClick,
                )
                PlayerToolbarIconButton(
                    painter = audioPainter,
                    contentDescription = stringResource(Res.string.compose_player_audio),
                    onClick = onAudioClick,
                )
                PlayerToolbarTextButton(
                    label = formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed),
                    contentDescription = formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed),
                    onClick = onSpeedClick,
                )
                PlayerToolbarIconButton(
                    painter = aspectRatioPainter,
                    contentDescription = stringResource(resizeMode.labelRes),
                    onClick = onResizeModeClick,
                )
                if (onSourcesClick != null) {
                    PlayerToolbarIconButton(
                        icon = Icons.Rounded.SwapHoriz,
                        contentDescription = stringResource(Res.string.compose_player_sources),
                        onClick = onSourcesClick,
                    )
                }
                if (onEpisodesClick != null) {
                    PlayerToolbarIconButton(
                        icon = Icons.Rounded.VideoLibrary,
                        contentDescription = stringResource(Res.string.compose_player_episodes),
                        onClick = onEpisodesClick,
                    )
                }
                PlayerToolbarIconButton(
                    icon = if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    contentDescription = if (isLocked) {
                        stringResource(Res.string.compose_player_unlock_controls)
                    } else {
                        stringResource(Res.string.compose_player_lock_controls)
                    },
                    onClick = onLockToggle,
                    isActive = isLocked,
                )
            }
        }
    }
}

@Composable
private fun PlayerControlCluster(
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.46f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.18f),
            shape = RoundedCornerShape(22.dp),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun PlayerToolbarPlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
) {
    val playPausePainter = appIconPainter(
        if (isPlaying) AppIconResource.PlayerPause else AppIconResource.PlayerPlay,
    )

    PlayerToolbarIconButton(
        painter = if (isBuffering) null else playPausePainter,
        contentDescription = if (isPlaying) {
            stringResource(Res.string.compose_action_pause)
        } else {
            stringResource(Res.string.detail_btn_play)
        },
        onClick = onClick,
        buttonSize = 46.dp,
        iconSize = 24.dp,
        customContent = if (isBuffering) {
            {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun PlayerToolbarTextButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    PlayerToolbarIconButton(
        text = label,
        contentDescription = contentDescription,
        onClick = onClick,
        buttonSize = 46.dp,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlayerToolbarIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    text: String? = null,
    buttonSize: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    isActive: Boolean = false,
    customContent: (@Composable () -> Unit)? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val backgroundAlpha by animateFloatAsState(
        targetValue = when {
            isActive -> 0.24f
            isHovered -> 0.16f
            else -> 0.001f
        },
        label = "player_toolbar_button_bg",
    )

    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = backgroundAlpha))
            .desktopClickablePointer()
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            customContent != null -> customContent()
            painter != null -> Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )

            text != null -> Text(
                text = text,
                style = MaterialTheme.nuvioTypeScale.labelSm.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    idleScaleY: Float,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var isHovered by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val onScrubChangeState = rememberUpdatedState(onScrubChange)
    val onScrubFinishedState = rememberUpdatedState(onScrubFinished)
    val coercedDurationMs = durationMs.coerceAtLeast(1L)
    val coercedPositionMs = positionMs.coerceIn(0L, coercedDurationMs)
    val sliderScaleY by animateFloatAsState(
        targetValue = if (isHovered || isFocused) 1f else idleScaleY,
        label = "player_seek_slider_scale",
    )

    fun positionToMs(x: Float, width: Float): Long {
        if (width <= 0f) return coercedPositionMs
        return ((x / width).coerceIn(0f, 1f) * coercedDurationMs).roundToLong()
    }

    fun commitSeek(targetMs: Long) {
        val coercedTarget = targetMs.coerceIn(0L, coercedDurationMs)
        onScrubChangeState.value(coercedTarget)
        onScrubFinishedState.value(coercedTarget)
    }

    Box(
        modifier = modifier
            .desktopClickablePointer()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        commitSeek(coercedPositionMs - 5_000L)
                        true
                    }

                    Key.DirectionRight -> {
                        commitSeek(coercedPositionMs + 5_000L)
                        true
                    }

                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .pointerInput(coercedDurationMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    focusRequester.requestFocus()
                    val width = size.width.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
                    var latestTargetMs = positionToMs(down.position.x, width)
                    onScrubChangeState.value(latestTargetMs)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        latestTargetMs = positionToMs(change.position.x, width)
                        onScrubChangeState.value(latestTargetMs)
                        change.consume()
                    }

                    onScrubFinishedState.value(latestTargetMs)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleY = sliderScaleY),
            value = coercedPositionMs.toFloat(),
            onValueChange = {},
            onValueChangeFinished = {},
            valueRange = 0f..coercedDurationMs.toFloat(),
        )
    }
}

@Composable
internal fun LockedPlayerOverlay(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    horizontalSafePadding: androidx.compose.ui.unit.Dp,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = playbackSnapshot.durationMs.coerceAtLeast(1L)
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.28f),
        disabledThumbColor = Color.White,
        disabledActiveTrackColor = Color.White,
        disabledInactiveTrackColor = Color.White.copy(alpha = 0.28f),
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .desktopClickablePointer()
                    .clickable(onClick = onUnlock),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = stringResource(Res.string.compose_player_unlock_controls),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.compose_player_tap_to_unlock),
                style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.92f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = horizontalSafePadding + metrics.horizontalPadding)
                .padding(bottom = metrics.sliderBottomOffset),
        ) {
            Slider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.sliderTouchHeight)
                    .graphicsLayer(scaleY = metrics.sliderScaleY),
                value = displayedPositionMs.coerceIn(0L, durationMs).toFloat(),
                onValueChange = {},
                onValueChangeFinished = {},
                valueRange = 0f..durationMs.toFloat(),
                enabled = false,
                colors = sliderColors,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimePill(text = formatPlaybackTime(displayedPositionMs), fontSize = metrics.timeSize)
                TimePill(text = formatPlaybackTime(durationMs), fontSize = metrics.timeSize)
            }
        }
    }
}

@Composable
private fun TimePill(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.nuvioTypeScale.labelSm.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.25f,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White,
        )
    }
}
