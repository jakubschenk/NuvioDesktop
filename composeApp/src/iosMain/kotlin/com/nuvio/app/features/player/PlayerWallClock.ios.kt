package com.nuvio.app.features.player

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

internal actual object PlayerWallClock {
    actual fun nowEpochMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    actual fun formatTime(epochMs: Long): String {
        val formatter = NSDateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.stringFromDate(
            NSDate.dateWithTimeIntervalSince1970(epochMs.toDouble() / 1000.0),
        )
    }
}
