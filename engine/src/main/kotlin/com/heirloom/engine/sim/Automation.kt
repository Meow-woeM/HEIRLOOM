package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.MechanicUnlock
import com.heirloom.engine.model.ResourceType

/**
 * Standing Orders (Monument 1 mechanic): auto-switch the active activity once per day.
 * A built-in safety rule keeps the larder stocked; otherwise the first unlocked entry
 * of the player's priority list wins.
 */
object Automation {

    fun onDayBoundary(state: GameState, config: BalanceConfig): GameState {
        if (!state.automationEnabled || !state.hasMechanic(MechanicUnlock.AUTOMATION, config)) return state
        val target = chooseActivity(state, config) ?: return state
        return if (target != state.activeActivity) state.copy(activeActivity = target) else state
    }

    fun chooseActivity(state: GameState, config: BalanceConfig): ActivityId? {
        val upkeep = YieldCalculator.dailyFoodUpkeep(state, config)
        if (state.resources.food < upkeep * config.automationFoodReserveDays) {
            bestFoodActivity(state, config)?.let { return it }
        }
        return state.automationPriorities.firstOrNull { PlayerActions.isActivityUnlocked(state, it, config) }
    }

    fun bestFoodActivity(state: GameState, config: BalanceConfig): ActivityId? =
        ActivityId.entries
            .filter { it.resource == ResourceType.FOOD && PlayerActions.isActivityUnlocked(state, it, config) }
            .maxByOrNull { YieldCalculator.dailyYield(state, it, config) }
}
