package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.ActivityTag
import com.heirloom.engine.model.EventType
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.ResourceType
import com.heirloom.engine.model.Season
import com.heirloom.engine.model.SkillId
import com.heirloom.engine.model.VentureId
import kotlin.math.pow

/**
 * The yield formula, in one place so the tick engine, UI estimates, and tests all agree:
 *
 * yield/day = base
 *   * (1 + activityLevelBonus * level)            — the activity levels itself
 *   * skillFactor                                  — Grit globally + the resource's skill
 *   * heirloomFactor                               — multiplicative stack
 *   * ventureFactor                                — Family Venture ranks for the resource
 *   * eraFactor                                    — infrastructure compounds
 *   * seasonFactor                                 — spring/fall bonuses, winter penalty, weather events
 *   * springBounty, posterity, monuments, supporter, starvation, railroad
 */
object YieldCalculator {

    fun dailyYield(state: GameState, activity: ActivityId, config: BalanceConfig): Double {
        val base = config.activityBaseYields.getValue(activity)
        val levelFactor = 1.0 + config.activityLevelYieldBonus * state.activityLevel(activity)
        return base * levelFactor *
            skillYieldFactor(state, activity, config) *
            heirloomYieldFactor(state, activity.resource, config) *
            ventureYieldFactor(state, activity.resource, config) *
            seasonFactor(state, activity, config) *
            resourceEventFactor(state, activity.resource, config) *
            globalFactor(state, config)
    }

    fun skillYieldFactor(state: GameState, activity: ActivityId, config: BalanceConfig): Double {
        val grit = 1.0 + config.gritSpeedPerLevel * state.skillLevel(SkillId.GRIT)
        val resourceSkill = when (activity.resource) {
            ResourceType.FOOD -> 1.0 + config.husbandryFoodYieldPerLevel * state.skillLevel(SkillId.HUSBANDRY)
            ResourceType.MATERIALS -> 1.0 + config.craftsmanshipMaterialsYieldPerLevel * state.skillLevel(SkillId.CRAFTSMANSHIP)
            ResourceType.STANDING -> 1.0 + config.communityStandingYieldPerLevel * state.skillLevel(SkillId.COMMUNITY)
            ResourceType.MONEY -> 1.0
        }
        return grit * resourceSkill
    }

    fun heirloomYieldFactor(state: GameState, resource: ResourceType, config: BalanceConfig): Double {
        val e = config.heirloomEffects
        var factor = 1.0
        if (state.hasHeirloom(HeirloomId.OXEN_YOKE)) factor *= 1.0 + e.oxenYokeAllYieldBonus
        if (state.hasHeirloom(HeirloomId.PIONEERS_JOURNAL)) factor *= 1.0 + e.journalAllYieldBonus
        when (resource) {
            ResourceType.FOOD -> {
                if (state.hasHeirloom(HeirloomId.IRON_STOVE)) factor *= 1.0 + e.ironStoveFoodBonus
                if (state.hasHeirloom(HeirloomId.HUNTING_RIFLE)) factor *= 1.0 + e.huntingRifleFoodBonus
            }
            ResourceType.MATERIALS ->
                if (state.hasHeirloom(HeirloomId.FATHERS_TOOLS)) factor *= 1.0 + e.fathersToolsMaterialsBonus
            ResourceType.MONEY ->
                if (state.hasHeirloom(HeirloomId.RAILROAD_SHARES)) factor *= 1.0 + e.railroadSharesMoneyBonus
            ResourceType.STANDING ->
                if (state.hasHeirloom(HeirloomId.QUILT_OF_MANY_HANDS)) factor *= 1.0 + e.quiltStandingBonus
        }
        return factor
    }

    fun ventureYieldFactor(state: GameState, resource: ResourceType, config: BalanceConfig): Double {
        val venture = when (resource) {
            ResourceType.FOOD -> VentureId.CATTLE_BRAND
            ResourceType.MATERIALS -> VentureId.SAWMILL_SHARE
            ResourceType.MONEY -> VentureId.SEAT_AT_THE_BANK
            ResourceType.STANDING -> VentureId.PREACHERS_CIRCUIT
        }
        val rank = state.ventureRank(venture)
        if (rank == 0) return 1.0
        return 1.0 + config.ventureSpecs.getValue(venture).effectPerRank * rank
    }

    fun seasonFactor(state: GameState, activity: ActivityId, config: BalanceConfig): Double =
        when (state.season(config)) {
            Season.SPRING -> if (ActivityTag.PLANTING in activity.tags) 1.0 + config.springPlantingBonus else 1.0
            Season.SUMMER -> 1.0
            Season.FALL -> if (ActivityTag.HARVEST in activity.tags) 1.0 + config.fallHarvestBonus else 1.0
            Season.WINTER -> if (ActivityTag.OUTDOOR in activity.tags) {
                val relief = if (state.ventureRank(VentureId.OLD_ALMANAC) > 0) config.almanacWinterRelief else 0.0
                1.0 - config.winterOutdoorPenalty * (1.0 - relief)
            } else 1.0
        }

