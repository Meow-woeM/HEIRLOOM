package com.heirloom.engine.balance

import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.EventType
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.MechanicUnlock
import com.heirloom.engine.model.Resources
import com.heirloom.engine.model.VentureId

/**
 * EVERY tunable constant in the game lives here — nothing is hard-coded elsewhere.
 * A data class so the HeadlessRunner (and tests) can copy() variants while tuning.
 *
 * Time scale note (see DECISIONS.md): the default is ~1 game-day per 45 real seconds,
 * which makes one almanac year ≈ 21 real minutes and one natural lifespan ≈ 15 real
 * hours — a generation per real-world day for a player who checks in 2-3 times. The
 * kickoff suggested 0.5 game-days/second, but that makes a whole life last ~40 active
 * minutes and leaves a 10h offline cap ~93% unusable (offline sim halts at death),
 * which contradicts the check-in pacing acceptance criteria. Tuned via HeadlessRunner.
 */
data class BalanceConfig(
    // ------------------------------------------------------------------ time
    val gameDaysPerRealSecond: Double = 1.0 / 45.0,
    val daysPerSeason: Int = 7,
    val seasonsPerYear: Int = 4,

    // ----------------------------------------------------------- life & death
    val startAgeYears: Double = 18.0,
    val baseDeathAgeYears: Double = 60.0,
    /** Death age varies ± this many years, rolled once at birth. */
    val deathAgeRandomRangeYears: Double = 4.0,
    /** +years per era index beyond 1 reached this life (prosperity lengthens life). */
    val deathAgeBonusPerEra: Double = 1.0,
    /** +years if food never hit zero this life. */
    val deathAgeFoodSecurityBonus: Double = 3.0,
    val retirementMinAgeYears: Double = 40.0,

    // ------------------------------------------------------------- resources
    /** The wagon arrives provisioned: enough food that day 1 isn't pure survival grinding. */
    val newGameResources: Resources = Resources(food = 150.0),
    /** Daily food upkeep at Era 1; scales by [upkeepEraFactor] per era (the family grows). */
    val baseFoodUpkeepPerDay: Double = 1.0,
    val upkeepEraFactor: Double = 2.5,
    /** Global yield multiplier while starving (food at zero). Starvation never kills in v1. */
    val starvationYieldFactor: Double = 0.5,

    // ------------------------------------------------------------- activities
    /** Base yield per game-day. Magnitudes are tiered roughly x6 per home era. */
    val activityBaseYields: Map<ActivityId, Double> = mapOf(
        // Era 1 — The Trail
        ActivityId.FORAGE to 1.6,
        ActivityId.HUNT to 2.2,
        ActivityId.HAUL_CARGO to 1.2,
        ActivityId.SCOUT_AHEAD to 1.4,
        // Era 2 — The Claim
        ActivityId.CLEAR_LAND to 8.0,
        ActivityId.BUILD_CABIN to 6.5,
        ActivityId.TRAP to 9.5,
        ActivityId.TEND_GARDEN to 9.0,
        // Era 3 — Homestead
        ActivityId.PLOW_FIELDS to 48.0,
        ActivityId.HARVEST to 55.0,
        ActivityId.RAISE_LIVESTOCK to 42.0,
        ActivityId.BLACKSMITH to 50.0,
        // Era 4 — Village
        ActivityId.RUN_MILL to 300.0,
        ActivityId.GENERAL_STORE to 260.0,
        ActivityId.CARPENTRY to 330.0,
        ActivityId.TEACH_SCHOOL to 30.0,
        // Era 5 — Railroad Town
        ActivityId.FREIGHT_CONTRACTS to 1900.0,
        ActivityId.BANKING to 2300.0,
        ActivityId.PRINT_NEWSPAPER to 180.0,
        ActivityId.LAND_OFFICE to 1600.0,
        // Era 6 — City & Statehood
        ActivityId.FOUND_INSTITUTIONS to 1200.0,
    ),
    /** Each activity level makes itself faster: yield *= (1 + this * level). */
    val activityLevelYieldBonus: Double = 0.08,
    /** XP toward next activity level: cost(level->level+1) = base * growth^level. 1 XP per day worked. */
    val activityXpBase: Double = 8.0,
    val activityXpGrowth: Double = 1.10,
    val activityXpPerDay: Double = 1.0,

    // ----------------------------------------------------------------- skills
    /** XP toward next skill level: cost(level->level+1) = base * growth^level. */
    val skillXpBase: Double = 5.0,
    val skillXpGrowth: Double = 1.12,
    val skillXpPerDay: Double = 1.0,
    /** XP multiplier when the active activity matches the training skill's domain. */
    val skillAffinityXpBonus: Double = 1.5,
    /** The SECOND_SKILL mechanic trains the second slot at this fraction of full speed. */
    val secondSkillXpFactor: Double = 0.5,
    val gritSpeedPerLevel: Double = 0.015,
    val husbandryFoodYieldPerLevel: Double = 0.03,
    val craftsmanshipMaterialsYieldPerLevel: Double = 0.02,
    /** Craftsmanship: materials portion of build/era costs *= (1 - this)^level. */
    val craftsmanshipCostReductionPerLevel: Double = 0.01,
    /** Thrift cost divisor: costs /= (1 + this * level) — smooth soft cap, never reaches zero. */
    val thriftCostReductionPerLevel: Double = 0.008,
    val communityStandingYieldPerLevel: Double = 0.03,
    /** Community level required to work standing-producing activities. */
    val communityGates: Map<ActivityId, Int> = mapOf(
        ActivityId.TEACH_SCHOOL to 5,
        ActivityId.PRINT_NEWSPAPER to 10,
        ActivityId.FOUND_INSTITUTIONS to 15,
    ),

    // ------------------------------------------------------------------- eras
    /** Cost to ENTER an era. Growth ~x50 per step (spec: x40-80). Standing gates Village+. */
    val eraCosts: Map<Era, Resources> = mapOf(
        Era.CLAIM to Resources(materials = 60.0, money = 40.0),
        Era.HOMESTEAD to Resources(materials = 3_000.0, money = 2_000.0),
        Era.VILLAGE to Resources(materials = 150_000.0, money = 100_000.0, standing = 500.0),
        Era.RAILROAD to Resources(materials = 7_500_000.0, money = 5_000_000.0, standing = 5_000.0),
        Era.CITY to Resources(materials = 375_000_000.0, money = 250_000_000.0, standing = 50_000.0),
    ),
    /** Global yield bonus per era index beyond 1 (infrastructure compounds). */
    val eraYieldBonusPerEra: Double = 0.25,

    // ---------------------------------------------------------------- seasons
    val springPlantingBonus: Double = 0.20,
    val fallHarvestBonus: Double = 0.50,
    /** Outdoor yields lose this fraction in winter. */
    val winterOutdoorPenalty: Double = 0.60,
    val winterFoodDrainMultiplier: Double = 3.0,
    val hardWinterFoodDrainMultiplier: Double = 4.0,
    /** Surviving winter without food hitting zero: +25% all yields the following spring. */
    val springBountyBonus: Double = 0.25,

    // ----------------------------------------------------------------- events
    /** Chance per day-boundary of rolling an event (≈1 per 7-day season). */
    val eventChancePerDay: Double = 1.0 / 7.0,
    val winterEventChanceMultiplier: Double = 1.5,
    /** Relative weights; HARD_WINTER only rolls in winter, DROUGHT/GOOD_RAINS never in winter. */
    val eventWeights: Map<EventType, Double> = mapOf(
        EventType.DROUGHT to 10.0,
        EventType.LOCUSTS to 8.0,
        EventType.HARD_WINTER to 8.0,
        EventType.TRAVELING_MERCHANT to 12.0,
        EventType.BARN_RAISING to 12.0,
        EventType.GOOD_RAINS to 10.0,
        EventType.COUNTY_FAIR to 8.0,
        EventType.NEWCOMERS to 8.0,
    ),
    val eventDurationDays: Double = 7.0,
    val droughtFoodYieldFactor: Double = 0.5,
    val goodRainsFoodYieldBonus: Double = 0.30,
    val locustsFoodLossFraction: Double = 0.30,
    /** Merchant pays this many days of the active activity's yield, converted to money. */
    val merchantWindfallDays: Double = 15.0,
    val barnRaisingStandingBase: Double = 5.0,
    val countyFairStandingBase: Double = 4.0,
    val countyFairMoneyDays: Double = 6.0,
    val newcomersStandingBase: Double = 6.0,
    /** Standing windfalls scale by era^2 so they stay relevant. */
    val standingEventEraPower: Double = 2.0,
    /** Railroad Arrives (auto, once per life, on entering Era 5): money yields x this for the rest of the life. */
    val railroadMoneyMultiplier: Double = 2.0,
    /** EVENT_CHOICES mechanic: negative event magnitudes are halved. */
    val eventMitigationFactor: Double = 0.5,
    val eventLogMaxEntries: Int = 120,

    // -------------------------------------------------------------- heirlooms
    val heirloomCraftSkillRequired: Int = 25,
    val heirloomBibleAgeYears: Double = 50.0,
    val heirloomRecipesWinters: Int = 5,
    val heirloomRifleLifetimeFood: Double = 10_000.0,
    val heirloomGritRequired: Int = 25,
    val heirloomQuiltCommunityRequired: Int = 25,
    val heirloomLedgerThriftRequired: Int = 25,
    val heirloomJournalAgeYears: Double = 70.0,
    val heirloomEffects: HeirloomEffects = HeirloomEffects(),

    // -------------------------------------------------------------- posterity
    /** posterityEarned = floor(K * sqrt(valueScore)) - alreadyEarnedThisMonument. */
    val posterityK: Double = 1.0,
    /** Passive bonus: all yields *= (1 + this * heldPosterity). */
    val posterityYieldBonusPerPoint: Double = 0.02,
    /** Weighted contribution of earned resources to the value score. */
    val valueScoreWeights: Map<com.heirloom.engine.model.ResourceType, Double> = mapOf(
        com.heirloom.engine.model.ResourceType.FOOD to 0.3,
        com.heirloom.engine.model.ResourceType.MATERIALS to 1.0,
        com.heirloom.engine.model.ResourceType.MONEY to 1.0,
        com.heirloom.engine.model.ResourceType.STANDING to 5.0,
    ),
    /** One-time score bonus at death for the era reached: base * era^power. */
    val eraScoreBonusBase: Double = 400.0,
    val eraScoreBonusPower: Double = 2.5,

    // --------------------------------------------------------------- ventures
    val ventureSpecs: Map<VentureId, VentureSpec> = mapOf(
        VentureId.SAWMILL_SHARE to VentureSpec.repeatable(baseCost = 15.0, costGrowth = 1.7, effectPerRank = 0.50),
        VentureId.CATTLE_BRAND to VentureSpec.repeatable(baseCost = 15.0, costGrowth = 1.7, effectPerRank = 0.50),
        VentureId.SEAT_AT_THE_BANK to VentureSpec.repeatable(baseCost = 40.0, costGrowth = 1.8, effectPerRank = 0.75),
        VentureId.PREACHERS_CIRCUIT to VentureSpec.repeatable(baseCost = 20.0, costGrowth = 1.7, effectPerRank = 0.50),
        VentureId.SCHOOLHOUSE_FUND to VentureSpec.repeatable(baseCost = 25.0, costGrowth = 1.75, effectPerRank = 0.25),
        VentureId.FAMILY_PLOT to VentureSpec.tiered(50.0, 250.0, 1_200.0, 6_000.0, 30_000.0),
        VentureId.WAGON_TRAIN to VentureSpec.tiered(80.0, 800.0, 8_000.0),
        VentureId.LETTERS_WEST to VentureSpec.tiered(30.0, 300.0, 3_000.0),
        VentureId.TOOL_SHED to VentureSpec.tiered(40.0, 400.0, 4_000.0),
        VentureId.FAMILY_DOCTOR to VentureSpec.tiered(60.0, 600.0, 6_000.0),
        VentureId.HOMESTEAD_ACT_FILING to VentureSpec.tiered(200.0),
        VentureId.OLD_ALMANAC to VentureSpec.tiered(100.0),
    ),
    val familyPlotYearsPerTier: Double = 5.0,
    val wagonTrainOfflineHoursPerTier: Double = 12.0,
    /** Letters West seed per tier (multiplied out in GenerationManager). */
    val lettersWestSeedBase: Resources = Resources(food = 50.0, materials = 40.0, money = 30.0),
    val lettersWestSeedGrowthPerTier: Double = 20.0,
    val toolShedLevelsPerTier: Int = 2,
    val familyDoctorYearsYoungerPerTier: Double = 2.0,
    val homesteadActEraCostFactor: Double = 0.80,
    /** Old Almanac: winter penalties (drain above 1x, and outdoor penalty) reduced by this fraction. */
    val almanacWinterRelief: Double = 0.25,

    // -------------------------------------------------------------- monuments
    /** Cost to found a Monument (already requires Era 6). Scales by [monumentCostGrowth]^n. */
    val monumentBaseCost: Resources = Resources(materials = 1.4e9, money = 1.0e9, standing = 140_000.0),
    val monumentCostGrowth: Double = 9.0,
    /** All yields *= (1 + this * monuments). */
    val monumentYieldBonusEach: Double = 1.0,
    /**
     * Founders' renown: posterity grants *= (1 + this * monuments). Keeps post-Monument
     * cycles accelerating (the prestige wave) even though posterity itself resets.
     */
    val monumentPosterityBonusEach: Double = 0.30,
    val mechanicUnlocks: Map<Int, MechanicUnlock> = mapOf(
        1 to MechanicUnlock.AUTOMATION,
        2 to MechanicUnlock.STARTING_ERA_BUMP,
        3 to MechanicUnlock.SECOND_SKILL,
        4 to MechanicUnlock.EVENT_CHOICES,
        5 to MechanicUnlock.OFFLINE_CAP_BONUS,
    ),
    /** Founding this many Monuments is "completion" (Future City vista). */
    val finalMonument: Int = 6,
    val offlineCapBonusMechanicHours: Double = 6.0,

    // ------------------------------------------------------------- automation
    /** Auto-switch to the best food activity when stores fall below this many days of upkeep. */
    val automationFoodReserveDays: Double = 30.0,

    // ---------------------------------------------------------------- offline
    val offlineCapHoursBase: Double = 10.0,
    /** Granularity of offline fast-forward, in game-days. */
    val offlineStepDays: Double = 1.0,
    /** Don't bother with the "While you were away" sheet for absences shorter than this. */
    val offlineSummaryMinAwaySeconds: Double = 60.0,

    // ----------------------------------------------------------- monetization
    val rewardedAdHours: Double = 4.0,
    val maxRewardedAdsPerDay: Int = 3,
    val supporterYieldMultiplier: Double = 2.0,
) {
    val daysPerYear: Int get() = daysPerSeason * seasonsPerYear

    companion object {
        /** The shipped balance. Tests and the simulator use this — never mocks. */
        val DEFAULT = BalanceConfig()
    }
}

