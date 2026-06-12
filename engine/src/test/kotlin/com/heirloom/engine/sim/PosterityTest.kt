package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.VentureId
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PosterityTest {
    private val cfg = T.quiet

    @Test
    fun `payout is floor of K root score minus what was already earned`() {
        val s = T.newState().copy(
            valueScoreThisMonument = 10_000.0,
            posterityEarnedThisMonument = 30.0,
            maxEraThisLife = Era.TRAIL,
        )
        val expected = floor(sqrt(10_000.0 + GenerationManager.eraScoreBonus(Era.TRAIL, cfg))) - 30.0
        assertEquals(expected, GenerationManager.posterityPreview(s, cfg), 1e-9)

        val dead = GenerationManager.endLife(s, voluntary = false, cfg, mutableListOf())
        assertEquals(expected, dead.pendingLegacy!!.posterityEarned, 1e-9)
        assertEquals(expected, dead.posterity, 1e-9)
    }

    @Test
    fun `era reached adds a one-time score bonus`() {
        assertEquals(400.0, GenerationManager.eraScoreBonus(Era.TRAIL, cfg), 1e-9)
        assertTrue(
            GenerationManager.eraScoreBonus(Era.CITY, cfg) > 30_000.0,
            "reaching the city is worth a real posterity bump",
        )
    }

    @Test
    fun `grinding one life forever has diminishing returns`() {
        // Same total score, earned as one long life vs. two lives: identical total posterity —
        // that's the sqrt: the SECOND half of the score is worth far less than the first.
        val oneLife = T.newState().copy(valueScoreThisMonument = 40_000.0)
        val oneDead = GenerationManager.endLife(oneLife, false, cfg, mutableListOf())

        val firstHalf = T.newState().copy(valueScoreThisMonument = 20_000.0)
        val firstDead = GenerationManager.endLife(firstHalf, false, cfg, mutableListOf())
        val secondHalf = GenerationManager.startNextGeneration(firstDead, cfg)
            .copy(valueScoreThisMonument = firstDead.valueScoreThisMonument + 20_000.0)
        val secondDead = GenerationManager.endLife(secondHalf, false, cfg, mutableListOf())

        // The era bonus is banked once per life, so allow that delta when comparing.
        val twoLivesTotal = secondDead.posterity
        val oneLifeTotal = oneDead.posterity
        assertTrue(twoLivesTotal >= oneLifeTotal, "splitting lives never loses posterity")
        val firstLifeEarn = firstDead.pendingLegacy!!.posterityEarned
        val secondLifeEarn = secondDead.pendingLegacy!!.posterityEarned
        assertTrue(
            secondLifeEarn < firstLifeEarn,
            "the same score earns less the second time (sqrt shape)",
        )
    }

    @Test
    fun `the founders gavel boosts each grant by half`() {
        val s = T.newState().copy(valueScoreThisMonument = 10_000.0)
        val plain = GenerationManager.endLife(s, false, cfg, mutableListOf())
        val gavel = GenerationManager.endLife(
            s.copy(heirlooms = setOf(HeirloomId.FOUNDERS_GAVEL)), false, cfg, mutableListOf(),
        )
        val raw = plain.pendingLegacy!!.posterityEarned
        assertEquals(floor(raw * 1.5), gavel.pendingLegacy!!.posterityEarned, 1e-9)
        assertEquals(
            plain.posterityEarnedThisMonument,
            gavel.posterityEarnedThisMonument,
            1e-9,
            "the raw formula counter ignores the gavel so the bonus never self-cancels",
        )
    }

    @Test
    fun `held posterity boosts all yields passively`() {
        val s = T.newState().copy(posterity = 75.0)
        assertEquals(1.0 + 0.02 * 75, YieldCalculator.globalFactor(s, cfg), 1e-12)
    }

    // -------------------------------------------------------------- ventures

    @Test
    fun `repeatable venture costs grow geometrically`() {
        val s = T.newState()
        assertEquals(15.0, PlayerActions.ventureCost(s, VentureId.SAWMILL_SHARE, cfg)!!, 1e-9)
        val ranked = s.copy(ventures = mapOf(VentureId.SAWMILL_SHARE to 3))
        assertEquals(15.0 * 1.7 * 1.7 * 1.7, PlayerActions.ventureCost(ranked, VentureId.SAWMILL_SHARE, cfg)!!, 1e-9)
    }

    @Test
    fun `tiered ventures run out`() {
        val maxed = T.newState().copy(ventures = mapOf(VentureId.OLD_ALMANAC to 1))
        assertEquals(null, PlayerActions.ventureCost(maxed, VentureId.OLD_ALMANAC, cfg))
        assertFalse(PlayerActions.canBuyVenture(maxed, VentureId.OLD_ALMANAC, cfg))
    }

    @Test
    fun `buying spends posterity and reduces the passive bonus - the classic tension`() {
        val rich = T.newState().copy(posterity = 100.0)
        val before = YieldCalculator.globalFactor(rich, cfg)
        val bought = PlayerActions.buyVenture(rich, VentureId.CATTLE_BRAND, cfg)
        assertEquals(85.0, bought.posterity, 1e-9)
        assertEquals(1, bought.ventureRank(VentureId.CATTLE_BRAND))
        assertTrue(YieldCalculator.globalFactor(bought, cfg) < before, "passive bonus dipped")
        assertEquals(
            1.5,
            YieldCalculator.ventureYieldFactor(bought, com.heirloom.engine.model.ResourceType.FOOD, cfg),
            1e-9,
            "...but food work gained +50%",
        )
    }

    @Test
    fun `you cannot buy what you cannot afford`() {
        val poor = T.newState().copy(posterity = 5.0)
        assertFalse(PlayerActions.canBuyVenture(poor, VentureId.SAWMILL_SHARE, cfg))
        assertFailsWith<IllegalArgumentException> {
            PlayerActions.buyVenture(poor, VentureId.SAWMILL_SHARE, cfg)
        }
    }

    @Test
    fun `ventures are buyable on the legacy screen`() {
        val dead = GenerationManager.endLife(
            T.newState().copy(posterity = 50.0, valueScoreThisMonument = 100.0),
            voluntary = false, cfg, mutableListOf(),
        )
        assertTrue(dead.isLegacyPending)
        assertTrue(PlayerActions.canBuyVenture(dead, VentureId.SAWMILL_SHARE, cfg))
        val bought = PlayerActions.buyVenture(dead, VentureId.SAWMILL_SHARE, cfg)
        assertEquals(1, bought.ventureRank(VentureId.SAWMILL_SHARE))
    }
}
