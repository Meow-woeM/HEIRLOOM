package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.EventLogEntry
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.SkillId

/**
 * Heirloom conditions, evaluated once per game-day and again when a life ends.
 * All conditions are single-life feats; the unlocks themselves are forever.
 */
object HeirloomChecker {

    fun check(state: GameState, config: BalanceConfig, logs: MutableList<EventLogEntry>): GameState {
        val unlocked = HeirloomId.entries.filter { it !in state.heirlooms && conditionMet(it, state, config) }
        if (unlocked.isEmpty()) return state
        unlocked.forEach { heirloom ->
            logs += EventLogEntry(
                kind = LogKind.HEIRLOOM_UNLOCKED,
                atTotalGameDays = state.totalGameDays,
                generation = state.generation,
                detail = heirloom.name,
            )
        }
        return state.copy(heirlooms = state.heirlooms + unlocked)
    }

    fun conditionMet(heirloom: HeirloomId, state: GameState, config: BalanceConfig): Boolean = when (heirloom) {
        HeirloomId.FATHERS_TOOLS -> state.skillLevel(SkillId.CRAFTSMANSHIP) >= config.heirloomCraftSkillRequired
        HeirloomId.FAMILY_BIBLE -> state.ageYears(config) >= config.heirloomBibleAgeYears
        HeirloomId.MOTHERS_RECIPES -> state.wintersSurvivedThisLife >= config.heirloomRecipesWinters
        HeirloomId.PROVEN_DEED -> state.maxEraThisLife.index >= Era.HOMESTEAD.index
        HeirloomId.IRON_STOVE -> state.maxEraThisLife.index >= Era.VILLAGE.index
        HeirloomId.RAILROAD_SHARES -> state.maxEraThisLife.index >= Era.RAILROAD.index
        HeirloomId.HUNTING_RIFLE -> state.lifetimeEarned.food >= config.heirloomRifleLifetimeFood
        HeirloomId.OXEN_YOKE -> state.skillLevel(SkillId.GRIT) >= config.heirloomGritRequired
        HeirloomId.QUILT_OF_MANY_HANDS -> state.skillLevel(SkillId.COMMUNITY) >= config.heirloomQuiltCommunityRequired
        HeirloomId.LEDGER_AND_QUILL -> state.skillLevel(SkillId.THRIFT) >= config.heirloomLedgerThriftRequired
        HeirloomId.PIONEERS_JOURNAL -> state.ageYears(config) >= config.heirloomJournalAgeYears
        HeirloomId.FOUNDERS_GAVEL -> state.maxEraThisLife.index >= Era.CITY.index
    }

    /** Human-readable condition for the heirloom shelf's locked silhouettes. */
    fun conditionText(heirloom: HeirloomId, config: BalanceConfig): String = when (heirloom) {
        HeirloomId.FATHERS_TOOLS -> "Reach Craftsmanship ${config.heirloomCraftSkillRequired} in one life"
        HeirloomId.FAMILY_BIBLE -> "Reach age ${config.heirloomBibleAgeYears.toInt()} in one life"
        HeirloomId.MOTHERS_RECIPES -> "Survive ${config.heirloomRecipesWinters} winters in one life"
        HeirloomId.PROVEN_DEED -> "Reach the ${Era.HOMESTEAD.displayName} era"
        HeirloomId.IRON_STOVE -> "Reach the ${Era.VILLAGE.displayName} era"
        HeirloomId.RAILROAD_SHARES -> "Reach the ${Era.RAILROAD.displayName} era"
        HeirloomId.HUNTING_RIFLE -> "Bring in ${config.heirloomRifleLifetimeFood.toInt()} food in one life"
        HeirloomId.OXEN_YOKE -> "Reach Grit ${config.heirloomGritRequired} in one life"
        HeirloomId.QUILT_OF_MANY_HANDS -> "Reach Community ${config.heirloomQuiltCommunityRequired} in one life"
        HeirloomId.LEDGER_AND_QUILL -> "Reach Thrift ${config.heirloomLedgerThriftRequired} in one life"
        HeirloomId.PIONEERS_JOURNAL -> "Reach age ${config.heirloomJournalAgeYears.toInt()} in one life"
        HeirloomId.FOUNDERS_GAVEL -> "Reach the ${Era.CITY.displayName} era"
    }
}
