package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.Season
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeasonTest {
    private val cfg = T.quiet

    @Test
    fun `the calendar cycles spring summer fall winter every 28 days`() {
        assertEquals(Season.SPRING, Season.atDay(0.0, cfg))
        assertEquals(Season.SPRING, Season.atDay(6.9, cfg))
        assertEquals(Season.SUMMER, Season.atDay(7.0, cfg))
        assertEquals(Season.FALL, Season.atDay(14.0, cfg))
        assertEquals(Season.WINTER, Season.atDay(21.0, cfg))
        assertEquals(Season.SPRING, Season.atDay(28.0, cfg))
        assertEquals(Season.WINTER, Season.atDay(28.0 * 3 + 27.0, cfg))
    }

    @Test
    fun `surviving winter with food grants the spring bounty`() {
        val stocked = T.newState().copy(resources = T.newState().resources.copy(food = 1_000.0))
        val s = TickEngine.tick(stocked, 28.0, cfg).state
        assertEquals(1, s.wintersSurvivedThisLife)
        assertTrue(s.springBountyActive)
        assertTrue(s.eventLog.any { it.kind == LogKind.SPRING_BOUNTY })
    }

    @Test
    fun `a starved winter grants nothing`() {
        val poor = T.newState().copy(
            activeActivity = ActivityId.SCOUT_AHEAD,
            resources = T.newState().resources.copy(food = 10.0), // dies out by winter
        )
        val s = TickEngine.tick(poor, 28.0, cfg).state
        assertEquals(0, s.wintersSurvivedThisLife)
        assertFalse(s.springBountyActive)
    }

    @Test
    fun `the bounty expires when spring ends`() {
        val stocked = T.newState().copy(resources = T.newState().resources.copy(food = 5_000.0))
        val spring = TickEngine.tick(stocked, 28.0, cfg).state
        assertTrue(spring.springBountyActive)
        val summer = TickEngine.tick(spring, 7.0, cfg).state
        assertFalse(summer.springBountyActive)
    }

    @Test
    fun `bounty actually boosts spring production`() {
        val stocked = T.newState().copy(
            resources = T.newState().resources.copy(food = 5_000.0),
            activeSkill = null,
        )
        val intoSpring = TickEngine.tick(stocked, 28.0, cfg).state
        val before = intoSpring.resources.food
        // Forage levels gained over 28 days: level 3 (8+8.8+9.68=26.48 xp consumed by day 27).
        val day = TickEngine.tick(intoSpring, 1.0, cfg).state
        val gain = day.resources.food - before + YieldCalculator.dailyFoodUpkeep(intoSpring, cfg)
        val expected = cfg.activityBaseYields.getValue(ActivityId.FORAGE) *
            (1.0 + 0.08 * intoSpring.activityLevel(ActivityId.FORAGE)) * 1.25
        assertEquals(expected, gain, 1e-9)
    }

    @Test
    fun `winter food drain is applied while ticking through winter`() {
        // Park at the first day of winter with plenty of food and indoor-less work stopped.
        val s0 = T.newState().copy(
            totalGameDays = 21.0,
            activeActivity = null,
            activeSkill = null,
            resources = T.newState().resources.copy(food = 100.0),
        )
        val s1 = TickEngine.tick(s0, 1.0, cfg).state
        assertEquals(100.0 - 3.0, s1.resources.food, 1e-9)
    }

    @Test
    fun `winters survived accumulate across years`() {
        val stocked = T.newState().copy(resources = T.newState().resources.copy(food = 100_000.0))
        val s = TickEngine.tick(stocked, 28.0 * 3, cfg).state
        assertEquals(3, s.wintersSurvivedThisLife)
        assertEquals(3, s.stats.wintersSurvivedTotal)
    }
}
