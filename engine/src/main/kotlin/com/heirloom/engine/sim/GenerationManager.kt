package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.EventLogEntry
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.LegacySummary
import com.heirloom.engine.model.LevelProgress
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.MechanicUnlock
import com.heirloom.engine.model.Pioneer
import com.heirloom.engine.model.Resources
import com.heirloom.engine.model.VentureId
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Death, voluntary retirement, the Posterity formula, and inheritance.
 *
 * Posterity (AdCap "angels" shape): a single cumulative value score grows across all
 * lives of a monument cycle, and floor(K * sqrt(score)) - alreadyEarned is paid out at
 * each death — so grinding one life forever has diminishing returns and the skill is
 * resetting at the right moment. [GameState.posterityEarnedThisMonument] tracks the RAW
 * formula payout; the Founder's Gavel bonus is applied on top of each grant so the
 * heirloom is a true +50% (see DECISIONS.md).
 */
object GenerationManager {

    /** Live death age: prosperity, food security, the Family Plot and a birth roll all matter. */
    fun deathAgeYears(state: GameState, config: BalanceConfig): Double {
        var age = config.baseDeathAgeYears
        age += config.deathAgeBonusPerEra * (state.maxEraThisLife.index - 1)
        if (!state.starvedThisLife) age += config.deathAgeFoodSecurityBonus
        age += config.familyPlotYearsPerTier * state.ventureRank(VentureId.FAMILY_PLOT)
        age += state.pioneer.deathAgeOffsetYears
        return age
    }

    fun checkDeath(state: GameState, config: BalanceConfig, logs: MutableList<EventLogEntry>): GameState =
        if (!state.isLegacyPending && state.ageYears(config) >= deathAgeYears(state, config)) {
            endLife(state, voluntary = false, config, logs)
        } else state

    fun canRetire(state: GameState, config: BalanceConfig): Boolean =
        !state.isLegacyPending && state.ageYears(config) >= config.retirementMinAgeYears

    fun retire(state: GameState, config: BalanceConfig, logs: MutableList<EventLogEntry>): GameState {
        require(canRetire(state, config)) { "Cannot retire before age ${config.retirementMinAgeYears}" }
        return endLife(state, voluntary = true, config, logs)
    }

    fun eraScoreBonus(era: Era, config: BalanceConfig): Double =
        config.eraScoreBonusBase * era.index.toDouble().pow(config.eraScoreBonusPower)

    /** Posterity that would be granted by ending the current life right now. */
    fun posterityPreview(state: GameState, config: BalanceConfig): Double {
        if (state.isLegacyPending) return 0.0
        val score = state.valueScoreThisMonument + eraScoreBonus(state.maxEraThisLife, config)
        return grantFromRawDelta(rawDelta(score, state.posterityEarnedThisMonument, config), state, config)
    }

    private fun rawDelta(score: Double, alreadyEarned: Double, config: BalanceConfig): Double =
        max(0.0, floor(config.posterityK * sqrt(score)) - alreadyEarned)

    private fun grantFromRawDelta(rawDelta: Double, state: GameState, config: BalanceConfig): Double {
        val gavel = if (state.hasHeirloom(HeirloomId.FOUNDERS_GAVEL)) {
            1.0 + config.heirloomEffects.foundersGavelPosterityBonus
        } else 1.0
        val renown = 1.0 + config.monumentPosterityBonusEach * state.monuments
        return floor(rawDelta * gavel * renown)
    }

