package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.Resources
import com.heirloom.engine.model.SkillId
import com.heirloom.engine.model.VentureId
import kotlin.math.pow

/** Cost reductions: Thrift (soft-capped divisor), Ledger & Quill, Craftsmanship (materials only). */
object CostCalculator {

    /** Applies to every cost in the game, including food upkeep. */
    fun costFactor(state: GameState, config: BalanceConfig): Double {
        var factor = 1.0 / (1.0 + config.thriftCostReductionPerLevel * state.skillLevel(SkillId.THRIFT))
        if (state.hasHeirloom(HeirloomId.LEDGER_AND_QUILL)) factor *= config.heirloomEffects.ledgerCostFactor
        return factor
    }

    /** Craftsmanship makes building cheaper: the materials share of structured costs only. */
    fun materialsCostFactor(state: GameState, config: BalanceConfig): Double =
        (1.0 - config.craftsmanshipCostReductionPerLevel).pow(state.skillLevel(SkillId.CRAFTSMANSHIP))

    /** Effective cost to advance INTO [target], with all reductions applied. */
    fun eraCost(state: GameState, target: Era, config: BalanceConfig): Resources {
        val base = config.eraCosts[target]
            ?: throw IllegalArgumentException("No advancement cost for $target")
        var cost = base * costFactor(state, config)
        if (state.ventureRank(VentureId.HOMESTEAD_ACT_FILING) > 0) cost *= config.homesteadActEraCostFactor
        return cost.copy(materials = cost.materials * materialsCostFactor(state, config))
    }

    /** Cost of the next Monument (the nth costs base * growth^n). */
    fun monumentCost(state: GameState, config: BalanceConfig): Resources {
        val cost = config.monumentBaseCost * config.monumentCostGrowth.pow(state.monuments) *
            costFactor(state, config)
        return cost.copy(materials = cost.materials * materialsCostFactor(state, config))
    }
}
