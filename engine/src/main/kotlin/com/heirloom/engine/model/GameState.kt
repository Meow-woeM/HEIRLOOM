package com.heirloom.engine.model

import com.heirloom.engine.balance.BalanceConfig
import kotlinx.serialization.Serializable

/**
 * The single source of truth. Immutable; every system is a pure function
 * (GameState, ...) -> GameState. Serialized as versioned JSON (see SaveCodec).
 */
@Serializable
data class GameState(
    val saveVersion: Int = 1,
    val rngSeed: Long,

    // ---- time ----
    /** World days since the save began. Never resets; drives the season calendar. */
    val totalGameDays: Double = 0.0,
    /** Days since this pioneer took over. */
    val dayOfLife: Double = 0.0,
    /** Wall-clock millis at last save; the app uses it to compute offline time. */
    val lastRealTimeMillis: Long = 0L,

    // ---- pioneer & line ----
    val generation: Int = 1,
    val pioneer: Pioneer,

    // ---- resources ----
    val resources: Resources = Resources.ZERO,
    /** Earned (produced) this life; feeds heirloom conditions and the legacy summary. */
    val lifetimeEarned: Resources = Resources.ZERO,

    // ---- era ----
    val era: Era = Era.TRAIL,
    val maxEraThisLife: Era = Era.TRAIL,
    /** Highest era any generation ever reached. Drives the Town Vista; never resets. */
    val maxEraEver: Era = Era.TRAIL,

    // ---- active work ----
    val activeActivity: ActivityId? = ActivityId.FORAGE,
    val activeSkill: SkillId? = SkillId.GRIT,
    /** Second concurrent skill; requires the SECOND_SKILL monument mechanic. */
    val secondSkill: SkillId? = null,
    val activityProgress: Map<ActivityId, LevelProgress> = emptyMap(),
    val skillProgress: Map<SkillId, LevelProgress> = emptyMap(),

    // ---- survival & seasons ----
    val starving: Boolean = false,
    val starvedThisLife: Boolean = false,
    val foodFailedThisWinter: Boolean = false,
    val wintersSurvivedThisLife: Int = 0,
    val hardWinterActive: Boolean = false,
    val springBountyActive: Boolean = false,

    // ---- events ----
    val activeEvents: List<ActiveEvent> = emptyList(),
    val eventLog: List<EventLogEntry> = emptyList(),
    val railroadArrivedThisLife: Boolean = false,

    // ---- prestige ----
    val heirlooms: Set<HeirloomId> = emptySet(),
    /** Currently held Posterity. Each point grants a passive yield bonus; spending reduces it. */
    val posterity: Double = 0.0,
    val posterityEarnedThisMonument: Double = 0.0,
    /** Cumulative weighted lifetime earnings (all lives this monument cycle); feeds the posterity formula. */
    val valueScoreThisMonument: Double = 0.0,
    val ventures: Map<VentureId, Int> = emptyMap(),
    val monuments: Int = 0,

    // ---- automation (monument mechanic) ----
    val automationEnabled: Boolean = false,
    val automationPriorities: List<ActivityId> = emptyList(),

    // ---- legacy flow ----
    /** Non-null while the Legacy screen is up. Ticking is frozen until the heir starts. */
    val pendingLegacy: LegacySummary? = null,

    // ---- monetization & meta ----
    val supporter: Boolean = false,
    val adsUsedToday: Int = 0,
    val lastAdEpochDay: Long = 0L,

    val stats: SaveStats = SaveStats(),
) {
    val isLegacyPending: Boolean get() = pendingLegacy != null

    fun ageYears(config: BalanceConfig): Double = pioneer.ageYears(config)

    fun season(config: BalanceConfig): Season = Season.atDay(totalGameDays, config)

    fun activityLevel(id: ActivityId): Int = activityProgress[id]?.level ?: 0

    fun skillLevel(id: SkillId): Int = skillProgress[id]?.level ?: 0

    fun ventureRank(id: VentureId): Int = ventures[id] ?: 0

    fun hasHeirloom(id: HeirloomId): Boolean = id in heirlooms

    fun hasMechanic(unlock: MechanicUnlock, config: BalanceConfig): Boolean =
        config.mechanicUnlocks.entries.any { (count, mech) -> mech == unlock && monuments >= count }

    val vistaStage: VistaStage
        get() = when {
            monuments >= 6 -> VistaStage.FUTURE_CITY
            monuments >= 5 -> VistaStage.MODERN_CITY
            monuments >= 3 -> VistaStage.OLD_CITY
            monuments >= 1 -> VistaStage.FOUNDED_CITY
            maxEraEver.index >= Era.RAILROAD.index -> VistaStage.FRONTIER_TOWN
            maxEraEver.index >= Era.VILLAGE.index -> VistaStage.VILLAGE
            maxEraEver.index >= Era.HOMESTEAD.index -> VistaStage.HOMESTEAD
            else -> VistaStage.WAGON_CAMP
        }

    companion object {
        /** A brand-new save: generation 1 steps off the trail at age 18 in spring. */
        fun newGame(config: BalanceConfig, rngSeed: Long = 0L): GameState {
            val rng = kotlin.random.Random(rngSeed)
            val offset = (rng.nextDouble() * 2.0 - 1.0) * config.deathAgeRandomRangeYears
            return GameState(
                rngSeed = rng.nextLong(),
                pioneer = Pioneer(
                    ageDays = config.startAgeYears * config.daysPerYear,
                    deathAgeOffsetYears = offset,
                ),
                resources = config.newGameResources,
            )
        }
    }
}
