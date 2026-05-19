package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.nuvio.app.desktop.DesktopPreferences
import com.nuvio.app.desktop.DesktopRuntimeLog

private const val debugPrefsNamespace = "nuvio_debug"
private const val debugLogsEnabledKey = "debug_logs_enabled"

@Composable
internal actual fun DebugLogsSettingsSection(isTablet: Boolean) {
    var enabled by remember {
        mutableStateOf(
            DesktopPreferences.getBoolean(debugPrefsNamespace, debugLogsEnabledKey) ?: false
        )
    }

    SettingsSection(
        title = "DEBUG LOGS",
        isTablet = isTablet,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 10.dp)) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = "Debug logs",
                    description = "Write detailed desktop-runtime.log diagnostics for player, UI, and performance issues. Log file location: %LOCALAPPDATA%/Nuvio/cache/logs/",
                    checked = enabled,
                    isTablet = isTablet,
                    onCheckedChange = { checked ->
                        enabled = checked
                        DesktopPreferences.putBoolean(debugPrefsNamespace, debugLogsEnabledKey, checked)
                        if (!checked) {
                            DesktopRuntimeLog.info("Debug logs disabled by user")
                        }
                        DesktopRuntimeLog.debugEnabled = checked
                        if (checked) {
                            DesktopRuntimeLog.info("Debug logs enabled by user")
                        }
                    },
                )
            }
        }
    }
}
