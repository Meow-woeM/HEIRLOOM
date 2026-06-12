package com.heirloom.engine.runner

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.ResourceType
import com.heirloom.engine.model.Resources
import com.heirloom.engine.model.SkillId
import com.heirloom.engine.model.VentureId
import com.heirloom.engine.sim.CostCalculator
import com.heirloom.engine.sim.GenerationManager
import com.heirloom.engine.sim.OfflineProgressCalculator
import com.heirloom.engine.sim.PlayerActions
import com.heirloom.engine.sim.TickEngine
import com.heirloom.engine.sim.YieldCalculator
import kotlin.math.max

/** A player's daily habit: when they open the app, for how long, and how many ads they'll watch. */
data class CheckInPlan(
    val name: String,
    val checkInHours: List<Double>, // hours-of-day, ascending
    val minutesPerCheckIn: Double,
    val adsPerDay: Int,
)

data class GenerationRecord(
    val generation: Int,
    val endedOnRealDay: Int,
    val ageAtEnd: Double,
    val voluntary: Boolean,
    val eraReached: Era,
    val posterityEarned: Double,
    val posterityHeldAfterSpending: Double,
    val heirloomsTotal: Int,
    val monumentsTotal: Int,
)

data class DayRecord(
    val day: Int, // 1-based real-world day
    val generation: Int,
    val era: Era,
    val ageYears: Double,
    val posterity: Double,
    val heirlooms: Int,
    val monuments: Int,
    val decisionsToday: Int,
    val levelsGainedToday: Int,
)

data class SimulationReport(
    val plan: CheckInPlan,
    val daysSimulated: Int,
    val generations: List<GenerationRecord>,
    val days: List<DayRecord>,
    val milestones: Map<String, Int>, // milestone -> real day (1-based)
    val finalState: GameState,
)

/**
 * Simulates a greedy check-in player against the REAL balance: offline gaps between
 * check-ins (capped like the app), short live sessions, rewarded-ad boosts, venture
 * shopping, retirement timing, era pushes and monument founding. This is the tuning
 * instrument for the pacing acceptance criteria — see SIMULATION.md.
 */
