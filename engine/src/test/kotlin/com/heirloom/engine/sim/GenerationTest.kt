package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.LevelProgress
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.Pioneer
import com.heirloom.engine.model.SkillId
import com.heirloom.engine.model.VentureId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerationTest {
    private val cfg = T.quiet

    // -------------------------------------------------------------- death age

    @Test
    fun `death age composes base, era, food security, family plot and the birth roll`() {
        val s = T.newState().copy(
            pioneer = Pioneer(ageDays = 18.0 * cfg.daysPerYear, deathAgeOffsetYears = 2.0),
            maxEraThisLife = Era.HOMESTEAD,
            ventures = mapOf(VentureId.FAMILY_PLOT to 2),
        )
        // 60 + 2 eras beyond Trail + 3 food security + 10 plot + 2 roll
        assertEquals(77.0, GenerationManager.deathAgeYears(s, cfg), 1e-9)
        val starved = s.copy(starvedThisLife = true)
        assertEquals(74.0, GenerationManager.deathAgeYears(starved, cfg), 1e-9)
    }

    @Test
    fun `the pioneer dies when ticking past the death age`() {
        val nearDeath = T.newState().copy(
            pioneer = Pioneer(ageDays = 62.9 * cfg.daysPerYear, deathAgeOffsetYears = 0.0),
            resources = T.newState().resources.copy(food = 10_000.0),
        )
        // Death age: 60 + 3 (food security) = 63. A season is more than enough.
        val result = TickEngine.tick(nearDeath, 7.0, cfg)
        assertTrue(result.lifeEnded)
        val legacy = result.state.pendingLegacy!!
        assertFalse(legacy.voluntary)
        assertEquals(1, legacy.generation)
        assertTrue(result.state.eventLog.any { it.kind == LogKind.DEATH })
        assertTrue(result.state.totalGameDays < 7.0, "remaining tick time after death is discarded")
    }

    // ------------------------------------------------------------ retirement

    @Test
    fun `retirement opens at 40`() {
        val young = T.newState()
        assertFalse(GenerationManager.canRetire(young, cfg))
        assertFailsWith<IllegalArgumentException> { PlayerActions.retire(young, cfg) }

        val mature = young.copy(pioneer = Pioneer(ageDays = 40.0 * cfg.daysPerYear))
        assertTrue(GenerationManager.canRetire(mature, cfg))
        val retired = PlayerActions.retire(mature, cfg)
        assertTrue(retired.isLegacyPending)
        assertTrue(retired.pendingLegacy!!.voluntary)
        assertEquals(1, retired.stats.voluntaryRetirements)
        assertTrue(retired.eventLog.any { it.kind == LogKind.RETIREMENT })
    }

    // ----------------------------------------------------------- inheritance

    private fun deceased() = GenerationManager.endLife(
        TickEngine.tick(
            T.newState().copy(resources = T.newState().resources.copy(food = 5_000.0)),
            60.0, cfg,
        ).state.copy(pioneer = Pioneer(ageDays = 45.0 * cfg.daysPerYear)),
        voluntary = true, cfg, mutableListOf(),
    )

    @Test
    fun `the heir starts fresh at 18 in era 1 with seed food`() {
        val heir = GenerationManager.startNextGeneration(deceased(), cfg)
        assertEquals(2, heir.generation)
        assertEquals(18.0, heir.ageYears(cfg), 1e-9)
        assertEquals(0.0, heir.dayOfLife)
        assertEquals(Era.TRAIL, heir.era)
        assertEquals(cfg.newGameResources, heir.resources)
        assertTrue(heir.skillProgress.isEmpty(), "skills reset on death")
        assertTrue(heir.activityProgress.isEmpty(), "activities reset on death")
        assertFalse(heir.isLegacyPending)
        assertFalse(heir.starvedThisLife)
        assertEquals(0, heir.wintersSurvivedThisLife)
    }

    @Test
    fun `the world calendar and the line's prestige survive succession`() {
        val dead = deceased().copy(
            heirlooms = setOf(HeirloomId.MOTHERS_RECIPES),
            posterity = 80.0,
            monuments = 1,
            maxEraEver = Era.VILLAGE,
        )
        val heir = GenerationManager.startNextGeneration(dead, cfg)
        assertEquals(dead.totalGameDays, heir.totalGameDays, "world time keeps flowing")
        assertEquals(setOf(HeirloomId.MOTHERS_RECIPES), heir.heirlooms)
        assertEquals(80.0, heir.posterity)
        assertEquals(1, heir.monuments)
        assertEquals(Era.VILLAGE, heir.maxEraEver)
        assertEquals(dead.eventLog, heir.eventLog, "the family chronicle persists")
    }

    @Test
    fun `proven deed and old money bump the starting era`() {
        val withDeed = deceased().copy(heirlooms = setOf(HeirloomId.PROVEN_DEED))
        assertEquals(Era.CLAIM, GenerationManager.startNextGeneration(withDeed, cfg).era)

        val oldMoney = withDeed.copy(monuments = 2)
        val heir = GenerationManager.startNextGeneration(oldMoney, cfg)
        assertEquals(Era.HOMESTEAD, heir.era, "deed and the monument bump stack")
        assertEquals(Era.HOMESTEAD, heir.maxEraThisLife)
    }

    @Test
    fun `letters west tool shed and the family doctor shape the heir`() {
        val invested = deceased().copy(
            ventures = mapOf(
                VentureId.LETTERS_WEST to 2,
                VentureId.TOOL_SHED to 1,
                VentureId.FAMILY_DOCTOR to 2,
            ),
        )
        val heir = GenerationManager.startNextGeneration(invested, cfg)
        // Letters tier 2: base * 20^(2-1) on top of the trail rations.
        assertEquals(cfg.newGameResources.food + 50.0 * 20.0, heir.resources.food, 1e-9)
        assertEquals(40.0 * 20.0, heir.resources.materials, 1e-9)
        assertEquals(2, heir.activityLevel(ActivityId.FORAGE), "tool shed pre-levels every activity")
        assertEquals(14.0, heir.ageYears(cfg), 1e-9, "the doctor keeps heirs healthy younger")
    }

    @Test
    fun `succession needs a pending legacy`() {
        assertFailsWith<IllegalArgumentException> {
            GenerationManager.startNextGeneration(T.newState(), cfg)
        }
    }

    @Test
    fun `gated work is dropped on succession but plain work is kept`() {
        val miller = deceased().copy(
            activeActivity = ActivityId.TEACH_SCHOOL, // needs Community 5, which resets
            heirlooms = setOf(HeirloomId.PROVEN_DEED),
        )
        assertEquals(ActivityId.FORAGE, GenerationManager.startNextGeneration(miller, cfg).activeActivity)

        val gardener = deceased().copy(
            activeActivity = ActivityId.TEND_GARDEN, // Era 2 work, heir starts in Era 2
            heirlooms = setOf(HeirloomId.PROVEN_DEED),
        )
        assertEquals(ActivityId.TEND_GARDEN, GenerationManager.startNextGeneration(gardener, cfg).activeActivity)
    }
}
