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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.core.ui.desktopClickablePointer
import com.nuvio.app.core.ui.nuvioTypeScale
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val PlayerSeekHoverThumbSize = 10.dp
private val PlayerSeekTimeTextWidth = 84.dp
private val PlayerSeekTimeHorizontalGap = 6.dp
private val PlayerToolbarButtonSize = 44.dp
private val PlayerToolbarIconSize = 23.dp
private val PlayerVolumeSliderWidth = 112.dp
private val PlayerVolumeSliderTouchHeight = 34.dp
private const val PlayerVolumeSliderIdleScaleY = 0.72f
private const val PlayerVolumeKeyboardStep = 0.05f
private const val PlayerVolumeSliderSteps = 19

private fun PlayerPlaybackSnapshot.displayPositionAt(
    snapshotEpochMs: Long,
    nowEpochMs: Long,
): Long {
    if (!isPlaying || durationMs <= 0L) {
        return positionMs.coerceAtLeast(0L)
    }
    val elapsedMs = (nowEpochMs - snapshotEpochMs).coerceAtLeast(0L)
    val interpolated = positionMs + (elapsedMs * playbackSpeed).roundToLong()
    return interpolated.coerceIn(0L, durationMs)
}

@Composable
private fun rememberLiveDisplayedPositionMs(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    snapshotEpochMs: Long,
    animateDisplayedPosition: Boolean,
): Long {
    var frameEpochMs by remember { mutableStateOf(PlayerWallClock.nowEpochMs()) }

    LaunchedEffect(
        animateDisplayedPosition,
        playbackSnapshot.isPlaying,
        playbackSnapshot.durationMs,
        playbackSnapshot.playbackSpeed,
        snapshotEpochMs,
    ) {
        if (!animateDisplayedPosition || !playbackSnapshot.isPlaying || playbackSnapshot.durationMs <= 0L) {
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { }
            frameEpochMs = PlayerWallClock.nowEpochMs()
        }
    }

    return if (animateDisplayedPosition) {
        playbackSnapshot.displayPositionAt(
            snapshotEpochMs = snapshotEpochMs,
            nowEpochMs = frameEpochMs,
        )
    } else {
        displayedPositionMs
    }
}

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
    snapshotEpochMs: Long,
    animateDisplayedPosition: Boolean,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    isFullscreenSupported: Boolean,
    isFullscreen: Boolean,
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
    onVolumeChange: ((Float) -> Unit)? = null,
    volumeLevel: Float = 1f,
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
    val liveDisplayedPositionMs = rememberLiveDisplayedPositionMs(
        playbackSnapshot = playbackSnapshot,
        displayedPositionMs = displayedPositionMs,
        snapshotEpochMs = snapshotEpochMs,
        animateDisplayedPosition = animateDisplayedPosition,
    )

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
                .height(320.dp)
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
                metrics = metrics,
                displayedPositionMs = liveDisplayedPositionMs,
                durationMs = playbackSnapshot.durationMs,
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Top))
                    .padding(
                        start = metrics.horizontalPadding,
                        end = metrics.horizontalPadding,
                        top = metrics.verticalPadding,
                    ),
            )

            ProgressControls(
                title = title,
                streamTitle = streamTitle,
                providerName = providerName,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                playbackSnapshot = playbackSnapshot,
                displayedPositionMs = liveDisplayedPositionMs,
                metrics = metrics,
                resizeMode = resizeMode,
                isFullscreenSupported = isFullscreenSupported,
                isFullscreen = isFullscreen,
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
                onVolumeChange = onVolumeChange,
                volumeLevel = volumeLevel,
                isVolumeMuted = isVolumeMuted,
                onNextEpisodeClick = onNextEpisodeClick,
                onFullscreenClick = onFullscreenClick,
                onSourcesClick = onSourcesClick,
                onEpisodesClick = onEpisodesClick,
                onSubmitIntroClick = onSubmitIntroClick,
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
    metrics: PlayerLayoutMetrics,
    displayedPositionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowEpochMs by remember { mutableStateOf(PlayerWallClock.nowEpochMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMs = PlayerWallClock.nowEpochMs()
            delay(1_000L)
        }
    }
    val clockText = remember(nowEpochMs) { PlayerWallClock.formatTime(nowEpochMs) }
    val endTimeText = remember(nowEpochMs, displayedPositionMs, durationMs) {
        val remainingMs = durationMs - displayedPositionMs
        remainingMs
            .takeIf { durationMs > 0L && it > 0L }
            ?.let { remaining -> PlayerWallClock.formatTime(nowEpochMs + remaining) }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
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
        PlayerClockReadout(
            currentTimeText = clockText,
            endTimeText = endTimeText,
            metrics = metrics,
        )
    }
}

