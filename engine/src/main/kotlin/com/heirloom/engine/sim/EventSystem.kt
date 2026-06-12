package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.ActiveEvent
import com.heirloom.engine.model.EventLogEntry
import com.heirloom.engine.model.EventType
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.MechanicUnlock
import com.heirloom.engine.model.ResourceType
import com.heirloom.engine.model.Season
import kotlin.random.Random

/**
 * Weighted random events, ~1 per season and more often in winter. v1 has no player
 * choices; with the EVENT_CHOICES monument mechanic the family "chooses wisely" and
 * negative events land softened ([ActiveEvent.mitigated]).
 *
 * RNG discipline: randomness is only consumed at day boundaries, seeded from
 * [GameState.rngSeed], and the advanced seed is written back — the whole engine
 * stays a deterministic function of (state, dt).
 */
object EventSystem {
    private const val EPS = 1e-9

    fun onDayBoundary(state: GameState, config: BalanceConfig, logs: MutableList<EventLogEntry>): GameState {
        var s = countDown(state)
        val rng = Random(s.rngSeed)
        val season = s.season(config)
        val chance = config.eventChancePerDay *
            (if (season == Season.WINTER) config.winterEventChanceMultiplier else 1.0)
        if (rng.nextDouble() < chance) {
            pick(rng, season, s, config)?.let { type -> s = fire(s, type, config, logs) }
        }
        return s.copy(rngSeed = rng.nextLong())
    }

    private fun countDown(state: GameState): GameState {
        if (state.activeEvents.isEmpty()) return state
        val remaining = state.activeEvents.mapNotNull { event ->
            val days = event.remainingDays - 1.0
            if (days > EPS) event.copy(remainingDays = days) else null
        }
        return state.copy(
            activeEvents = remaining,
            hardWinterActive = remaining.any { it.type == EventType.HARD_WINTER },
        )
    }

    private fun pick(rng: Random, season: Season, state: GameState, config: BalanceConfig): EventType? {
        val candidates = config.eventWeights.filter { (type, weight) ->
            weight > 0 && when (type) {
                EventType.HARD_WINTER -> season == Season.WINTER && state.activeEvents.none { it.type == type }
                EventType.DROUGHT, EventType.GOOD_RAINS ->
                    season != Season.WINTER && state.activeEvents.none { it.type == type }
                EventType.RAILROAD_ARRIVES -> false // fired by era advancement, never rolled
                else -> true
            }
        }
        if (candidates.isEmpty()) return null
        var roll = rng.nextDouble() * candidates.values.sum()
        for ((type, weight) in candidates) {
            roll -= weight
            if (roll <= 0) return type
        }
        return candidates.keys.last()
    }

    /** Applies an event. Lasting effects become [ActiveEvent]s; instant ones hit the wallet now. */
    fun fire(state: GameState, type: EventType, config: BalanceConfig, logs: MutableList<EventLogEntry>): GameState {
        val mitigated = type.isNegative && state.hasMechanic(MechanicUnlock.EVENT_CHOICES, config)
        val severity = if (mitigated) config.eventMitigationFactor else 1.0
        var s = when (type) {
            EventType.DROUGHT, EventType.GOOD_RAINS -> s(state) {
                copy(activeEvents = activeEvents + ActiveEvent(type, config.eventDurationDays, mitigated))
            }

            EventType.HARD_WINTER -> s(state) {
                copy(
                    activeEvents = activeEvents + ActiveEvent(type, config.eventDurationDays, mitigated),
                    hardWinterActive = true,
                )
            }

            EventType.LOCUSTS -> s(state) {
                copy(resources = resources.copy(food = resources.food * (1.0 - config.locustsFoodLossFraction * severity)))
            }

            EventType.TRAVELING_MERCHANT -> earn(state, ResourceType.MONEY, windfall(state, config, config.merchantWindfallDays), config)

            EventType.BARN_RAISING -> earn(state, ResourceType.STANDING, standingWindfall(state, config, config.barnRaisingStandingBase), config)

            EventType.NEWCOMERS -> earn(state, ResourceType.STANDING, standingWindfall(state, config, config.newcomersStandingBase), config)

            EventType.COUNTY_FAIR -> earn(
                earn(state, ResourceType.MONEY, windfall(state, config, config.countyFairMoneyDays), config),
                ResourceType.STANDING, standingWindfall(state, config, config.countyFairStandingBase), config,
            )

            EventType.RAILROAD_ARRIVES -> state.copy(railroadArrivedThisLife = true)
        }
        logs += EventLogEntry(
            kind = LogKind.RANDOM_EVENT,
            atTotalGameDays = s.totalGameDays,
            generation = s.generation,
            eventType = type,
            mitigated = mitigated,
        )
        return s.copy(stats = s.stats.copy(eventsFired = s.stats.eventsFired + 1))
    }

    private inline fun s(state: GameState, block: GameState.() -> GameState): GameState = state.block()

    /** Windfalls are real earnings: they count toward lifetime totals and the value score. */
    private fun earn(state: GameState, resource: ResourceType, amount: Double, config: BalanceConfig): GameState {
        if (amount <= 0.0) return state
        return state.copy(
            resources = state.resources.add(resource, amount),
            lifetimeEarned = state.lifetimeEarned.add(resource, amount),
            valueScoreThisMonument = state.valueScoreThisMonument +
                amount * config.valueScoreWeights.getValue(resource),
        )
    }

    /** "[days] worth of your current work, paid in coin" — scaled by value weights. */
    private fun windfall(state: GameState, config: BalanceConfig, days: Double): Double {
        val activity = state.activeActivity ?: return 0.0
        val daily = YieldCalculator.dailyYield(state, activity, config)
        val ratio = config.valueScoreWeights.getValue(activity.resource) /
            config.valueScoreWeights.getValue(ResourceType.MONEY)
        return days * daily * ratio
    }

    /**
     * Standing windfalls behave like standing *yields*: they scale with era, Community
     * skill, the Quilt, the Preacher's Circuit and the global multipliers — otherwise
     * standing (events are its only source before the Village era) could never keep
     * pace with era requirements as the line prospers.
     */
    private fun standingWindfall(state: GameState, config: BalanceConfig, base: Double): Double {
        val community = 1.0 + config.communityStandingYieldPerLevel *
            state.skillLevel(com.heirloom.engine.model.SkillId.COMMUNITY)
        return base * Math.pow(state.era.index.toDouble(), config.standingEventEraPower) *
            community *
            YieldCalculator.heirloomYieldFactor(state, ResourceType.STANDING, config) *
            YieldCalculator.ventureYieldFactor(state, ResourceType.STANDING, config) *
            YieldCalculator.globalFactor(state, config)
    }
}
