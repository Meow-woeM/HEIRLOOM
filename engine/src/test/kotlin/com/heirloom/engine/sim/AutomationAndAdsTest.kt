package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomationAndAdsTest {
    private val cfg = T.quiet

    private fun automated() = T.newState().copy(
        monuments = 1,
        automationEnabled = true,
        automationPriorities = listOf(ActivityId.HAUL_CARGO, ActivityId.FORAGE),
        resources = T.newState().resources.copy(food = 10_000.0),
    )

    @Test
    fun `standing orders follow the priority list when the larder is full`() {
        val s = TickEngine.tick(automated(), 1.0, cfg).state
        assertEquals(ActivityId.HAUL_CARGO, s.activeActivity)
    }

    @Test
    fun `the food failsafe overrides the priorities`() {
        val hungry = automated().copy(resources = T.newState().resources.copy(food = 5.0))
        val s = TickEngine.tick(hungry, 1.0, cfg).state
        assertEquals(ActivityId.HUNT, s.activeActivity, "best unlocked food work wins")
    }

    @Test
    fun `no monument, no automation`() {
        val pretender = automated().copy(monuments = 0)
        val s = TickEngine.tick(pretender, 1.0, cfg).state
        assertEquals(ActivityId.FORAGE, s.activeActivity, "selection untouched")
        assertFailsWith<IllegalArgumentException> {
            PlayerActions.setAutomation(T.newState(), enabled = true, listOf(ActivityId.FORAGE), cfg)
        }
    }

    @Test
    fun `locked priorities are skipped`() {
        val s = automated().copy(automationPriorities = listOf(ActivityId.BANKING, ActivityId.HAUL_CARGO))
        assertEquals(ActivityId.HAUL_CARGO, Automation.chooseActivity(s, cfg), "banking needs Era 5")
    }

    // ------------------------------------------------------------ traveler boosts

    @Test
    fun `a traveler grants four hours of instant progress`() {
        val s = T.newState().copy(resources = T.newState().resources.copy(food = 100_000.0))
        val result = PlayerActions.watchRewardedAd(s, nowEpochDay = 100L, cfg)
        assertEquals(cfg.rewardedAdHours * 3600.0 * cfg.gameDaysPerRealSecond, result.summary.gameDaysSimulated, 1e-6)
        assertEquals(1, result.state.adsUsedToday)
        assertEquals(1, result.state.stats.adsWatched)
    }

    @Test
    fun `three travelers a day, then the road is empty until tomorrow`() {
        var s = T.newState().copy(resources = T.newState().resources.copy(food = 1e9))
        repeat(3) {
            assertTrue(PlayerActions.canWatchAd(s, 100L, cfg))
            s = PlayerActions.watchRewardedAd(s, 100L, cfg).state
        }
        assertFalse(PlayerActions.canWatchAd(s, 100L, cfg))
        assertFailsWith<IllegalArgumentException> { PlayerActions.watchRewardedAd(s, 100L, cfg) }
        assertTrue(PlayerActions.canWatchAd(s, 101L, cfg), "a new day resets the count")
        assertEquals(1, PlayerActions.watchRewardedAd(s, 101L, cfg).state.adsUsedToday)
    }

    @Test
    fun `no boosts while the legacy screen is up`() {
        val dead = GenerationManager.endLife(T.newState(), voluntary = false, cfg, mutableListOf())
        assertFalse(PlayerActions.canWatchAd(dead, 100L, cfg))
    }
}
