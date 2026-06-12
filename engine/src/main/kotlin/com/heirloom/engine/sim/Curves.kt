package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.LevelProgress
import kotlin.math.pow

/** XP curves shared by activities and skills. XP cost is for the NEXT level from [level]. */
object Curves {

    fun xpToNextActivityLevel(level: Int, config: BalanceConfig): Double =
        config.activityXpBase * config.activityXpGrowth.pow(level)

    fun xpToNextSkillLevel(level: Int, config: BalanceConfig): Double =
        config.skillXpBase * config.skillXpGrowth.pow(level)

    /** Adds XP, consuming level-ups as thresholds are crossed. Returns progress and levels gained. */
    fun addXp(progress: LevelProgress, gained: Double, xpToNext: (Int) -> Double): Pair<LevelProgress, Int> {
        var level = progress.level
        var xp = progress.xp + gained
        var levelsGained = 0
        while (xp >= xpToNext(level)) {
            xp -= xpToNext(level)
            level++
            levelsGained++
        }
        return LevelProgress(level, xp) to levelsGained
    }
}
