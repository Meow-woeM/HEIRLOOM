package com.heirloom.engine.runner

import com.heirloom.engine.balance.BalanceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The simulator drives every engine system at once — a cheap end-to-end integration net. */
class RunnerSmokeTest {

    private val plan = CheckInPlan("test", listOf(8.0, 13.5, 21.0), 2.0, adsPerDay = 0)

    @Test
    fun `three simulated days play through the whole loop`() {
        val report = CheckInSimulator(BalanceConfig.DEFAULT, plan, seed = 1867L).run(3)
        assertTrue(report.generations.isNotEmpty(), "at least one generation ends")
        assertTrue(report.finalState.stats.totalPosterityEarned > 0, "posterity flows")
        assertTrue(report.finalState.generation >= 2, "the line continues")
        assertEquals(3, report.days.size)
        assertTrue(report.days.all { it.decisionsToday + it.levelsGainedToday > 0 }, "no dead days")
    }

    @Test
    fun `day one delivers the kickoff promise`() {
        val report = CheckInSimulator(BalanceConfig.DEFAULT, plan, seed = 1867L).run(1)
        val gen1 = report.generations.firstOrNull { it.generation == 1 }
        assertTrue(gen1 != null, "generation 1 finishes on day 1")
        assertTrue(gen1.eraReached.index >= 2, "generation 1 gets off the trail")
        assertTrue(gen1.posterityEarned > 0, "first posterity earned on day 1")
    }

    @Test
    fun `the simulation is deterministic`() {
        val a = CheckInSimulator(BalanceConfig.DEFAULT, plan, seed = 7L).run(5)
        val b = CheckInSimulator(BalanceConfig.DEFAULT, plan, seed = 7L).run(5)
        assertEquals(a.finalState, b.finalState)
        assertEquals(a.milestones, b.milestones)
    }

    @Test
    fun `rewarded ads speed the same player up`() {
        val noAds = CheckInSimulator(BalanceConfig.DEFAULT, plan, seed = 7L).run(7)
        val withAds = CheckInSimulator(BalanceConfig.DEFAULT, plan.copy(adsPerDay = 3), seed = 7L).run(7)
        assertTrue(
            withAds.finalState.stats.totalPosterityEarned > noAds.finalState.stats.totalPosterityEarned,
            "ads must matter",
        )
    }
}
