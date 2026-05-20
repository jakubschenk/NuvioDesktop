package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.desktop.DesktopPreferences

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
        }

        SettingsGroup(isTablet = isTablet) {
            Text(
                text = "On Windows the app defaults to ANGLE for Compose and mpv's " +
                    "native Windows surface for video presentation. Player chrome is rendered " +
                    "in an owned Swing-backed overlay window above the native video surface; " +
                    "set NUVIO_PLAYER_OVERLAY_RENDERER=skia only for renderer troubleshooting.\n\n" +
                    "Hardware decoding (hwdec) is separate from GPU rendering: you can use " +
                    "D3D11VA or NVDEC for video decoding while the selected renderer handles " +
                    "presentation. The Windows default is ANGLE/D3D11; launch with " +
                    "NUVIO_SKIKO_RENDER_API=DIRECT3D for Skiko D3D12, or OPENGL for the legacy " +
                    "libmpv/OpenGL path. Set NUVIO_SKIKO_VSYNC_ENABLED=false only when " +
                    "comparing renderer frame pacing.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
}