@Composable
private fun PlayerClockReadout(
    currentTimeText: String,
    endTimeText: String?,
    metrics: PlayerLayoutMetrics,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = currentTimeText,
            style = MaterialTheme.nuvioTypeScale.labelSm.copy(
                fontSize = metrics.metadataSize * 1.4f,
                lineHeight = metrics.metadataSize * 1.55f,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
            maxLines = 1,
        )
        if (endTimeText != null) {
            Text(
                text = "Ends $endTimeText",
                style = MaterialTheme.nuvioTypeScale.labelSm.copy(
                    fontSize = metrics.metadataSize,
                    lineHeight = metrics.metadataSize * 1.2f,
                ),
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProgressControls(
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
    isFullscreenSupported: Boolean,
    isFullscreen: Boolean,
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
    onVolumeChange: ((Float) -> Unit)? = null,
    volumeLevel: Float = 1f,
    isVolumeMuted: Boolean = false,
    onNextEpisodeClick: (() -> Unit)? = null,
    onFullscreenClick: () -> Unit,
    onSourcesClick: (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
    onSubmitIntroClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val durationMs = playbackSnapshot.durationMs.coerceAtLeast(1L)
    val aspectRatioPainter = appIconPainter(AppIconResource.PlayerAspectRatio)
    val subtitlesPainter = appIconPainter(AppIconResource.PlayerSubtitles)
    val audioPainter = appIconPainter(AppIconResource.PlayerAudioFilled)

    Column(modifier = modifier) {
        PlayerBottomMetadata(
            title = title,
            streamTitle = streamTitle,
            providerName = providerName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            metrics = metrics,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(PlayerSeekTimeHorizontalGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerTimeText(
                text = formatPlaybackTimeFixedHours(displayedPositionMs),
                fontSize = metrics.timeSize,
                modifier = Modifier.width(PlayerSeekTimeTextWidth),
                textAlign = TextAlign.Start,
            )
            PlayerSeekBar(
                modifier = Modifier
                    .weight(1f)
                    .height(metrics.sliderTouchHeight),
                positionMs = displayedPositionMs,
                durationMs = durationMs,
                idleScaleY = metrics.sliderScaleY,
                onScrubChange = onScrubChange,
                onScrubFinished = onScrubFinished,
            )
            PlayerTimeText(
                text = formatPlaybackTimeFixedHours(durationMs),
                fontSize = metrics.timeSize,
                modifier = Modifier.width(PlayerSeekTimeTextWidth),
                textAlign = TextAlign.End,
            )
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
                if (onVolumeClick != null || onVolumeChange != null) {
                    PlayerVolumeControl(
                        volumeLevel = volumeLevel,
                        isMuted = isVolumeMuted,
                        onMuteClick = onVolumeClick,
                        onVolumeChange = onVolumeChange,
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
                if (onSubmitIntroClick != null) {
                    PlayerToolbarIconButton(
                        icon = Icons.Rounded.Flag,
                        contentDescription = "Submit Intro",
                        onClick = onSubmitIntroClick,
                    )
                }
                if (isFullscreenSupported) {
                    PlayerToolbarIconButton(
                        icon = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = if (isFullscreen) {
                            stringResource(Res.string.compose_player_exit_fullscreen)
                        } else {
                            stringResource(Res.string.compose_player_enter_fullscreen)
                        },
                        onClick = onFullscreenClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBottomMetadata(
    title: String,
    streamTitle: String,
    providerName: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    metrics: PlayerLayoutMetrics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.nuvioTypeScale.titleLg.copy(
                fontSize = metrics.titleSize * 0.9f,
                lineHeight = metrics.titleSize,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White,
            maxLines = 1,
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
                style = MaterialTheme.nuvioTypeScale.bodyMd.copy(
                    fontSize = metrics.episodeInfoSize,
                    lineHeight = metrics.episodeInfoSize * 1.2f,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color.White.copy(alpha = 0.88f),
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
                style = MaterialTheme.nuvioTypeScale.labelSm.copy(
                    fontSize = metrics.metadataSize,
                    lineHeight = metrics.metadataSize * 1.2f,
                ),
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = providerName,
                style = MaterialTheme.nuvioTypeScale.labelSm.copy(
                    fontSize = metrics.metadataSize,
                    lineHeight = metrics.metadataSize * 1.2f,
                    fontStyle = FontStyle.Italic,
                ),
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
        buttonSize = PlayerToolbarButtonSize,
        iconSize = PlayerToolbarIconSize,
        customContent = if (isBuffering) {
            {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(PlayerToolbarIconSize),
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
        buttonSize = PlayerToolbarButtonSize,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlayerVolumeControl(
    volumeLevel: Float,
    isMuted: Boolean,
    onMuteClick: (() -> Unit)?,
    onVolumeChange: ((Float) -> Unit)?,
) {
    val focusRequester = remember { FocusRequester() }
    val onVolumeChangeState = rememberUpdatedState(onVolumeChange)
    val volumeEnabled = onVolumeChange != null
    var isHovered by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val coercedVolume = volumeLevel.coerceIn(0f, 1f)
    val sliderScaleY by animateFloatAsState(
        targetValue = if (isHovered || isFocused || isDragging) 1f else PlayerVolumeSliderIdleScaleY,
        label = "player_volume_slider_scale",
    )

    fun commitVolume(value: Float) {
        val snapped = ((value.coerceIn(0f, 1f) / PlayerVolumeKeyboardStep).roundToInt() * PlayerVolumeKeyboardStep)
            .coerceIn(0f, 1f)
        onVolumeChangeState.value?.invoke(snapped)
    }

    Row(
        modifier = Modifier.padding(end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerToolbarIconButton(
            icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
            contentDescription = stringResource(Res.string.compose_player_audio),
            onClick = { onMuteClick?.invoke() },
        )
        Box(
            modifier = Modifier
                .width(PlayerVolumeSliderWidth)
                .height(PlayerVolumeSliderTouchHeight)
                .then(if (volumeEnabled) Modifier.desktopClickablePointer() else Modifier)
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable(enabled = volumeEnabled)
                .onPreviewKeyEvent { event ->
                    if (!volumeEnabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            commitVolume(coercedVolume - PlayerVolumeKeyboardStep)
                            true
                        }

                        Key.DirectionRight -> {
                            commitVolume(coercedVolume + PlayerVolumeKeyboardStep)
                            true
                        }

                        else -> false
                    }
                }
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) {
                    isHovered = false
                }
                .onPointerEvent(PointerEventType.Press) {
                    if (volumeEnabled) {
                        focusRequester.requestFocus()
                        isDragging = true
                    }
                }
                .onPointerEvent(PointerEventType.Release) {
                    isDragging = false
                },
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleY = sliderScaleY),
                value = coercedVolume,
                onValueChange = { value ->
                    isDragging = true
                    commitVolume(value)
                },
                onValueChangeFinished = { isDragging = false },
                valueRange = 0f..1f,
                steps = PlayerVolumeSliderSteps,
                enabled = volumeEnabled,
            )
        }
    }
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
    buttonSize: androidx.compose.ui.unit.Dp = PlayerToolbarButtonSize,
    iconSize: androidx.compose.ui.unit.Dp = PlayerToolbarIconSize,
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
    var isScrubbing by remember { mutableStateOf(false) }
    var seekBarWidthPx by remember { mutableStateOf(0) }
    var hoverFraction by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current
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

    fun updateHoverFraction(x: Float) {
        val width = seekBarWidthPx.takeIf { it > 0 }?.toFloat() ?: return
        hoverFraction = (x / width).coerceIn(0f, 1f)
    }

    fun commitSeek(targetMs: Long) {
        val coercedTarget = targetMs.coerceIn(0L, coercedDurationMs)
        onScrubChangeState.value(coercedTarget)
        onScrubFinishedState.value(coercedTarget)
    }

    Box(
        modifier = modifier
            .desktopClickablePointer()
            .onSizeChanged { size -> seekBarWidthPx = size.width }
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
            .onPointerEvent(PointerEventType.Enter) { event ->
                isHovered = true
                event.changes.firstOrNull()?.position?.x?.let(::updateHoverFraction)
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                event.changes.firstOrNull()?.position?.x?.let(::updateHoverFraction)
            }
            .onPointerEvent(PointerEventType.Exit) {
                isHovered = false
                hoverFraction = null
            }
            .pointerInput(coercedDurationMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    focusRequester.requestFocus()
                    val width = size.width.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
                    isScrubbing = true
                    try {
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
                    } finally {
                        isScrubbing = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val previewFraction = hoverFraction
        val previewThumbOffsetPx = with(density) { (PlayerSeekHoverThumbSize / 2).roundToPx() }
        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleY = sliderScaleY),
            value = coercedPositionMs.toFloat(),
            onValueChange = {},
            onValueChangeFinished = {},
            valueRange = 0f..coercedDurationMs.toFloat(),
        )
        if (previewFraction != null && !isScrubbing && seekBarWidthPx > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            x = (seekBarWidthPx * previewFraction).roundToInt() - previewThumbOffsetPx,
                            y = 0,
                        )
                    }
                    .size(PlayerSeekHoverThumbSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.42f)),
            )
        }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(PlayerSeekTimeHorizontalGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerTimeText(
                    text = formatPlaybackTimeFixedHours(displayedPositionMs),
                    fontSize = metrics.timeSize,
                    modifier = Modifier.width(PlayerSeekTimeTextWidth),
                    textAlign = TextAlign.Start,
                )
                Slider(
                    modifier = Modifier
                        .weight(1f)
                        .height(metrics.sliderTouchHeight)
                        .graphicsLayer(scaleY = metrics.sliderScaleY),
                    value = displayedPositionMs.coerceIn(0L, durationMs).toFloat(),
                    onValueChange = {},
                    onValueChangeFinished = {},
                    valueRange = 0f..durationMs.toFloat(),
                    enabled = false,
                    colors = sliderColors,
                )
                PlayerTimeText(
                    text = formatPlaybackTimeFixedHours(durationMs),
                    fontSize = metrics.timeSize,
                    modifier = Modifier.width(PlayerSeekTimeTextWidth),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun PlayerTimeText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.nuvioTypeScale.labelSm.copy(
            fontSize = fontSize,
            lineHeight = fontSize * 1.25f,
            fontWeight = FontWeight.Medium,
        ),
        color = Color.White.copy(alpha = 0.88f),
        maxLines = 1,
        textAlign = textAlign,
    )
}
