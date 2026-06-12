package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.LevelProgress
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.Resources
import com.heirloom.engine.model.SkillId
import com.heirloom.engine.model.VentureId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EraTest {
    private val cfg = T.quiet

    @Test
    fun `activities unlock with their era and stay unlocked`() {
        val trail = T.newState()
        assertTrue(PlayerActions.isActivityUnlocked(trail, ActivityId.FORAGE, cfg))
        assertFalse(PlayerActions.isActivityUnlocked(trail, ActivityId.CLEAR_LAND, cfg))

        val village = trail.copy(era = Era.VILLAGE)
        assertTrue(PlayerActions.isActivityUnlocked(village, ActivityId.FORAGE, cfg), "trail work never goes away")
        assertTrue(PlayerActions.isActivityUnlocked(village, ActivityId.CLEAR_LAND, cfg))
        assertTrue(PlayerActions.isActivityUnlocked(village, ActivityId.GENERAL_STORE, cfg))
    }

    @Test
    fun `standing work is gated by community skill`() {
        val village = T.newState().copy(era = Era.VILLAGE)
        assertFalse(PlayerActions.isActivityUnlocked(village, ActivityId.TEACH_SCHOOL, cfg))
        val communal = village.copy(skillProgress = mapOf(SkillId.COMMUNITY to LevelProgress(5)))
        assertTrue(PlayerActions.isActivityUnlocked(communal, ActivityId.TEACH_SCHOOL, cfg))
    }

    @Test
    fun `selecting a locked activity throws`() {
        assertFailsWith<IllegalArgumentException> {
            PlayerActions.selectActivity(T.newState(), ActivityId.BLACKSMITH, cfg)
        }
    }

    @Test
    fun `era advancement needs the full cost`() {
        val broke = T.newState()
        assertFalse(PlayerActions.canAdvanceEra(broke, cfg))

        val funded = broke.copy(resources = Resources(food = 25.0, materials = 60.0, money = 40.0))
        assertTrue(PlayerActions.canAdvanceEra(funded, cfg))
        val advanced = PlayerActions.advanceEra(funded, cfg)
        assertEquals(Era.CLAIM, advanced.era)
        assertEquals(0.0, advanced.resources.materials, 1e-9)
        assertEquals(0.0, advanced.resources.money, 1e-9)
        assertEquals(25.0, advanced.resources.food, 1e-9, "food is not part of the cost")
        assertEquals(Era.CLAIM, advanced.maxEraThisLife)
        assertEquals(Era.CLAIM, advanced.maxEraEver)
        assertTrue(advanced.eventLog.any { it.kind == LogKind.ERA_ADVANCED && it.detail == "CLAIM" })
    }

    @Test
    fun `thrift ledger and the homestead act make advancement cheaper`() {
        val s = T.newState().copy(
            skillProgress = mapOf(SkillId.THRIFT to LevelProgress(10), SkillId.CRAFTSMANSHIP to LevelProgress(10)),
            heirlooms = setOf(HeirloomId.LEDGER_AND_QUILL),
            ventures = mapOf(VentureId.HOMESTEAD_ACT_FILING to 1),
        )
        val cost = CostCalculator.eraCost(s, Era.CLAIM, cfg)
        val costFactor = 1.0 / (1.0 + 0.008 * 10) * 0.9 * 0.8
        assertEquals(40.0 * costFactor, cost.money, 1e-9)
        assertEquals(60.0 * costFactor * Math.pow(0.99, 10.0), cost.materials, 1e-9, "craftsmanship trims the materials share")
    }

    @Test
    fun `the last era has no successor`() {
        val city = T.newState().copy(
            era = Era.CITY,
            resources = Resources(1e18, 1e18, 1e18, 1e18),
        )
        assertFalse(PlayerActions.canAdvanceEra(city, cfg))
    }

    @Test
    fun `entering railroad town brings the railroad once per life`() {
        val ready = T.newState().copy(
            era = Era.VILLAGE,
            resources = Resources(food = 100.0, materials = 1e9, money = 1e9, standing = 1e6),
        )
        val s = PlayerActions.advanceEra(ready, cfg)
        assertTrue(s.railroadArrivedThisLife)
        assertTrue(s.eventLog.any { it.eventType == com.heirloom.engine.model.EventType.RAILROAD_ARRIVES })
    }

    @Test
    fun `upkeep rises with the era so advancing is a real decision`() {
        val trail = T.newState()
        val claim = trail.copy(era = Era.CLAIM)
        assertTrue(
            YieldCalculator.dailyFoodUpkeep(claim, cfg) > YieldCalculator.dailyFoodUpkeep(trail, cfg),
        )
    }
}