    /** Weather events touch food production; the railroad doubles money for the rest of the life. */
    fun resourceEventFactor(state: GameState, resource: ResourceType, config: BalanceConfig): Double {
        var factor = 1.0
        if (resource == ResourceType.FOOD) {
            for (event in state.activeEvents) {
                when (event.type) {
                    EventType.DROUGHT -> {
                        val loss = 1.0 - config.droughtFoodYieldFactor
                        factor *= 1.0 - loss * (if (event.mitigated) config.eventMitigationFactor else 1.0)
                    }
                    EventType.GOOD_RAINS -> factor *= 1.0 + config.goodRainsFoodYieldBonus
                    else -> {}
                }
            }
        }
        if (resource == ResourceType.MONEY && state.railroadArrivedThisLife) {
            factor *= config.railroadMoneyMultiplier
        }
        return factor
    }

    /** Everything that applies to all yields regardless of resource. */
    fun globalFactor(state: GameState, config: BalanceConfig): Double {
        var factor = 1.0 + config.eraYieldBonusPerEra * (state.era.index - 1)
        factor *= 1.0 + config.posterityYieldBonusPerPoint * state.posterity
        factor *= 1.0 + config.monumentYieldBonusEach * state.monuments
        if (state.springBountyActive && state.season(config) == Season.SPRING) factor *= 1.0 + config.springBountyBonus
        if (state.supporter) factor *= config.supporterYieldMultiplier
        if (state.starving) factor *= config.starvationYieldFactor
        return factor
    }

    /**
     * Daily food upkeep: scales with era (the household grows), multiplied in winter,
     * softened by Mother's Recipes / Old Almanac, and reduced like any cost by Thrift & Ledger.
     */
    fun dailyFoodUpkeep(state: GameState, config: BalanceConfig): Double {
        var upkeep = config.baseFoodUpkeepPerDay * config.upkeepEraFactor.pow(state.era.index - 1)
        if (state.season(config) == Season.WINTER) {
            val hardWinter = state.activeEvents.firstOrNull { it.type == EventType.HARD_WINTER }
            var winterMult = if (hardWinter != null) {
                if (hardWinter.mitigated) {
                    val extra = config.hardWinterFoodDrainMultiplier - config.winterFoodDrainMultiplier
                    config.winterFoodDrainMultiplier + extra * config.eventMitigationFactor
                } else config.hardWinterFoodDrainMultiplier
            } else config.winterFoodDrainMultiplier
            if (state.ventureRank(VentureId.OLD_ALMANAC) > 0) {
                winterMult = 1.0 + (winterMult - 1.0) * (1.0 - config.almanacWinterRelief)
            }
            upkeep *= winterMult
            if (state.hasHeirloom(HeirloomId.MOTHERS_RECIPES)) {
                upkeep *= config.heirloomEffects.mothersRecipesWinterDrainFactor
            }
        }
        return upkeep * CostCalculator.costFactor(state, config)
    }

    /** XP per game-day for a training skill (second slot trains slower). */
    fun skillXpPerDay(state: GameState, skill: SkillId, isSecondSlot: Boolean, config: BalanceConfig): Double {
        var xp = config.skillXpPerDay
        val active = state.activeActivity
        if (active != null && skillMatchesActivity(skill, active)) xp *= config.skillAffinityXpBonus
        if (state.hasHeirloom(HeirloomId.FAMILY_BIBLE)) xp *= 1.0 + config.heirloomEffects.familyBibleSkillXpBonus
        val schoolhouse = state.ventureRank(VentureId.SCHOOLHOUSE_FUND)
        if (schoolhouse > 0) xp *= 1.0 + config.ventureSpecs.getValue(VentureId.SCHOOLHOUSE_FUND).effectPerRank * schoolhouse
        if (isSecondSlot) xp *= config.secondSkillXpFactor
        return xp
    }

    /** Which work teaches which skill (the affinity XP boost). */
    fun skillMatchesActivity(skill: SkillId, activity: ActivityId): Boolean = when (skill) {
        SkillId.GRIT -> ActivityTag.OUTDOOR in activity.tags
        SkillId.HUSBANDRY -> activity.resource == ResourceType.FOOD
        SkillId.CRAFTSMANSHIP -> activity.resource == ResourceType.MATERIALS
        SkillId.THRIFT -> activity.resource == ResourceType.MONEY
        SkillId.COMMUNITY -> activity.resource == ResourceType.STANDING
    }
}
