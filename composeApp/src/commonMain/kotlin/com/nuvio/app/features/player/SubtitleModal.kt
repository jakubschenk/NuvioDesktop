package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.desktopClickablePointer
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.addon_title
import nuvio.composeapp.generated.resources.compose_player_built_in
import nuvio.composeapp.generated.resources.compose_player_fetch_subtitles
import nuvio.composeapp.generated.resources.compose_player_none
import nuvio.composeapp.generated.resources.compose_player_style
import nuvio.composeapp.generated.resources.compose_player_subtitles
import nuvio.composeapp.generated.resources.settings_playback_option_forced
import nuvio.composeapp.generated.resources.subtitle_language_unknown
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

private const val SubtitleNoneGroupKey = "__none__"

@Composable
fun SubtitleModal(
    visible: Boolean,
    activeTab: SubtitleTab,
    subtitleTracks: List<SubtitleTrack>,
    selectedSubtitleIndex: Int,
    addonSubtitles: List<AddonSubtitle>,
    selectedAddonSubtitleId: String?,
    isLoadingAddonSubtitles: Boolean,
    subtitleStyle: SubtitleStyleState,
    onTabSelected: (SubtitleTab) -> Unit,
    onBuiltInTrackSelected: (Int) -> Unit,
    onAddonSubtitleSelected: (AddonSubtitle) -> Unit,
    onFetchAddonSubtitles: () -> Unit,
    onStyleChanged: (SubtitleStyleState) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .desktopClickablePointer()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                )
                .background(colorScheme.scrim.copy(alpha = 0.56f)),
            contentAlignment = Alignment.Center,
        ) {
            val maxH = maxHeight
            val isCompact = maxWidth < 360.dp || maxHeight < 640.dp

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(300)) { it / 3 } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(250)) { it / 3 } + fadeOut(tween(250)),
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = if (activeTab == SubtitleTab.Style) 420.dp else 720.dp)
                        .fillMaxWidth(0.9f)
                        .heightIn(max = maxH * 0.95f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colorScheme.surface)
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {},
                        ),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.compose_player_subtitles),
                                color = colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        SubtitleTabBar(
                            activeTab = activeTab,
                            onTabSelected = onTabSelected,
                        )

                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 20.dp),
                        ) {
                            when (activeTab) {
                                SubtitleTab.BuiltIn -> BuiltInSubtitleList(
                                    tracks = subtitleTracks,
                                    selectedIndex = selectedSubtitleIndex,
                                    onTrackSelected = onBuiltInTrackSelected,
                                )
                                SubtitleTab.Addons -> AddonSubtitleList(
                                    addons = addonSubtitles,
                                    selectedId = selectedAddonSubtitleId,
                                    isLoading = isLoadingAddonSubtitles,
                                    onSubtitleSelected = onAddonSubtitleSelected,
                                    onFetch = onFetchAddonSubtitles,
                                )
                                SubtitleTab.Style -> SubtitleStylePanel(
                                    style = subtitleStyle,
                                    isCompact = isCompact,
                                    onStyleChanged = onStyleChanged,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleTabBar(
    activeTab: SubtitleTab,
    onTabSelected: (SubtitleTab) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 70.dp)
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        SubtitleTab.entries.forEach { tab ->
            val isSelected = tab == activeTab
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.92f),
                animationSpec = tween(250),
            )
            val radius by animateDpAsState(
                targetValue = if (isSelected) 10.dp else 40.dp,
                animationSpec = tween(250),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(radius))
                    .background(bgColor)
                    .desktopClickablePointer()
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (tab) {
                        SubtitleTab.BuiltIn -> stringResource(Res.string.compose_player_built_in)
                        SubtitleTab.Addons -> stringResource(Res.string.addon_title)
                        SubtitleTab.Style -> stringResource(Res.string.compose_player_style)
                    },
                    color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun BuiltInSubtitleList(
    tracks: List<SubtitleTrack>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit,
) {
    val groups = remember(tracks) { tracks.toBuiltInSubtitleGroups() }
    val groupKeys = remember(groups) { groups.map { it.key } }
    val selectedTrackGroupKey = remember(tracks, selectedIndex) {
        tracks.firstOrNull { it.index == selectedIndex }?.languageGroupKey() ?: SubtitleNoneGroupKey
    }
    var selectedGroupKey by remember { mutableStateOf(selectedTrackGroupKey) }

    LaunchedEffect(selectedTrackGroupKey, groupKeys) {
        selectedGroupKey = when {
            selectedTrackGroupKey in groupKeys -> selectedTrackGroupKey
            groupKeys.isNotEmpty() -> groupKeys.first()
            else -> SubtitleNoneGroupKey
        }
    }

    val selectedGroup = groups.firstOrNull { it.key == selectedGroupKey }
        ?: groups.firstOrNull()

    SubtitleLanguageVariantColumns(
        languageColumn = {
            groups.forEach { group ->
                val isSelected = group.key == selectedGroup?.key
                SubtitleChoiceRow(
                    title = if (group.isNone) {
                        stringResource(Res.string.compose_player_none)
                    } else {
                        subtitleGroupLabel(group.languageCode, group.fallbackLabel)
                    },
                    subtitle = if (group.isNone) null else group.tracks.size.toString(),
                    selected = isSelected,
                    onClick = {
                        selectedGroupKey = group.key
                        if (group.isNone) onTrackSelected(-1)
                    },
                )
            }
        },
        variantColumn = {
            if (selectedGroup?.isNone == true) {
                SubtitleChoiceRow(
                    title = stringResource(Res.string.compose_player_none),
                    selected = selectedIndex == -1,
                    onClick = { onTrackSelected(-1) },
                )
            } else {
                selectedGroup?.tracks.orEmpty().forEach { track ->
                    val isSelected = track.index == selectedIndex
                    SubtitleChoiceRow(
                        title = builtInSubtitleVariantLabel(track),
                        selected = isSelected,
                        onClick = { onTrackSelected(track.index) },
                    )
                }
            }
        },
    )
}

@Composable
private fun builtInSubtitleVariantLabel(track: SubtitleTrack): String {
    val displayName = localizedTrackDisplayName(track.label, track.language, track.index)
    if (!track.isForced) return displayName

    val forced = stringResource(Res.string.settings_playback_option_forced)
    return if (displayName.contains(forced, ignoreCase = true)) {
        displayName
    } else {
        "$displayName - $forced"
    }
}

@Composable
private fun AddonSubtitleList(
    addons: List<AddonSubtitle>,
    selectedId: String?,
    isLoading: Boolean,
    onSubtitleSelected: (AddonSubtitle) -> Unit,
    onFetch: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp),
            )
        }
        return
    }

    if (addons.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .desktopClickablePointer()
                .clickable(onClick = onFetch)
                .padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.then(
                    Modifier.padding()
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(Res.string.compose_player_fetch_subtitles),
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        return
    }

    val groups = remember(addons) { addons.toAddonSubtitleGroups() }
    val groupKeys = remember(groups) { groups.map { it.key } }
    val selectedSubtitleGroupKey = remember(addons, selectedId) {
        addons.firstOrNull { it.id == selectedId }?.languageGroupKey()
    }
    var selectedGroupKey by remember {
        mutableStateOf(selectedSubtitleGroupKey ?: groupKeys.firstOrNull().orEmpty())
    }

    LaunchedEffect(selectedSubtitleGroupKey, groupKeys) {
        selectedGroupKey = when {
            selectedSubtitleGroupKey != null && selectedSubtitleGroupKey in groupKeys -> selectedSubtitleGroupKey
            selectedGroupKey in groupKeys -> selectedGroupKey
            groupKeys.isNotEmpty() -> groupKeys.first()
            else -> ""
        }
    }

    val selectedGroup = groups.firstOrNull { it.key == selectedGroupKey }
        ?: groups.firstOrNull()

    SubtitleLanguageVariantColumns(
        languageColumn = {
            groups.forEach { group ->
                SubtitleChoiceRow(
                    title = subtitleGroupLabel(group.languageCode, fallbackLabel = null),
                    subtitle = group.subtitles.size.toString(),
                    selected = group.key == selectedGroup?.key,
                    onClick = { selectedGroupKey = group.key },
                )
            }
        },
        variantColumn = {
            selectedGroup?.subtitles.orEmpty().forEach { sub ->
                SubtitleChoiceRow(
                    title = sub.display,
                    selected = sub.id == selectedId,
                    onClick = { onSubtitleSelected(sub) },
                )
            }
        },
    )
}