class CheckInSimulator(
    private val config: BalanceConfig,
    private val plan: CheckInPlan,
    seed: Long = 1867L,
) {
    private var state: GameState = GameState.newGame(config, seed)
    private val generations = mutableListOf<GenerationRecord>()
    private val days = mutableListOf<DayRecord>()
    private val milestones = linkedMapOf<String, Int>()
    private var decisionsToday = 0
    private var levelsToday = 0

    fun run(realDays: Int): SimulationReport {
        var lastMomentSeconds = plan.checkInHours.first() * 3600.0
        session(realDay = 1) // day 1, first open: a brand-new game
        for (day in 1..realDays) {
            for ((index, hour) in plan.checkInHours.withIndex()) {
                if (day == 1 && index == 0) continue // already played the first session
                val now = ((day - 1) * 24.0 + hour) * 3600.0
                val gap = now - lastMomentSeconds
                lastMomentSeconds = now
                offline(gap)
                session(day)
            }
            recordDay(day)
        }
        return SimulationReport(plan, realDays, generations, days, milestones, state)
    }

    // ------------------------------------------------------------------ flow

    private fun offline(gapSeconds: Double) {
        val before = levelSum()
        state = OfflineProgressCalculator.apply(state, gapSeconds, config).state
        levelsToday += levelSum() - before
    }

    private fun session(realDay: Int) {
        handleDecisions(realDay)
        if (plan.adsPerDay > 0) {
            var guard = plan.adsPerDay
            while (guard-- > 0 && PlayerActions.canWatchAd(state, realDay.toLong(), config)) {
                val before = levelSum()
                state = PlayerActions.watchRewardedAd(state, realDay.toLong(), config).state
                levelsToday += levelSum() - before
                handleDecisions(realDay)
            }
        }
        // A couple of live minutes watching the numbers move.
        if (!state.isLegacyPending) {
            val liveDays = plan.minutesPerCheckIn * 60.0 * config.gameDaysPerRealSecond
            state = TickEngine.tick(state, liveDays, config).state
        }
        handleDecisions(realDay)
        // Set the table for the away time ahead.
        if (!state.isLegacyPending) {
            chooseSkill()
            chooseActivity()
        }
        trackMilestones(realDay)
    }

    /** The decision loop: inherit, found, advance, retire, shop — until nothing's left to do. */
    private fun handleDecisions(realDay: Int) {
        var guard = 64
        while (guard-- > 0) {
            when {
                state.isLegacyPending -> {
                    val legacy = state.pendingLegacy!!
                    spendPosterity()
                    generations += GenerationRecord(
                        generation = legacy.generation,
                        endedOnRealDay = realDay,
                        ageAtEnd = legacy.ageAtDeathYears,
                        voluntary = legacy.voluntary,
                        eraReached = legacy.eraReached,
                        posterityEarned = legacy.posterityEarned,
                        posterityHeldAfterSpending = state.posterity,
                        heirloomsTotal = state.heirlooms.size,
                        monumentsTotal = state.monuments,
                    )
                    state = PlayerActions.startNextGeneration(state, config)
                    decisionsToday++
                }

                PlayerActions.canFoundMonument(state, config) -> {
                    val founder = state.generation
                    state = PlayerActions.foundMonument(state, config)
                    generations += GenerationRecord(
                        generation = founder,
                        endedOnRealDay = realDay,
                        ageAtEnd = 0.0,
                        voluntary = true,
                        eraReached = Era.CITY,
                        posterityEarned = 0.0,
                        posterityHeldAfterSpending = 0.0,
                        heirloomsTotal = state.heirlooms.size,
                        monumentsTotal = state.monuments,
                    )
                    decisionsToday++
                }

                PlayerActions.canAdvanceEra(state, config) -> {
                    state = PlayerActions.advanceEra(state, config)
                    decisionsToday++
                }

                shouldRetire() -> {
                    state = PlayerActions.retire(state, config)
                    decisionsToday++
                }

                else -> {
                    val bought = spendPosterity()
                    if (bought == 0) return
                }
            }
        }
    }

    private fun shouldRetire(): Boolean {
        if (!PlayerActions.canRetire(state, config)) return false
        // A life that reaches the railroad commits to the City push and the Monument.
        if (state.era.index >= Era.RAILROAD.index) return false
        val preview = GenerationManager.posterityPreview(state, config)
        // Patient foundational lives while the line is young; once established, cycle
        // faster — frequent well-timed resets beat long grinds (the sqrt at work).
        val bar = if (state.posterity < 500.0) 0.9 else 0.4
        return preview >= max(30.0, bar * state.posterity)
    }

    /** Greedy shopping: cheapest venture that costs at most 35% of held posterity, repeat. */
    private fun spendPosterity(): Int {
        var bought = 0
        var guard = 64
        while (guard-- > 0) {
            val pick = VentureId.entries
                .mapNotNull { id ->
                    PlayerActions.ventureCost(state, id, config)
                        ?.takeIf { it <= state.posterity * 0.35 }
                        ?.let { id to it }
                }
                .minByOrNull { it.second } ?: break
            state = PlayerActions.buyVenture(state, pick.first, config)
            bought++
            decisionsToday++
        }
        return bought
    }

    // ------------------------------------------------------------- strategy

    private fun unlockedActivities(): List<ActivityId> =
        ActivityId.entries.filter { PlayerActions.isActivityUnlocked(state, it, config) }

    private fun bestActivityFor(resource: ResourceType): ActivityId? =
        unlockedActivities().filter { it.resource == resource }
            .maxByOrNull { YieldCalculator.dailyYield(state, it, config) }

    /**
     * Food first (enough to ride out the next away-stretch), then grind whichever
     * resource of the next big purchase (era or monument) takes longest to finish.
     */
    private fun chooseActivity() {
        // Early on, era pushes beat food security (starving only halves yields); once the
        // household grows, keep ~a banked year of winter-rate upkeep for the away-stretch.
        val springUpkeep = YieldCalculator.dailyFoodUpkeep(state.copy(totalGameDays = 0.0), config)
        val reserveTarget = springUpkeep *
            if (state.era.index <= 2) 120.0 else 450.0 * config.winterFoodDrainMultiplier
        if (state.resources.food < reserveTarget) {
            bestActivityFor(ResourceType.FOOD)?.let { select(it); return }
        }
        val target: Resources = when {
            state.era == Era.CITY -> CostCalculator.monumentCost(state, config)
            state.era.next != null -> CostCalculator.eraCost(state, state.era.next!!, config)
            else -> Resources.ZERO
        }
        val pick = listOf(ResourceType.MATERIALS, ResourceType.MONEY, ResourceType.STANDING)
            .mapNotNull { r ->
                val deficit = target[r] - state.resources[r]
                if (deficit <= 0.0) return@mapNotNull null
                val activity = bestActivityFor(r) ?: return@mapNotNull null
                val rate = YieldCalculator.dailyYield(state, activity, config)
                Triple(r, activity, deficit / rate)
            }
            .maxByOrNull { it.third }
        when {
            pick != null -> select(pick.second)
            else -> bestActivityFor(ResourceType.MONEY)?.let { select(it) }
        }
    }

    private fun select(activity: ActivityId) {
        if (state.activeActivity != activity) {
            state = PlayerActions.selectActivity(state, activity, config)
            decisionsToday++
        }
    }

    /** Standing gates first when they block era work, then the heirloom skill ladder. */
    private fun chooseSkill() {
        val priorities = buildList {
            val gateNeeded = config.communityGates
                .filterKeys { it.era.index <= state.era.index }
                .values.maxOrNull() ?: 0
            if (state.skillLevel(SkillId.COMMUNITY) < gateNeeded) add(SkillId.COMMUNITY)
            if (state.skillLevel(SkillId.CRAFTSMANSHIP) < config.heirloomCraftSkillRequired) add(SkillId.CRAFTSMANSHIP)
            if (state.skillLevel(SkillId.GRIT) < config.heirloomGritRequired) add(SkillId.GRIT)
            if (state.skillLevel(SkillId.THRIFT) < config.heirloomLedgerThriftRequired) add(SkillId.THRIFT)
            if (state.skillLevel(SkillId.COMMUNITY) < config.heirloomQuiltCommunityRequired) add(SkillId.COMMUNITY)
            add(SkillId.HUSBANDRY)
        }
        if (state.activeSkill != priorities.first()) {
            state = PlayerActions.selectSkill(state, priorities.first())
            decisionsToday++
        }
        if (PlayerActions.canUseSecondSkill(state, config)) {
            val second = priorities.drop(1).firstOrNull { it != state.activeSkill }
            if (second != null && state.secondSkill != second) {
                state = PlayerActions.selectSecondSkill(state, second, config)
            }
        }
    }

    // ----------------------------------------------------------- bookkeeping

    private fun levelSum(): Int =
        state.activityProgress.values.sumOf { it.level } + state.skillProgress.values.sumOf { it.level }

    private fun trackMilestones(realDay: Int) {
        fun first(key: String, condition: Boolean) {
            if (condition && key !in milestones) milestones[key] = realDay
        }
        first("First Posterity earned", state.stats.totalPosterityEarned > 0)
        first("First Family Venture", generations.isNotEmpty() && state.ventures.isNotEmpty())
        Era.entries.filter { it != Era.TRAIL }.forEach { era ->
            first("Era reached: ${era.displayName}", state.maxEraEver.index >= era.index)
        }
        (1..config.finalMonument).forEach { n ->
            first("Monument $n", state.monuments >= n)
        }
        first("All 12 heirlooms", state.heirlooms.size == com.heirloom.engine.model.HeirloomId.entries.size)
        first("COMPLETION (final Monument + all heirlooms)",
            state.monuments >= config.finalMonument &&
                state.heirlooms.size == com.heirloom.engine.model.HeirloomId.entries.size)
    }

    private fun recordDay(day: Int) {
        days += DayRecord(
            day = day,
            generation = state.generation,
            era = state.era,
            ageYears = state.ageYears(config),
            posterity = state.posterity,
            heirlooms = state.heirlooms.size,
            monuments = state.monuments,
            decisionsToday = decisionsToday,
            levelsGainedToday = levelsToday,
        )
        decisionsToday = 0
        levelsToday = 0
    }
}
