package com.nuvio.app.features.player

internal expect object PlayerWallClock {
    fun nowEpochMs(): Long
    fun formatTime(epochMs: Long): String
}
