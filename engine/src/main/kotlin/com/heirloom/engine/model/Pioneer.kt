package com.heirloom.engine.model

import com.heirloom.engine.balance.BalanceConfig
import kotlinx.serialization.Serializable

/**
 * The currently living pioneer. Age is stored in game-days so ticking stays simple;
 * [deathAgeOffsetYears] is rolled once at birth (the ± in the death formula), while the
 * era/food-security/venture bonuses are evaluated live in GenerationManager.
 */
@Serializable
data class Pioneer(
    val ageDays: Double,
    val deathAgeOffsetYears: Double = 0.0,
) {
    fun ageYears(config: BalanceConfig): Double = ageDays / config.daysPerYear
}
