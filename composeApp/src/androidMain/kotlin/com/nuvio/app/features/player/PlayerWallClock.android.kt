package com.nuvio.app.features.player

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal actual object PlayerWallClock {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())

    actual fun nowEpochMs(): Long = System.currentTimeMillis()

    actual fun formatTime(epochMs: Long): String =
        formatter.format(Instant.ofEpochMilli(epochMs))
}
