package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.desktopClickablePointer
import com.nuvio.app.core.ui.nuvioDesktopImageSamplingCacheKey
import com.nuvio.app.desktop.DesktopAppRestarter
import com.nuvio.app.desktop.DesktopPreferences
import com.nuvio.app.desktop.DesktopRendererApi
import com.nuvio.app.desktop.DesktopRendererSettings
import com.nuvio.app.desktop.DesktopRuntimeLog

private const val preferencesName = "nuvio_decoder_settings"
private const val hwdecModeKey = "hwdec_mode"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun DesktopDecoderSettingsSection(isTablet: Boolean) {
    var hwdecMode by remember {
        mutableStateOf(
            DesktopPreferences.getString(preferencesName, hwdecModeKey) ?: "auto"
        )
    }

    var showHwdecDialog by remember { mutableStateOf(false) }
    var selectedRenderer by remember {
        mutableStateOf(DesktopRendererSettings.selectedOrDefault())
    }
    var showRendererDialog by remember { mutableStateOf(false) }
    var restartFailed by remember { mutableStateOf(false) }
    val rendererRequiresRestart = rendererRequiresRestart(selectedRenderer)

    val hwdecOptions = listOf(
        "auto" to "Auto (recommended)",
        "no" to "Software Only",
        "nvdec" to "NVIDIA NVDEC",
        "dxva2" to "DXVA2 (native)",
        "d3d11va" to "D3D11VA",
        "nvdec-copy" to "NVDec (copy-back)",
        "d3d11va-copy" to "D3D11VA (copy-back)",
        "cuda" to "CUDA",
        "vaapi" to "VAAPI",
        "vdpau" to "VDPAU",
    )

    SettingsSection(
        title = "Decoder (Desktop)",
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = "Hardware Decoding",
                description = hwdecOptions.firstOrNull { it.first == hwdecMode }?.second ?: hwdecMode,
                isTablet = isTablet,
                onClick = { showHwdecDialog = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = "Renderer",
                description = rendererSettingDescription(
                    selectedRenderer = selectedRenderer,
                    requiresRestart = rendererRequiresRestart,
                ),
                isTablet = isTablet,
                onClick = { showRendererDialog = true },
            )
            if (rendererRequiresRestart) {
                SettingsGroupDivider(isTablet = isTablet)
                RendererRestartRow(
                    selectedRenderer = selectedRenderer,
                    restartFailed = restartFailed,
                    isTablet = isTablet,
                    onRestart = {
                        restartFailed = !DesktopAppRestarter.restart()
                    },
                )
            }
        }

        SettingsGroup(isTablet = isTablet) {
            Text(
                text = "On Windows the app defaults to ANGLE for Compose and mpv's " +
                    "native Windows surface for video presentation. Player chrome is rendered " +
                    "in an owned transparent overlay window above the native video surface. " +
                    "ANGLE defaults to Compose's Skia Swing layer there because the native " +
                    "SkiaSurface does not present through a translucent overlay window reliably. " +
                    "Set NUVIO_PLAYER_OVERLAY_RENDERER=skia-surface to troubleshoot that path, " +
                    "or swing for the old AWT/Swing repaint fallback.\n\n" +
                    "Hardware decoding (hwdec) is separate from GPU rendering: you can use " +
                    "D3D11VA or NVDEC for video decoding while the selected renderer handles " +
                    "presentation. The Windows default is ANGLE/D3D11. Use the Renderer setting " +
                    "above for normal switching, or launch with NUVIO_SKIKO_RENDER_API=DIRECT3D " +
                    "for Skiko D3D12, or OPENGL for the legacy libmpv/OpenGL path. Set " +
                    "NUVIO_SKIKO_VSYNC_ENABLED=false or " +
                    "NUVIO_SKIKO_WAIT_FOR_VSYNC_ON_REDRAW=true only when comparing renderer frame pacing.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsGroup(isTablet = isTablet) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Runtime diagnostics",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                desktopRuntimeDiagnostics().forEach { (label, value) ->
                    RuntimeDiagnosticRow(label = label, value = value)
                }
            }
        }
    }

    if (showHwdecDialog) {
        BasicAlertDialog(onDismissRequest = { showHwdecDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Hardware Decoding",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        hwdecOptions.forEach { (mode, label) ->
                            val isSelected = mode == hwdecMode
                            val containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .desktopClickablePointer()
                                    .clickable {
                                        hwdecMode = mode
                                        DesktopPreferences.putString(preferencesName, hwdecModeKey, mode)
                                        showHwdecDialog = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = containerColor,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRendererDialog) {
        RendererSelectionDialog(
            selectedRenderer = selectedRenderer,
            onSelected = { renderer ->
                selectedRenderer = renderer
                restartFailed = false
                DesktopRendererSettings.savePreferredRenderApi(renderer)
                showRendererDialog = false
            },
            onDismiss = { showRendererDialog = false },
        )
    }
}

@Composable
private fun RendererRestartRow(
    selectedRenderer: DesktopRendererApi,
    restartFailed: Boolean,
    isTablet: Boolean,
    onRestart: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = if (isTablet) 14.dp else 12.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Requires restart",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (restartFailed) {
                    "Automatic restart failed. Close and reopen Nuvio to apply ${selectedRenderer.displayName}."
                } else {
                    "Restart Nuvio to apply ${selectedRenderer.displayName}."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            modifier = Modifier.desktopClickablePointer(),
            onClick = onRestart,
        ) {
            Text("Restart app")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RendererSelectionDialog(
    selectedRenderer: DesktopRendererApi,
    onSelected: (DesktopRendererApi) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Renderer",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DesktopRendererSettings.options.forEach { renderer ->
                        RendererOptionRow(
                            renderer = renderer,
                            isSelected = renderer == selectedRenderer,
                            onClick = { onSelected(renderer) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RendererOptionRow(
    renderer: DesktopRendererApi,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .desktopClickablePointer()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = renderer.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = renderer.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuntimeDiagnosticRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun desktopRuntimeDiagnostics(): List<Pair<String, String>> =
    listOf(
        "Compose renderer" to (System.getProperty("skiko.renderApi") ?: "default"),
        "MPV surface" to (System.getProperty("nuvio.mpv.surface") ?: "auto"),
        "Interop blending" to (System.getProperty("compose.interop.blending") ?: "default"),
        "Overlay renderer" to overlayRendererLabel(),
        "Overlay repaint" to overlayRepaintLabel(),
        "Overlay paint" to (System.getenv("NUVIO_PLAYER_OVERLAY_PAINT_MODE")?.takeIf(String::isNotBlank) ?: defaultOverlayPaintModeLabel()),
        "Redraw vsync" to (System.getProperty("skiko.rendering.windows.waitForFrameVsyncOnRedrawImmediately") ?: "default"),
        "Player perf logs" to playerPerfLogsLabel(),
        "Image sampling" to nuvioDesktopImageSamplingCacheKey,
        "Skiko native path" to (System.getProperty("skiko.library.path") ?: "bundled/default"),
        "Runtime log" to DesktopRuntimeLog.path().toString(),
    )

private fun defaultOverlayPaintModeLabel(): String =
    if (defaultOverlayRendererLabel() == "skia-swing") {
        "render-immediately"
    } else {
        "immediate"
    }

private fun overlayRepaintLabel(): String =
    System.getenv("NUVIO_PLAYER_OVERLAY_REPAINT_HZ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: "display refresh (default)"

private fun overlayRendererLabel(): String =
    System.getenv("NUVIO_PLAYER_OVERLAY_RENDERER")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: "${defaultOverlayRendererLabel()} (default)"

private fun defaultOverlayRendererLabel(): String =
    if (System.getProperty("skiko.renderApi").equals("ANGLE", ignoreCase = true)) {
        "skia-swing"
    } else {
        "swing"
    }

private fun playerPerfLogsLabel(): String =
    if (
        System.getenv("NUVIO_PLAYER_PERF_LOGS").isTruthy() ||
        System.getenv("NUVIO_PLAYER_PERF_TRACE").isTruthy() ||
        System.getProperty("nuvio.player.perfLogs").isTruthy() ||
        System.getProperty("nuvio.player.perfTrace").isTruthy()
    ) {
        "enabled"
    } else {
        "disabled (NUVIO_PLAYER_PERF_LOGS=true)"
    }

private fun String?.isTruthy(): Boolean =
    equals("true", ignoreCase = true) ||
        equals("1") ||
        equals("yes", ignoreCase = true) ||
        equals("on", ignoreCase = true)

private fun rendererRequiresRestart(selectedRenderer: DesktopRendererApi): Boolean =
    !System.getProperty("skiko.renderApi")
        .equals(selectedRenderer.propertyValue, ignoreCase = true)

private fun rendererSettingDescription(
    selectedRenderer: DesktopRendererApi,
    requiresRestart: Boolean,
): String =
    if (requiresRestart) {
        "${selectedRenderer.displayName} - Requires restart"
    } else {
        "${selectedRenderer.displayName} - active"
    }
