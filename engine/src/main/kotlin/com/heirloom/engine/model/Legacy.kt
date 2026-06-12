package com.heirloom.engine.model

import kotlinx.serialization.Serializable

/**
 * Set on the state when a pioneer dies or retires. While present, ticking is frozen:
 * the player is on the Legacy screen, spending Posterity and starting the heir.
 */
@Serializable
data class LegacySummary(
    val generation: Int,
    val ageAtDeathYears: Double,
    val voluntary: Boolean,
    val eraReached: Era,
    val posterityEarned: Double,
    val heirloomsUnlockedThisLife: Set<HeirloomId> = emptySet(),
    val lifetimeEarned: Resources = Resources.ZERO,
    val wintersSurvived: Int = 0,
)

/** Aggregate save statistics (Legacy screen, achievements, simulation output). */
@Serializable
data class SaveStats(
    val generationsCompleted: Int = 0,
    val voluntaryRetirements: Int = 0,
    val eventsFired: Int = 0,
    val wintersSurvivedTotal: Int = 0,
    val totalPosterityEarned: Double = 0.0,
    val adsWatched: Int = 0,
)