@Composable
private fun SubtitleLanguageVariantColumns(
    languageColumn: @Composable ColumnScope.() -> Unit,
    variantColumn: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(0.42f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Language",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            languageColumn()
        }
        Column(
            modifier = Modifier.weight(0.58f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Variant",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            variantColumn()
        }
    }
}

@Composable
private fun SubtitleChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .desktopClickablePointer()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = if (selected) {
                        colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun subtitleGroupLabel(
    languageCode: String?,
    fallbackLabel: String?,
): String {
    val normalized = normalizeLanguageCode(languageCode)
    return when {
        !normalized.isNullOrBlank() -> languageLabelForCode(normalized)
        !fallbackLabel.isNullOrBlank() -> fallbackLabel
        else -> stringResource(Res.string.subtitle_language_unknown)
    }
}

private data class BuiltInSubtitleGroup(
    val key: String,
    val languageCode: String?,
    val fallbackLabel: String?,
    val tracks: List<SubtitleTrack>,
) {
    val isNone: Boolean = key == SubtitleNoneGroupKey
}

private data class AddonSubtitleGroup(
    val key: String,
    val languageCode: String?,
    val subtitles: List<AddonSubtitle>,
)

private fun List<SubtitleTrack>.toBuiltInSubtitleGroups(): List<BuiltInSubtitleGroup> {
    val grouped = groupBy { it.languageGroupKey() }
    return listOf(
        BuiltInSubtitleGroup(
            key = SubtitleNoneGroupKey,
            languageCode = null,
            fallbackLabel = null,
            tracks = emptyList(),
        ),
    ) + grouped.map { (key, tracks) ->
        val first = tracks.first()
        BuiltInSubtitleGroup(
            key = key,
            languageCode = first.language?.takeIf(String::isNotBlank),
            fallbackLabel = first.label.takeIf(String::isNotBlank),
            tracks = tracks,
        )
    }
}

private fun List<AddonSubtitle>.toAddonSubtitleGroups(): List<AddonSubtitleGroup> =
    groupBy { it.languageGroupKey() }
        .map { (key, subtitles) ->
            AddonSubtitleGroup(
                key = key,
                languageCode = subtitles.firstOrNull()?.language,
                subtitles = subtitles,
            )
        }

private fun SubtitleTrack.languageGroupKey(): String =
    subtitleLanguageGroupKey(language, label)

private fun AddonSubtitle.languageGroupKey(): String =
    subtitleLanguageGroupKey(language, display)

private fun subtitleLanguageGroupKey(
    language: String?,
    fallbackLabel: String?,
): String {
    normalizeLanguageCode(language)?.let { return "language:${it.lowercase(Locale.US)}" }
    val fallback = fallbackLabel
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.lowercase(Locale.US)
    return if (fallback != null) {
        "label:$fallback"
    } else {
        "language:unknown"
    }
}
