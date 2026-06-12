package com.heirloom.app.ui

import com.heirloom.engine.model.NumberFormatting

fun Double.compact(): String = NumberFormatting.compact(this)

fun formatRealDuration(totalSeconds: Double): String {
    val seconds = totalSeconds.toLong()
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
