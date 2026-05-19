package com.nuvio.app.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_root_about_section
import nuvio.composeapp.generated.resources.compose_settings_root_general_section
import nuvio.composeapp.generated.resources.compose_settings_page_playback
import nuvio.composeapp.generated.resources.compose_settings_page_poster_customization
import nuvio.composeapp.generated.resources.compose_settings_page_appearance
import nuvio.composeapp.generated.resources.compose_settings_page_root
import nuvio.composeapp.generated.resources.compose_settings_root_nightly_updates_description
import nuvio.composeapp.generated.resources.compose_settings_root_nightly_updates_title
import nuvio.composeapp.generated.resources.settings_layout_remember_fullscreen
import nuvio.composeapp.generated.resources.settings_layout_remember_fullscreen_description
import nuvio.composeapp.generated.resources.settings_layout_remember_screen
import nuvio.composeapp.generated.resources.settings_layout_remember_screen_description
import nuvio.composeapp.generated.resources.settings_layout_section_window
import nuvio.composeapp.generated.resources.settings_keybinds_description
import nuvio.composeapp.generated.resources.settings_keybinds_title
import nuvio.composeapp.generated.resources.settings_playback_section_decoder
import nuvio.composeapp.generated.resources.settings_poster_always_animate_gif
import org.jetbrains.compose.resources.stringResource

/**
 * Desktop-only Settings Search entries for surfaces that only render on Desktop
 * (Keybinds, Debug Logs, Desktop decoder, Always Animate GIFs, updater / nightly build mode).
 *
 * Kept in desktopMain so Android and iOS don't pull in Desktop-only row copy. Labels reuse
 * existing localized resource keys wherever a matching one exists in values/strings.xml;
 * the Debug Logs and Desktop decoder surfaces only ship Desktop-local English copy in
 * build 61 and are referenced verbatim here so search matches the rendered rows.
 */
@Composable
internal actual fun platformSettingsSearchEntries(): List<SettingsSearchEntry> {
    val generalCategory = stringResource(SettingsCategory.General.labelRes)
    val aboutCategory = stringResource(SettingsCategory.About.labelRes)
    val generalSection = stringResource(Res.string.compose_settings_root_general_section)
    val aboutSection = stringResource(Res.string.compose_settings_root_about_section)
    val rootPage = stringResource(Res.string.compose_settings_page_root)
    val playbackPage = stringResource(Res.string.compose_settings_page_playback)
    val playbackDecoderSection = stringResource(Res.string.settings_playback_section_decoder)
    val posterPage = stringResource(Res.string.compose_settings_page_poster_customization)
    val layoutPage = stringResource(Res.string.compose_settings_page_appearance)
    val windowSection = stringResource(Res.string.settings_layout_section_window)

    val keybindsTitle = stringResource(Res.string.settings_keybinds_title)
    val keybindsDescription = stringResource(Res.string.settings_keybinds_description)
    val alwaysAnimateGifTitle = stringResource(Res.string.settings_poster_always_animate_gif)
    val nightlyTitle = stringResource(Res.string.compose_settings_root_nightly_updates_title)
    val nightlyDescription = stringResource(Res.string.compose_settings_root_nightly_updates_description)
    val rememberScreenTitle = stringResource(Res.string.settings_layout_remember_screen)
    val rememberScreenDescription = stringResource(Res.string.settings_layout_remember_screen_description)
    val rememberFullscreenTitle = stringResource(Res.string.settings_layout_remember_fullscreen)
    val rememberFullscreenDescription = stringResource(Res.string.settings_layout_remember_fullscreen_description)

    return listOf(
        SettingsSearchEntry(
            key = "desktop-keybinds",
            title = keybindsTitle,
            description = keybindsDescription,
            page = rootPage,
            section = generalSection,
            category = generalCategory,
            icon = Icons.Rounded.Keyboard,
            target = SettingsSearchTarget.Page(SettingsPage.Root),
        ),
        SettingsSearchEntry(
            key = "desktop-decoder",
            // Mirrors the DesktopDecoderSettingsSection header; Desktop-local copy in
            // build 61 that is only rendered on Desktop (see desktopMain actual).
            title = "Decoder (Desktop)",
            description = "Hardware decoding mode for the MPV / MediaMP backend.",
            page = playbackPage,
            section = playbackDecoderSection,
            category = generalCategory,
            icon = Icons.Rounded.Memory,
            target = SettingsSearchTarget.Page(SettingsPage.Playback),
        ),
        SettingsSearchEntry(
            key = "desktop-debug-logs",
            // Mirrors the DebugLogsSettingsSection header + row; Desktop-local copy in
            // build 61 that is only rendered on Desktop (see desktopMain actual).
            title = "Debug Logs",
            description = "Enable Debug Logs — writes detailed runtime diagnostics to desktop-runtime.log.",
            page = rootPage,
            section = aboutSection,
            category = aboutCategory,
            icon = Icons.Rounded.BugReport,
            target = SettingsSearchTarget.Page(SettingsPage.Root),
        ),
        SettingsSearchEntry(
            key = "desktop-remember-screen",
            title = rememberScreenTitle,
            description = rememberScreenDescription,
            page = layoutPage,
            section = windowSection,
            category = generalCategory,
            icon = Icons.Rounded.Tune,
            target = SettingsSearchTarget.Page(SettingsPage.Appearance),
        ),
        SettingsSearchEntry(
            key = "desktop-remember-fullscreen",
            title = rememberFullscreenTitle,
            description = rememberFullscreenDescription,
            page = layoutPage,
            section = windowSection,
            category = generalCategory,
            icon = Icons.Rounded.Tune,
            target = SettingsSearchTarget.Page(SettingsPage.Appearance),
        ),
        SettingsSearchEntry(
            key = "desktop-always-animate-gif",
            title = alwaysAnimateGifTitle,
            description = "",
            page = posterPage,
            section = posterPage,
            category = generalCategory,
            icon = Icons.Rounded.Tune,
            target = SettingsSearchTarget.Page(SettingsPage.PosterCustomization),
        ),
        SettingsSearchEntry(
            key = "desktop-nightly-updates",
            title = nightlyTitle,
            description = nightlyDescription,
            page = rootPage,
            section = aboutSection,
            category = aboutCategory,
            icon = Icons.Rounded.CloudDownload,
            target = SettingsSearchTarget.Page(SettingsPage.Root),
        ),
    )
}
