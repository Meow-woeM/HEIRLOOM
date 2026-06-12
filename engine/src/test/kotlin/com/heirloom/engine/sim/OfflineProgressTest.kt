package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.Pioneer
import com.heirloom.engine.model.VentureId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineProgressTest {
    private val cfg = T.quiet

    @Test
    fun `real time converts to game days at the configured rate`() {
        val secondsPerDay = 1.0 / cfg.gameDaysPerRealSecond
        val result = OfflineProgressCalculator.apply(stocked(), secondsPerDay * 3.0, cfg)
        assertEquals(3.0, result.summary.gameDaysSimulated, 1e-9)
        assertFalse(result.summary.cappedByLimit)
    }

    @Test
    fun `away time beyond the cap is forfeited`() {
        val tenDaysReal = 10.0 * 24 * 3600.0
        val result = OfflineProgressCalculator.apply(stocked(), tenDaysReal, cfg)
        assertTrue(result.summary.cappedByLimit)
        assertEquals(cfg.offlineCapHoursBase * 3600.0, result.summary.realSecondsCounted, 1e-6)
        assertEquals(
            cfg.offlineCapHoursBase * 3600.0 * cfg.gameDaysPerRealSecond,
            result.summary.gameDaysSimulated,
            1e-6,
        )
    }

    @Test
    fun `the wagon train and long roads mechanic stretch the cap`() {
        val base = stocked()
        assertEquals(10.0, OfflineProgressCalculator.offlineCapHours(base, cfg))
        val wagons = base.copy(ventures = mapOf(VentureId.WAGON_TRAIN to 2))
        assertEquals(34.0, OfflineProgressCalculator.offlineCapHours(wagons, cfg))
        val longRoads = wagons.copy(monuments = 5)
        assertEquals(40.0, OfflineProgressCalculator.offlineCapHours(longRoads, cfg))
    }

    @Test
    fun `offline catch-up equals a live tick of the same length`() {
        // Same tick function, same day boundaries, same rng walk — results must be identical.
        val start = stocked()
        val offline = OfflineProgressCalculator.apply(start, 3600.0, T.cfg) // real config, events on
        val days = 3600.0 * T.cfg.gameDaysPerRealSecond
        val live = TickEngine.tick(start, days, T.cfg)
        assertEquals(live.state, offline.state)
    }

    @Test
    fun `bypassing the cap powers rewarded-ad boosts`() {
        // 12 real hours is past the 10h cap but well within the pioneer's remaining years.
        val twelveHours = 12.0 * 3600.0
        val result = OfflineProgressCalculator.apply(stocked(), twelveHours, cfg, bypassCap = true)
        assertFalse(result.summary.cappedByLimit)
        assertEquals(twelveHours * cfg.gameDaysPerRealSecond, result.summary.gameDaysSimulated, 1e-6)
    }

    @Test
    fun `the summary reports gains seasons and levels`() {
        val secondsPerDay = 1.0 / cfg.gameDaysPerRealSecond
        val result = OfflineProgressCalculator.apply(stocked(), secondsPerDay * 28.0, cfg)
        val summary = result.summary
        assertEquals(4, summary.seasonsPassed)
        assertEquals(1, summary.yearsPassed)
        assertTrue(summary.resourcesGained.food != 0.0)
        assertTrue(summary.activityLevelsGained >= 3, "28 days of foraging levels it a few times")
        assertTrue(summary.skillLevelsGained >= 1)
        assertFalse(summary.lifeEnded)
    }

    @Test
    fun `dying offline halts the simulation at the legacy screen`() {
        // A pioneer two days from certain death, with a long absence ahead.
        val old = stocked().copy(
            pioneer = Pioneer(ageDays = 75.0 * cfg.daysPerYear, deathAgeOffsetYears = 0.0),
        )
        val tenDaysReal = 10.0 * 24 * 3600.0
        val wagons = old.copy(ventures = mapOf(VentureId.WAGON_TRAIN to 3)) // cap 46h, plenty
        val result = OfflineProgressCalculator.apply(wagons, tenDaysReal, cfg)
        assertTrue(result.summary.lifeEnded)
        assertTrue(result.state.isLegacyPending)
        assertTrue(result.summary.posterityEarned > 0.0)
        assertTrue(
            result.summary.gameDaysSimulated < result.summary.realSecondsCounted * cfg.gameDaysPerRealSecond - 1.0,
            "time after death is forfeited",
        )
    }

    private fun stocked() = T.newState().copy(
        resources = T.newState().resources.copy(food = 100_000.0),
    )
}
