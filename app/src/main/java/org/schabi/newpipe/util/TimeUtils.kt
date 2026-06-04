package org.schabi.newpipe.util

import java.util.Locale

object TimeUtils {
    @JvmStatic
    fun millisecondsToString(milliseconds: Double): String {
        val seconds = (milliseconds / 1000).toInt() % 60
        val minutes = ((milliseconds / (1000 * 60)) % 60).toInt()
        val hours = ((milliseconds / (1000 * 60 * 60)) % 24).toInt()

        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }
}
