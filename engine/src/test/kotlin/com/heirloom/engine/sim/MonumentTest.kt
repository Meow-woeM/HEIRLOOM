package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.MechanicUnlock
import com.heirloom.engine.model.Resources
import com.heirloom.engine.model.VentureId
import com.heirloom.engine.model.VistaStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonumentTest {
    private val cfg = T.quiet

    private fun cityState() = T.newState().copy(
        era = Era.CITY,
        maxEraThisLife = Era.CITY,
        maxEraEver = Era.CITY,
        resources = Resources(food = 1e12, materials = 1e12, money = 1e12, standing = 1e12),
        posterity = 5_000.0,
        posterityEarnedThisMonument = 5_000.0,
        valueScoreThisMonument = 2.5e7,
        ventures = mapOf(VentureId.SAWMILL_SHARE to 4, VentureId.WAGON_TRAIN to 2),
        heirlooms = setOf(HeirloomId.PROVEN_DEED, HeirloomId.FOUNDERS_GAVEL),
        generation = 14,
    )

    @Test
    fun `founding requires era 6 and the price`() {
        assertFalse(MonumentManager.canFound(T.newState(), cfg), "not in the city era")
        assertFalse(
            MonumentManager.canFound(cityState().copy(resources = Resources.ZERO), cfg),
            "cannot afford",
        )
        assertTrue(MonumentManager.canFound(cityState(), cfg))
    }

    @Test
    fun `monument cost scales with each founding`() {
        val first = CostCalculator.monumentCost(cityState(), cfg)
        val third = CostCalculator.monumentCost(cityState().copy(monuments = 2), cfg)
        assertEquals(first.materials * cfg.monumentCostGrowth * cfg.monumentCostGrowth, third.materials, 1e-3)
    }

    @Test
    fun `founding resets the line but never the legacy`() {
        val s = MonumentManager.found(cityState(), cfg)
        assertEquals(1, s.monuments)
        assertEquals(0.0, s.posterity, "posterity resets")
        assertEquals(0.0, s.posterityEarnedThisMonument)
        assertEquals(0.0, s.valueScoreThisMonument)
        assertTrue(s.ventures.isEmpty(), "ventures reset")
        assertEquals(setOf(HeirloomId.PROVEN_DEED, HeirloomId.FOUNDERS_GAVEL), s.heirlooms, "heirlooms persist")
        assertEquals(Era.CITY, s.maxEraEver, "the vista never forgets")
        assertEquals(15, s.generation, "generation numbering stays monotonic")
        assertEquals(Era.CLAIM, s.era, "fresh line starts per the deed (no Old Money yet at 1 monument)")
        assertFalse(s.isLegacyPending, "the new line starts immediately")
        assertTrue(s.eventLog.any { it.kind == LogKind.MONUMENT_FOUNDED })
    }

    @Test
    fun `each monument is a permanent +100% to all yields`() {
        val two = T.newState().copy(monuments = 2)
        assertEquals(3.0, YieldCalculator.globalFactor(two, cfg), 1e-12)
    }

    @Test
    fun `mechanics unlock on the configured schedule`() {
        val s = T.newState()
        assertFalse(s.hasMechanic(MechanicUnlock.AUTOMATION, cfg))
        assertTrue(s.copy(monuments = 1).hasMechanic(MechanicUnlock.AUTOMATION, cfg))
        assertFalse(s.copy(monuments = 1).hasMechanic(MechanicUnlock.SECOND_SKILL, cfg))
        assertTrue(s.copy(monuments = 2).hasMechanic(MechanicUnlock.STARTING_ERA_BUMP, cfg))
        assertTrue(s.copy(monuments = 3).hasMechanic(MechanicUnlock.SECOND_SKILL, cfg))
        assertTrue(s.copy(monuments = 4).hasMechanic(MechanicUnlock.EVENT_CHOICES, cfg))
        assertTrue(s.copy(monuments = 5).hasMechanic(MechanicUnlock.OFFLINE_CAP_BONUS, cfg))
        assertTrue(s.copy(monuments = 6).hasMechanic(MechanicUnlock.AUTOMATION, cfg), "unlocks never expire")
    }

    @Test
    fun `the town vista grows on first-time eras then monuments and never resets`() {
        val s = T.newState()
        assertEquals(VistaStage.WAGON_CAMP, s.vistaStage)
        assertEquals(VistaStage.WAGON_CAMP, s.copy(maxEraEver = Era.CLAIM).vistaStage)
        assertEquals(VistaStage.HOMESTEAD, s.copy(maxEraEver = Era.HOMESTEAD).vistaStage)
        assertEquals(VistaStage.VILLAGE, s.copy(maxEraEver = Era.VILLAGE).vistaStage)
        assertEquals(VistaStage.FRONTIER_TOWN, s.copy(maxEraEver = Era.RAILROAD).vistaStage)
        assertEquals(VistaStage.FRONTIER_TOWN, s.copy(maxEraEver = Era.CITY).vistaStage, "city needs a monument")
        assertEquals(VistaStage.FOUNDED_CITY, s.copy(maxEraEver = Era.CITY, monuments = 1).vistaStage)
        assertEquals(VistaStage.OLD_CITY, s.copy(maxEraEver = Era.CITY, monuments = 3).vistaStage)
        assertEquals(VistaStage.MODERN_CITY, s.copy(maxEraEver = Era.CITY, monuments = 5).vistaStage)
        assertEquals(VistaStage.FUTURE_CITY, s.copy(maxEraEver = Era.CITY, monuments = 6).vistaStage)
    }

    @Test
    fun `the sixth monument completes the save`() {
        assertFalse(MonumentManager.isComplete(T.newState().copy(monuments = 5), cfg))
        assertTrue(MonumentManager.isComplete(T.newState().copy(monuments = 6), cfg))
    }
}