    /** Ends the life (death or retirement): final heirloom check, posterity payout, Legacy screen. */
    fun endLife(
        state: GameState,
        voluntary: Boolean,
        config: BalanceConfig,
        logs: MutableList<EventLogEntry>,
    ): GameState {
        val heirloomsBefore = state.heirlooms
        var s = HeirloomChecker.check(state, config, logs)
        val newHeirlooms = s.heirlooms - heirloomsBefore

        val score = s.valueScoreThisMonument + eraScoreBonus(s.maxEraThisLife, config)
        val raw = rawDelta(score, s.posterityEarnedThisMonument, config)
        val granted = grantFromRawDelta(raw, s, config)

        logs += EventLogEntry(
            kind = if (voluntary) LogKind.RETIREMENT else LogKind.DEATH,
            atTotalGameDays = s.totalGameDays,
            generation = s.generation,
            detail = "age ${s.ageYears(config).toInt()}, ${s.maxEraThisLife.displayName}",
        )

        return s.copy(
            valueScoreThisMonument = score,
            posterityEarnedThisMonument = s.posterityEarnedThisMonument + raw,
            posterity = s.posterity + granted,
            pendingLegacy = LegacySummary(
                generation = s.generation,
                ageAtDeathYears = s.ageYears(config),
                voluntary = voluntary,
                eraReached = s.maxEraThisLife,
                posterityEarned = granted,
                heirloomsUnlockedThisLife = newHeirlooms,
                lifetimeEarned = s.lifetimeEarned,
                wintersSurvived = s.wintersSurvivedThisLife,
            ),
            stats = s.stats.copy(
                generationsCompleted = s.stats.generationsCompleted + 1,
                voluntaryRetirements = s.stats.voluntaryRetirements + (if (voluntary) 1 else 0),
                totalPosterityEarned = s.stats.totalPosterityEarned + granted,
            ),
        )
    }

    /** The era a fresh line starts in: Trail, unless the deed or Old Money say otherwise. */
    fun startingEra(state: GameState, config: BalanceConfig): Era {
        var era = Era.TRAIL
        if (state.hasHeirloom(HeirloomId.PROVEN_DEED)) era = config.heirloomEffects.provenDeedStartEra
        if (state.hasMechanic(MechanicUnlock.STARTING_ERA_BUMP, config)) era = era.next ?: era
        return era
    }

    /** Starting resources: a little trail food, plus whatever Letters West arranged ahead. */
    fun seedResources(state: GameState, config: BalanceConfig): Resources {
        var seed = config.newGameResources
        val letters = state.ventureRank(VentureId.LETTERS_WEST)
        if (letters > 0) {
            seed += config.lettersWestSeedBase * config.lettersWestSeedGrowthPerTier.pow(letters - 1)
        }
        return seed
    }

    /** The heir takes over. Skills, activities, era and resources reset; the line's prestige persists. */
    fun startNextGeneration(state: GameState, config: BalanceConfig): GameState {
        require(state.isLegacyPending) { "No legacy pending — the pioneer is still alive" }
        val rng = Random(state.rngSeed)
        val deathOffset = (rng.nextDouble() * 2.0 - 1.0) * config.deathAgeRandomRangeYears
        val newSeed = rng.nextLong()

        val startAge = config.startAgeYears -
            config.familyDoctorYearsYoungerPerTier * state.ventureRank(VentureId.FAMILY_DOCTOR)
        val startEra = startingEra(state, config)

        val toolLevels = config.toolShedLevelsPerTier * state.ventureRank(VentureId.TOOL_SHED)
        val activityProgress = if (toolLevels > 0) {
            ActivityId.entries.associateWith { LevelProgress(level = toolLevels) }
        } else emptyMap()

        // Keep the old selection when the heir can do that work from day one.
        val keepActivity = state.activeActivity?.takeIf {
            it.era.index <= startEra.index && (config.communityGates[it] ?: 0) == 0
        } ?: ActivityId.FORAGE

        return state.copy(
            rngSeed = newSeed,
            generation = state.generation + 1,
            dayOfLife = 0.0,
            pioneer = Pioneer(ageDays = startAge * config.daysPerYear, deathAgeOffsetYears = deathOffset),
            resources = seedResources(state, config),
            lifetimeEarned = Resources.ZERO,
            era = startEra,
            maxEraThisLife = startEra,
            activeActivity = keepActivity,
            activityProgress = activityProgress,
            skillProgress = emptyMap(),
            starving = false,
            starvedThisLife = false,
            foodFailedThisWinter = false,
            wintersSurvivedThisLife = 0,
            hardWinterActive = false,
            springBountyActive = false,
            activeEvents = emptyList(),
            railroadArrivedThisLife = false,
            pendingLegacy = null,
        )
    }
}