/** Heirloom effect magnitudes, grouped to keep the main config scannable. */
data class HeirloomEffects(
    val fathersToolsMaterialsBonus: Double = 0.25,
    val familyBibleSkillXpBonus: Double = 0.15,
    val mothersRecipesWinterDrainFactor: Double = 0.80,
    /** Proven Deed: heirs start at this era. */
    val provenDeedStartEra: Era = Era.CLAIM,
    val ironStoveFoodBonus: Double = 0.30,
    val railroadSharesMoneyBonus: Double = 0.25,
    val huntingRifleFoodBonus: Double = 0.20,
    val oxenYokeAllYieldBonus: Double = 0.10,
    val quiltStandingBonus: Double = 0.25,
    val ledgerCostFactor: Double = 0.90,
    val journalAllYieldBonus: Double = 0.10,
    val foundersGavelPosterityBonus: Double = 0.50,
)

/**
 * A Family Venture's cost curve. Repeatable ventures use baseCost * costGrowth^rank with
 * no cap; tiered ventures have explicit per-tier costs and an implicit max rank.
 */
data class VentureSpec(
    val tierCosts: List<Double>?,
    val baseCost: Double,
    val costGrowth: Double,
    val effectPerRank: Double,
) {
    val maxRanks: Int? get() = tierCosts?.size

    fun costForRank(currentRank: Int): Double? = when {
        tierCosts != null -> tierCosts.getOrNull(currentRank)
        else -> baseCost * Math.pow(costGrowth, currentRank.toDouble())
    }

    companion object {
        fun repeatable(baseCost: Double, costGrowth: Double, effectPerRank: Double) =
            VentureSpec(tierCosts = null, baseCost = baseCost, costGrowth = costGrowth, effectPerRank = effectPerRank)

        fun tiered(vararg costs: Double) =
            VentureSpec(tierCosts = costs.toList(), baseCost = 0.0, costGrowth = 1.0, effectPerRank = 0.0)
    }
}
