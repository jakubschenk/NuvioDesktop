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
                text = "This player uses mpv's libmpv render API (vo=libmpv) which " +
                    "renders video frames into an OpenGL framebuffer when the app is using " +
                    "the legacy OpenGL renderer. On Direct3D/ANGLE, the player uses mpv's " +
                    "native Windows surface path instead, so Compose can keep the faster " +
                    "desktop renderer while mpv owns the video HWND.\n\n" +
                    "Hardware decoding (hwdec) is separate from GPU rendering: you can use " +
                    "D3D11VA or NVDEC for video decoding while the selected renderer handles " +
                    "presentation. If the native surface path has a driver issue, launch with " +
                    "NUVIO_SKIKO_RENDER_API=OPENGL to force the legacy OpenGL path.",
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
