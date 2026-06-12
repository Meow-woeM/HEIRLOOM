package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.EventLogEntry
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.LegacySummary
import com.heirloom.engine.model.LogKind

/**
 * Monuments — the deep reset. Reaching Era 6 unlocks the purchase; founding one erects
 * a statue in the Town Vista forever, grants +100% all yields, and "completes" the line:
 * Posterity, Family Ventures and era progress reset; heirlooms and the vista persist.
 *
 * Founding immediately starts a fresh line (no Legacy screen — posterity was just
 * zeroed, so there would be nothing to spend; the celebration replaces it).
 */
object MonumentManager {

    fun isComplete(state: GameState, config: BalanceConfig): Boolean =
        state.monuments >= config.finalMonument

    fun canFound(state: GameState, config: BalanceConfig): Boolean =
        !state.isLegacyPending &&
            state.era == Era.CITY &&
            state.resources.covers(CostCalculator.monumentCost(state, config))

    fun found(state: GameState, config: BalanceConfig): GameState {
        require(canFound(state, config)) { "Cannot found a monument" }
        val cost = CostCalculator.monumentCost(state, config)
        val founded = state.monuments + 1
        val logs = listOf(
            EventLogEntry(
                kind = LogKind.MONUMENT_FOUNDED,
                atTotalGameDays = state.totalGameDays,
                generation = state.generation,
                detail = founded.toString(),
            ),
        )
        var s = state.copy(
            resources = state.resources - cost,
            monuments = founded,
            posterity = 0.0,
            posterityEarnedThisMonument = 0.0,
            valueScoreThisMonument = 0.0,
            ventures = emptyMap(),
            stats = state.stats.copy(generationsCompleted = state.stats.generationsCompleted + 1),
            // The founder passes into history; consumed immediately by startNextGeneration.
            pendingLegacy = LegacySummary(
                generation = state.generation,
                ageAtDeathYears = state.ageYears(config),
                voluntary = true,
                eraReached = state.maxEraThisLife,
                posterityEarned = 0.0,
                lifetimeEarned = state.lifetimeEarned,
                wintersSurvived = state.wintersSurvivedThisLife,
            ),
        )
        s = PlayerActions.appendLog(s, logs, config)
        return GenerationManager.startNextGeneration(s, config)
    }
}
