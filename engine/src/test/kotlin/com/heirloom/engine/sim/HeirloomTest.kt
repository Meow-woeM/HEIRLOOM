package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.LevelProgress
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.Pioneer
import com.heirloom.engine.model.SkillId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeirloomTest {
    private val cfg = T.quiet

    private fun met(id: HeirloomId, state: GameState) = HeirloomChecker.conditionMet(id, state, cfg)

    @Test
    fun `every condition triggers at its threshold and not before`() {
        val s = T.newState()

        assertFalse(met(HeirloomId.FATHERS_TOOLS, s))
        assertTrue(met(HeirloomId.FATHERS_TOOLS, s.copy(skillProgress = mapOf(SkillId.CRAFTSMANSHIP to LevelProgress(25)))))

        assertFalse(met(HeirloomId.FAMILY_BIBLE, s))
        assertTrue(met(HeirloomId.FAMILY_BIBLE, s.copy(pioneer = Pioneer(50.0 * cfg.daysPerYear))))

        assertFalse(met(HeirloomId.MOTHERS_RECIPES, s.copy(wintersSurvivedThisLife = 4)))
        assertTrue(met(HeirloomId.MOTHERS_RECIPES, s.copy(wintersSurvivedThisLife = 5)))

        assertFalse(met(HeirloomId.PROVEN_DEED, s.copy(maxEraThisLife = Era.CLAIM)))
        assertTrue(met(HeirloomId.PROVEN_DEED, s.copy(maxEraThisLife = Era.HOMESTEAD)))
        assertTrue(met(HeirloomId.IRON_STOVE, s.copy(maxEraThisLife = Era.VILLAGE)))
        assertTrue(met(HeirloomId.RAILROAD_SHARES, s.copy(maxEraThisLife = Era.RAILROAD)))
        assertTrue(met(HeirloomId.FOUNDERS_GAVEL, s.copy(maxEraThisLife = Era.CITY)))

        assertFalse(met(HeirloomId.HUNTING_RIFLE, s))
        assertTrue(met(HeirloomId.HUNTING_RIFLE, s.copy(lifetimeEarned = s.lifetimeEarned.copy(food = 10_000.0))))

        assertTrue(met(HeirloomId.OXEN_YOKE, s.copy(skillProgress = mapOf(SkillId.GRIT to LevelProgress(25)))))
        assertTrue(met(HeirloomId.QUILT_OF_MANY_HANDS, s.copy(skillProgress = mapOf(SkillId.COMMUNITY to LevelProgress(25)))))
        assertTrue(met(HeirloomId.LEDGER_AND_QUILL, s.copy(skillProgress = mapOf(SkillId.THRIFT to LevelProgress(25)))))
        assertTrue(met(HeirloomId.PIONEERS_JOURNAL, s.copy(pioneer = Pioneer(70.0 * cfg.daysPerYear))))
    }

    @Test
    fun `check unlocks once and writes the chronicle`() {
        val s = T.newState().copy(skillProgress = mapOf(SkillId.CRAFTSMANSHIP to LevelProgress(25)))
        val logs = mutableListOf<com.heirloom.engine.model.EventLogEntry>()
        val unlocked = HeirloomChecker.check(s, cfg, logs)
        assertTrue(HeirloomId.FATHERS_TOOLS in unlocked.heirlooms)
        assertEquals(1, logs.count { it.kind == LogKind.HEIRLOOM_UNLOCKED })

        val again = HeirloomChecker.check(unlocked, cfg, logs)
        assertEquals(unlocked, again, "no double unlocks")
        assertEquals(1, logs.count { it.kind == LogKind.HEIRLOOM_UNLOCKED })
    }

    @Test
    fun `heirlooms unlock mid-life through the tick loop`() {
        // Train craftsmanship on materials work; XP 1.5/day. Level 25 needs ~666 XP -> ~444 days.
        val s = T.newState().copy(
            activeActivity = com.heirloom.engine.model.ActivityId.SCOUT_AHEAD,
            activeSkill = SkillId.CRAFTSMANSHIP,
            resources = T.newState().resources.copy(food = 100_000.0),
        )
        val later = TickEngine.tick(s, 500.0, cfg).state
        assertTrue(later.skillLevel(SkillId.CRAFTSMANSHIP) >= 25)
        assertTrue(HeirloomId.FATHERS_TOOLS in later.heirlooms)
        assertTrue(later.eventLog.any { it.kind == LogKind.HEIRLOOM_UNLOCKED && it.detail == "FATHERS_TOOLS" })
    }

    @Test
    fun `the final breath still counts - conditions are re-checked at death`() {
        val s = T.newState().copy(
            pioneer = Pioneer(ageDays = 71.0 * cfg.daysPerYear), // past 70 without a boundary check
        )
        val dead = GenerationManager.endLife(s, voluntary = false, cfg, mutableListOf())
        assertTrue(HeirloomId.PIONEERS_JOURNAL in dead.heirlooms)
        assertTrue(HeirloomId.FAMILY_BIBLE in dead.heirlooms)
        assertTrue(HeirloomId.PIONEERS_JOURNAL in dead.pendingLegacy!!.heirloomsUnlockedThisLife)
    }

    @Test
    fun `ledger and quill trims every cost`() {
        val s = T.newState().copy(heirlooms = setOf(HeirloomId.LEDGER_AND_QUILL))
        assertEquals(0.9, CostCalculator.costFactor(s, cfg), 1e-12)
    }
}
