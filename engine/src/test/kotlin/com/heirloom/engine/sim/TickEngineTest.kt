package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.SkillId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TickEngineTest {
    private val cfg = T.quiet

    @Test
    fun `one day of foraging produces the daily yield and burns upkeep`() {
        val start = T.newState().copy(activeSkill = null)
        val result = TickEngine.tick(start, 1.0, cfg)
        val yield1 = cfg.activityBaseYields.getValue(ActivityId.FORAGE)
        assertEquals(25.0 + yield1 - 1.0, result.state.resources.food, 1e-9)
        assertEquals(yield1, result.state.lifetimeEarned.food, 1e-9)
        assertEquals(yield1 * 0.3, result.state.valueScoreThisMonument, 1e-9, "food weights 0.3 into the value score")
        assertEquals(1.0, result.state.totalGameDays, 1e-12)
        assertEquals(1.0, result.state.dayOfLife, 1e-12)
    }

    @Test
    fun `aging tracks elapsed days`() {
        val start = T.newState()
        val aged = TickEngine.tick(start, 3.5, cfg).state
        assertEquals(start.pioneer.ageDays + 3.5, aged.pioneer.ageDays, 1e-9)
        assertEquals(18.0 + 3.5 / cfg.daysPerYear, aged.ageYears(cfg), 1e-9)
    }

    @Test
    fun `activity earns xp and levels itself up`() {
        val start = T.newState()
        // 8 XP at 1/day: level 1 lands exactly at day 8.
        val s = TickEngine.tick(start, 8.0, cfg).state
        assertEquals(1, s.activityLevel(ActivityId.FORAGE))
        assertEquals(0.0, s.activityProgress.getValue(ActivityId.FORAGE).xp, 1e-9)
    }

    @Test
    fun `training skill earns xp with the affinity boost`() {
        val start = T.newState().copy(activeSkill = SkillId.HUSBANDRY) // forage = food work -> 1.5x
        val s = TickEngine.tick(start, 2.0, cfg).state
        assertEquals(3.0, s.skillProgress.getValue(SkillId.HUSBANDRY).xp, 1e-9)
    }

    @Test
    fun `second skill trains at half speed`() {
        val start = T.newState().copy(
            monuments = 3, // unlocks SECOND_SKILL per the default schedule
            activeSkill = SkillId.HUSBANDRY,
            secondSkill = SkillId.THRIFT,
        )
        val s = TickEngine.tick(start, 2.0, cfg).state
        assertEquals(1.0, s.skillProgress.getValue(SkillId.THRIFT).xp, 1e-9, "1.0/day * 0.5 * 2 days")
    }

    @Test
    fun `running out of food clamps at zero and flags starvation`() {
        val start = T.newState().copy(
            activeActivity = ActivityId.SCOUT_AHEAD, // no food coming in
            resources = T.newState().resources.copy(food = 0.5),
        )
        val s = TickEngine.tick(start, 1.0, cfg).state
        assertEquals(0.0, s.resources.food)
        assertTrue(s.starving)
        assertTrue(s.starvedThisLife)
    }

    @Test
    fun `starvation halves the next day's work`() {
        val start = T.newState().copy(
            activeActivity = ActivityId.SCOUT_AHEAD,
            resources = T.newState().resources.copy(food = 0.0),
        )
        val day1 = TickEngine.tick(start, 1.0, cfg).state // starves during day 1
        val materialsAfterDay1 = day1.resources.materials
        val day2 = TickEngine.tick(day1, 1.0, cfg).state
        val gainDay2 = day2.resources.materials - materialsAfterDay1
        assertEquals(
            cfg.activityBaseYields.getValue(ActivityId.SCOUT_AHEAD) * cfg.starvationYieldFactor,
            gainDay2,
            1e-9,
        )
    }

    @Test
    fun `recovering food clears the starving flag`() {
        val start = T.newState().copy(starving = true, starvedThisLife = true)
        val s = TickEngine.tick(start, 1.0, cfg).state // foraging brings in more than upkeep even at half rate
        assertFalse(s.starving)
        assertTrue(s.starvedThisLife, "food security stays lost for the life")
    }

    @Test
    fun `many small steps equal one big step`() {
        // Two days: no level threshold is crossed (level-ups are quantized to step ends,
        // so equivalence is only exact between thresholds).
        val start = T.newState().copy(activeSkill = SkillId.GRIT)
        var split = start
        repeat(20) { split = TickEngine.tick(split, 0.1, cfg).state }
        val whole = TickEngine.tick(start, 2.0, cfg).state
        assertEquals(whole.resources.food, split.resources.food, 1e-6)
        assertEquals(whole.pioneer.ageDays, split.pioneer.ageDays, 1e-9)
        assertEquals(whole.skillProgress.getValue(SkillId.GRIT).xp, split.skillProgress.getValue(SkillId.GRIT).xp, 1e-6)
        assertEquals(whole.totalGameDays, split.totalGameDays, 1e-9)
    }

    @Test
    fun `ticking is frozen while the legacy screen is pending`() {
        val start = T.newState()
        val dead = GenerationManager.endLife(start, voluntary = false, cfg, mutableListOf())
        val after = TickEngine.tick(dead, 10.0, cfg)
        assertEquals(dead, after.state)
        assertTrue(after.newLogEntries.isEmpty())
    }

    @Test
    fun `zero or negative dt is a no-op`() {
        val start = T.newState()
        assertEquals(start, TickEngine.tick(start, 0.0, cfg).state)
        assertEquals(start, TickEngine.tick(start, -1.0, cfg).state)
    }
}
