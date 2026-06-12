package com.heirloom.engine.model

import kotlin.math.abs

/** Idle-game compact numbers: 999, 1.2K, 3.4M, 5.6B, 7.8T, then scientific. */
object NumberFormatting {
    private val suffixes = listOf("" to 1.0, "K" to 1e3, "M" to 1e6, "B" to 1e9, "T" to 1e12, "Qa" to 1e15)

    fun compact(value: Double): String {
        if (value.isNaN()) return "?"
        val v = abs(value)
        val sign = if (value < 0) "-" else ""
        if (v < 1000.0) {
            return sign + if (v == Math.floor(v)) v.toLong().toString() else String.format("%.1f", v)
        }
        for (i in suffixes.indices.reversed()) {
            val (suffix, scale) = suffixes[i]
            if (v >= scale && suffix.isNotEmpty()) {
                val scaled = v / scale
                return sign + when {
                    scaled >= 1e3 -> String.format("%.2e", value) // beyond Qa
                    scaled >= 100 -> String.format("%.0f%s", scaled, suffix)
                    scaled >= 10 -> String.format("%.1f%s", scaled, suffix)
                    else -> String.format("%.2f%s", scaled, suffix)
                }
            }
        }
        return sign + String.format("%.2e", v)
    }
}
