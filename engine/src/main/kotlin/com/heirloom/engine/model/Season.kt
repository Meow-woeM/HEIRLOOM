package com.heirloom.engine.model

import com.heirloom.engine.balance.BalanceConfig
import kotlinx.serialization.Serializable

@Serializable
enum class Season(val displayName: String) {
    SPRING("Spring"),
    SUMMER("Summer"),
    FALL("Fall"),
    WINTER("Winter");

    companion object {
        /** Season for an absolute world day. The world calendar runs continuously across generations. */
        fun atDay(totalGameDays: Double, config: BalanceConfig): Season {
            val dayOfYear = Math.floorMod(Math.floor(totalGameDays).toLong(), config.daysPerYear.toLong()).toInt()
            return entries[dayOfYear / config.daysPerSeason]
        }

        fun yearAtDay(totalGameDays: Double, config: BalanceConfig): Int =
            (Math.floor(totalGameDays).toLong() / config.daysPerYear).toInt() + 1
    }
}
