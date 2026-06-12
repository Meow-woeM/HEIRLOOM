package com.heirloom.engine.balance

import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.VentureId
import kotlin.test.Test
import kotlin.test.assertTrue

/** Structural guarantees over the shipped balance — catches accidental config edits. */
class BalanceSanityTest {
    private val cfg = BalanceConfig.DEFAULT

    @Test
    fun `every activity has a base yield`() {
        ActivityId.entries.forEach { id ->
            assertTrue(cfg.activityBaseYields.containsKey(id), "missing base yield for $id")
            assertTrue(cfg.activityBaseYields.getValue(id) > 0.0, "non-positive yield for $id")
        }
    }

    @Test
    fun `every venture has a spec and tiered ventures have costs`() {
        VentureId.entries.forEach { id ->
            val spec = cfg.ventureSpecs[id]
            assertTrue(spec != null, "missing venture spec for $id")
            assertTrue(spec!!.costForRank(0)!! > 0.0, "rank 0 of $id must cost something")
        }
    }

    @Test
    fun `era advancement costs grow 40x-80x per era`() {
        val eras = listOf(Era.CLAIM, Era.HOMESTEAD, Era.VILLAGE, Era.RAILROAD, Era.CITY)
        eras.zipWithNext().forEach { (from, to) ->
            val a = cfg.eraCosts.getValue(from)
            val b = cfg.eraCosts.getValue(to)
            val materialsRatio = b.materials / a.materials
            val moneyRatio = b.money / a.money
            assertTrue(materialsRatio in 40.0..80.0, "$from->$to materials ratio $materialsRatio outside 40-80")
            assertTrue(moneyRatio in 40.0..80.0, "$from->$to money ratio $moneyRatio outside 40-80")
        }
    }

    @Test
    fun `every era past the first has an advancement cost`() {
        Era.entries.filter { it != Era.TRAIL }.forEach { era ->
            assertTrue(cfg.eraCosts.containsKey(era), "missing era cost for $era")
        }
    }

    @Test
    fun `mechanic unlocks are unique and within the monument run`() {
        val unlocks = cfg.mechanicUnlocks
        assertTrue(unlocks.values.distinct().size == unlocks.size, "duplicate mechanic unlock")
        assertTrue(unlocks.keys.all { it in 1..cfg.finalMonument }, "unlock outside 1..finalMonument")
    }

    @Test
    fun `skill xp curve uses the specified growth`() {
        assertTrue(cfg.skillXpGrowth == 1.12, "spec pins skill XP growth at 1.12")
        assertTrue(cfg.activityLevelYieldBonus == 0.08, "spec pins activity self-speed at 8%/level")
    }
}
