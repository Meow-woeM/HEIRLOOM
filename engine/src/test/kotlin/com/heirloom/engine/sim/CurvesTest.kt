package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.LevelProgress
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

class CurvesTest {

    @Test
    fun `skill xp requirement is base times growth to the level`() {
        assertEquals(T.cfg.skillXpBase, Curves.xpToNextSkillLevel(0, T.cfg))
        assertEquals(T.cfg.skillXpBase * 1.12, Curves.xpToNextSkillLevel(1, T.cfg), 1e-12)
        assertEquals(T.cfg.skillXpBase * 1.12.pow(25), Curves.xpToNextSkillLevel(25, T.cfg), 1e-9)
    }

    @Test
    fun `activity xp requirement is base times growth to the level`() {
        assertEquals(T.cfg.activityXpBase, Curves.xpToNextActivityLevel(0, T.cfg))
        assertEquals(
            T.cfg.activityXpBase * T.cfg.activityXpGrowth.pow(10),
            Curves.xpToNextActivityLevel(10, T.cfg),
            1e-9,
        )
    }

    @Test
    fun `addXp accumulates below the threshold`() {
        // Skill level 1 costs 5 XP; 1 + 2 stays below it.
        val (progress, gained) = Curves.addXp(LevelProgress(0, 1.0), 2.0) { Curves.xpToNextSkillLevel(it, T.cfg) }
        assertEquals(LevelProgress(0, 3.0), progress)
        assertEquals(0, gained)
    }

    @Test
    fun `addXp consumes multiple level-ups in one grant`() {
        // Costs from level 0: 8, 8.8, 9.68 (activity curve). 27 XP clears exactly three.
        val total = 8.0 + 8.8 + 9.68
        val (progress, gained) = Curves.addXp(LevelProgress(), total + 0.5) {
            Curves.xpToNextActivityLevel(it, T.cfg)
        }
        assertEquals(3, progress.level)
        assertEquals(3, gained)
        assertEquals(0.5, progress.xp, 1e-9)
    }
}
