package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.ActiveEvent
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.EventType
import com.heirloom.engine.model.LogKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventSystemTest {
    private val cfg = T.cfg

    private fun certainty(type: EventType) = cfg.copy(
        eventChancePerDay = 1.0,
        eventWeights = mapOf(type to 1.0),
    )

    @Test
    fun `the engine is deterministic - same seed, same history`() {
        val a = TickEngine.tick(T.newState(config = cfg, seed = 42L), 200.0, cfg).state
        val b = TickEngine.tick(T.newState(config = cfg, seed = 42L), 200.0, cfg).state
        assertEquals(a, b)
    }

    @Test
    fun `events fire from the day-boundary roll and are logged`() {
        val sure = certainty(EventType.BARN_RAISING)
        val s = TickEngine.tick(T.newState(sure), 1.0, sure).state
        assertTrue(s.eventLog.any { it.kind == LogKind.RANDOM_EVENT && it.eventType == EventType.BARN_RAISING })
        assertEquals(1, s.stats.eventsFired)
        assertTrue(s.resources.standing > 0.0)
    }

    @Test
    fun `locusts eat a share of the stores`() {
        val s = T.newState().copy(resources = T.newState().resources.copy(food = 100.0))
        val hit = EventSystem.fire(s, EventType.LOCUSTS, cfg, mutableListOf())
        assertEquals(70.0, hit.resources.food, 1e-9)
    }

    @Test
    fun `family counsel softens negative events`() {
        val s = T.newState().copy(
            monuments = 4, // EVENT_CHOICES unlocked
            resources = T.newState().resources.copy(food = 100.0),
        )
        val hit = EventSystem.fire(s, EventType.LOCUSTS, cfg, mutableListOf())
        assertEquals(85.0, hit.resources.food, 1e-9, "30% loss halved to 15%")
        val logs = mutableListOf<com.heirloom.engine.model.EventLogEntry>()
        EventSystem.fire(s, EventType.DROUGHT, cfg, logs)
        assertTrue(logs.single().mitigated)
    }

    @Test
    fun `the merchant pays days of the active work converted to coin`() {
        val s = T.newState() // foraging, base 1.6 food/day
        val paid = EventSystem.fire(s, EventType.TRAVELING_MERCHANT, cfg, mutableListOf())
        val expected = 15.0 * 1.6 * (0.3 / 1.0) // days * daily yield * weight ratio food->money
        assertEquals(expected, paid.resources.money, 1e-9)
        assertEquals(expected, paid.lifetimeEarned.money, 1e-9, "windfalls are real earnings")
        assertTrue(paid.valueScoreThisMonument > s.valueScoreThisMonument)
    }

    @Test
    fun `standing windfalls scale with era and the standing multipliers`() {
        val trail = EventSystem.fire(T.newState(), EventType.BARN_RAISING, cfg, mutableListOf())
        assertEquals(5.0, trail.resources.standing, 1e-9)
        // Village: era^2 = 16, global era factor 1 + 0.25*3 = 1.75.
        val village = EventSystem.fire(T.newState().copy(era = Era.VILLAGE), EventType.BARN_RAISING, cfg, mutableListOf())
        assertEquals(5.0 * 16.0 * 1.75, village.resources.standing, 1e-9)
    }

    @Test
    fun `lasting events count down daily and expire`() {
        val s = T.newState(T.quiet).copy(activeEvents = listOf(ActiveEvent(EventType.DROUGHT, 2.0)))
        val day1 = TickEngine.tick(s, 1.0, T.quiet).state
        assertEquals(1.0, day1.activeEvents.single().remainingDays, 1e-9)
        val day2 = TickEngine.tick(day1, 1.0, T.quiet).state
        assertTrue(day2.activeEvents.isEmpty())
    }

    @Test
    fun `hard winter cannot strike in summer and drought cannot strike in winter`() {
        val hw = certainty(EventType.HARD_WINTER)
        val summer = T.newState(hw).copy(totalGameDays = 7.0)
        val s = TickEngine.tick(summer, 1.0, hw).state
        assertTrue(s.activeEvents.isEmpty())

        val dr = certainty(EventType.DROUGHT)
        val winter = T.newState(dr).copy(totalGameDays = 21.0, resources = T.newState().resources.copy(food = 1000.0))
        val w = TickEngine.tick(winter, 1.0, dr).state
        assertTrue(w.activeEvents.none { it.type == EventType.DROUGHT })
    }

    @Test
    fun `a hard winter thaws when winter ends even if rolled late`() {
        val hw = certainty(EventType.HARD_WINTER)
        // Day 26: late winter. The event would last 7 days but spring melts it at day 28.
        val lateWinter = T.newState(hw).copy(
            totalGameDays = 26.0,
            resources = T.newState().resources.copy(food = 1_000.0),
        )
        val frozen = TickEngine.tick(lateWinter, 1.0, hw).state
        assertTrue(frozen.hardWinterActive)
        val thawed = TickEngine.tick(frozen, 1.0, hw).state // crosses day 28 into spring
        assertFalse(thawed.hardWinterActive)
        assertTrue(thawed.activeEvents.none { it.type == EventType.HARD_WINTER })
    }

    @Test
    fun `the railroad event doubles money work for the rest of the life`() {
        val s = T.newState().copy(activeActivity = ActivityId.HAUL_CARGO)
        val before = YieldCalculator.dailyYield(s, ActivityId.HAUL_CARGO, cfg)
        val arrived = EventSystem.fire(s, EventType.RAILROAD_ARRIVES, cfg, mutableListOf())
        assertEquals(before * 2.0, YieldCalculator.dailyYield(arrived, ActivityId.HAUL_CARGO, cfg), 1e-9)
    }

    @Test
    fun `the chronicle is capped`() {
        val tiny = certainty(EventType.NEWCOMERS).copy(eventLogMaxEntries = 5)
        val s = TickEngine.tick(T.newState(tiny).copy(resources = T.newState().resources.copy(food = 1e6)), 40.0, tiny).state
        assertEquals(5, s.eventLog.size)
    }
}
