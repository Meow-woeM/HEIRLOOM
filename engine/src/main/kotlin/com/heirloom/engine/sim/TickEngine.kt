package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.EventLogEntry
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.LevelProgress
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.Season
import kotlin.math.floor
import kotlin.math.min

data class TickResult(val state: GameState, val newLogEntries: List<EventLogEntry>) {
    /** True when this tick ended the pioneer's life (the Legacy screen is now pending). */
    val lifeEnded: Boolean get() = state.isLegacyPending
}

/**
 * The heart of the engine: a pure fast-forward function. The same code runs the live
 * 1-second UI loop and offline catch-up — only the dt differs.
 *
 * Time is advanced in sub-steps that never span a day boundary; production, upkeep, XP
 * and aging integrate continuously within a step, while discrete systems (season
 * transitions, event rolls, heirloom checks, automation) fire on the boundary. Ticking
 * freezes while a Legacy screen is pending and any leftover dt is discarded.
 */
object TickEngine {
    private const val EPS = 1e-9

    fun tick(state: GameState, gameDays: Double, config: BalanceConfig): TickResult {
        if (gameDays <= EPS || state.isLegacyPending) return TickResult(state, emptyList())
        var s = state
        val logs = mutableListOf<EventLogEntry>()
        var remaining = gameDays
        while (remaining > EPS && !s.isLegacyPending) {
            // Boundaries are snapped to exactly when processed (below), so a sub-EPS gap
            // can only mean "already handled" — float drift never skips or doubles a day.
            var nextBoundary = floor(s.totalGameDays) + 1.0
            if (nextBoundary - s.totalGameDays < EPS) nextBoundary += 1.0
            val step = min(remaining, nextBoundary - s.totalGameDays)
            s = advance(s, step, config)
            remaining -= step
            if (!s.isLegacyPending && s.totalGameDays >= nextBoundary - EPS) {
                s = s.copy(totalGameDays = nextBoundary)
                s = dayBoundary(s, config, logs)
            }
            if (!s.isLegacyPending) {
                s = GenerationManager.checkDeath(s, config, logs)
            }
        }
        if (logs.isNotEmpty()) {
            s = s.copy(eventLog = (s.eventLog + logs).takeLast(config.eventLogMaxEntries))
        }
        return TickResult(s, logs)
    }

    /** Continuous integration of one sub-step. [dt] never spans a day boundary. */
    private fun advance(s: GameState, dt: Double, config: BalanceConfig): GameState {
        var resources = s.resources
        var lifetimeEarned = s.lifetimeEarned
        var valueScore = s.valueScoreThisMonument

        // Production (uses the starving/season state at the start of the step).
        val activity = s.activeActivity
        var activityProgress = s.activityProgress
        if (activity != null) {
            val amount = YieldCalculator.dailyYield(s, activity, config) * dt
            resources = resources.add(activity.resource, amount)
            lifetimeEarned = lifetimeEarned.add(activity.resource, amount)
            valueScore += amount * config.valueScoreWeights.getValue(activity.resource)

            val (progress, _) = Curves.addXp(
                activityProgress[activity] ?: LevelProgress(),
                config.activityXpPerDay * dt,
            ) { Curves.xpToNextActivityLevel(it, config) }
            activityProgress = activityProgress + (activity to progress)
        }

        // Food upkeep. Hitting zero starves the family: yields halve and food security is lost.
        val upkeep = YieldCalculator.dailyFoodUpkeep(s, config) * dt
        var food = resources.food - upkeep
        var starving = false
        var starvedThisLife = s.starvedThisLife
        var foodFailedThisWinter = s.foodFailedThisWinter
        if (food <= 0.0) {
            food = 0.0
            starving = true
            starvedThisLife = true
            if (s.season(config) == Season.WINTER) foodFailedThisWinter = true
        }
        resources = resources.copy(food = food)

        // Skill training.
        var skillProgress = s.skillProgress
        s.activeSkill?.let { skill ->
            val xp = YieldCalculator.skillXpPerDay(s, skill, isSecondSlot = false, config) * dt
            val (progress, _) = Curves.addXp(skillProgress[skill] ?: LevelProgress(), xp) {
                Curves.xpToNextSkillLevel(it, config)
            }
            skillProgress = skillProgress + (skill to progress)
        }
        s.secondSkill?.let { skill ->
            val xp = YieldCalculator.skillXpPerDay(s, skill, isSecondSlot = true, config) * dt
            val (progress, _) = Curves.addXp(skillProgress[skill] ?: LevelProgress(), xp) {
                Curves.xpToNextSkillLevel(it, config)
            }
            skillProgress = skillProgress + (skill to progress)
        }

        return s.copy(
            totalGameDays = s.totalGameDays + dt,
            dayOfLife = s.dayOfLife + dt,
            pioneer = s.pioneer.copy(ageDays = s.pioneer.ageDays + dt),
            resources = resources,
            lifetimeEarned = lifetimeEarned,
            valueScoreThisMonument = valueScore,
            activityProgress = activityProgress,
            skillProgress = skillProgress,
            starving = starving,
            starvedThisLife = starvedThisLife,
            foodFailedThisWinter = foodFailedThisWinter,
        )
    }

    /** Discrete once-per-day systems. Runs with totalGameDays sitting on an integer day. */
    private fun dayBoundary(s: GameState, config: BalanceConfig, logs: MutableList<EventLogEntry>): GameState {
        var state = s
        val today = floor(state.totalGameDays + EPS)
        val newSeason = Season.atDay(today, config)
        val prevSeason = Season.atDay(today - 1.0, config)
        if (newSeason != prevSeason) {
            state = seasonTransition(state, prevSeason, newSeason, config, logs)
        }
        state = EventSystem.onDayBoundary(state, config, logs)
        state = HeirloomChecker.check(state, config, logs)
        state = Automation.onDayBoundary(state, config)
        return state
    }

    private fun seasonTransition(
        s: GameState,
        prev: Season,
        new: Season,
        config: BalanceConfig,
        logs: MutableList<EventLogEntry>,
    ): GameState {
        var state = s
        when {
            // Winter begins: start tracking food security for the Spring Bounty.
            new == Season.WINTER -> state = state.copy(foodFailedThisWinter = false)

            // Winter ends: count survival, grant the bounty, and let hard winters thaw.
            prev == Season.WINTER -> {
                val survived = !state.foodFailedThisWinter
                if (survived) {
                    logs += EventLogEntry(
                        kind = LogKind.SPRING_BOUNTY,
                        atTotalGameDays = state.totalGameDays,
                        generation = state.generation,
                    )
                    state = state.copy(
                        wintersSurvivedThisLife = state.wintersSurvivedThisLife + 1,
                        springBountyActive = true,
                        stats = state.stats.copy(wintersSurvivedTotal = state.stats.wintersSurvivedTotal + 1),
                    )
                }
                state = state.copy(
                    hardWinterActive = false,
                    activeEvents = state.activeEvents.filter { it.type != com.heirloom.engine.model.EventType.HARD_WINTER },
                    foodFailedThisWinter = false,
                )
            }

            // Spring ends: the bounty (if any) is spent.
            prev == Season.SPRING -> state = state.copy(springBountyActive = false)
        }
        return state
    }
}
