package com.heirloom.engine.model

import kotlinx.serialization.Serializable

/**
 * Tags drive season modifiers: PLANTING gets the spring bonus, HARVEST the fall bonus,
 * OUTDOOR suffers the winter penalty. INDOOR work is winter-proof.
 */
@Serializable
enum class ActivityTag { PLANTING, HARVEST, OUTDOOR, INDOOR }

/**
 * Every activity in the game. Activities unlock with their era and stay available in all
 * later eras (the money you earned hauling cargo on the trail still matters on the claim).
 * Base yields per game-day live in [com.heirloom.engine.balance.BalanceConfig.activityBaseYields].
 */
@Serializable
enum class ActivityId(
    val displayName: String,
    val era: Era,
    val resource: ResourceType,
    val tags: Set<ActivityTag>,
) {
    // Era 1 - The Trail
    FORAGE("Forage", Era.TRAIL, ResourceType.FOOD, setOf(ActivityTag.OUTDOOR, ActivityTag.HARVEST)),
    HUNT("Hunt", Era.TRAIL, ResourceType.FOOD, setOf(ActivityTag.OUTDOOR)),
    HAUL_CARGO("Haul Cargo", Era.TRAIL, ResourceType.MONEY, setOf(ActivityTag.OUTDOOR)),
    SCOUT_AHEAD("Scout Ahead", Era.TRAIL, ResourceType.MATERIALS, setOf(ActivityTag.OUTDOOR)),

    // Era 2 - The Claim
    CLEAR_LAND("Clear Land", Era.CLAIM, ResourceType.MATERIALS, setOf(ActivityTag.OUTDOOR)),
    BUILD_CABIN("Build Cabin", Era.CLAIM, ResourceType.MATERIALS, setOf(ActivityTag.OUTDOOR)),
    TRAP("Trap", Era.CLAIM, ResourceType.MATERIALS, setOf(ActivityTag.OUTDOOR)),
    TEND_GARDEN("Tend Garden", Era.CLAIM, ResourceType.FOOD, setOf(ActivityTag.OUTDOOR, ActivityTag.PLANTING)),

    // Era 3 - Homestead
    PLOW_FIELDS("Plow Fields", Era.HOMESTEAD, ResourceType.FOOD, setOf(ActivityTag.OUTDOOR, ActivityTag.PLANTING)),
    HARVEST("Harvest", Era.HOMESTEAD, ResourceType.FOOD, setOf(ActivityTag.OUTDOOR, ActivityTag.HARVEST)),
    RAISE_LIVESTOCK("Raise Livestock", Era.HOMESTEAD, ResourceType.FOOD, setOf(ActivityTag.OUTDOOR)),
    BLACKSMITH("Blacksmith", Era.HOMESTEAD, ResourceType.MATERIALS, setOf(ActivityTag.INDOOR)),

    // Era 4 - Village
    RUN_MILL("Run the Mill", Era.VILLAGE, ResourceType.MATERIALS, setOf(ActivityTag.INDOOR)),
    GENERAL_STORE("Keep the General Store", Era.VILLAGE, ResourceType.MONEY, setOf(ActivityTag.INDOOR)),
    CARPENTRY("Carpentry", Era.VILLAGE, ResourceType.MATERIALS, setOf(ActivityTag.INDOOR)),
    TEACH_SCHOOL("Teach School", Era.VILLAGE, ResourceType.STANDING, setOf(ActivityTag.INDOOR)),

    // Era 5 - Railroad Town
    FREIGHT_CONTRACTS("Freight Contracts", Era.RAILROAD, ResourceType.MONEY, setOf(ActivityTag.OUTDOOR)),
    BANKING("Banking", Era.RAILROAD, ResourceType.MONEY, setOf(ActivityTag.INDOOR)),
    PRINT_NEWSPAPER("Print Newspaper", Era.RAILROAD, ResourceType.STANDING, setOf(ActivityTag.INDOOR)),
    LAND_OFFICE("Land Office", Era.RAILROAD, ResourceType.MONEY, setOf(ActivityTag.INDOOR)),

    // Era 6 - City & Statehood (endgame sink: feeds Standing toward the Monument)
    FOUND_INSTITUTIONS("Found Institutions", Era.CITY, ResourceType.STANDING, setOf(ActivityTag.INDOOR)),
    ;

    companion object {
        fun forEra(era: Era): List<ActivityId> = entries.filter { it.era == era }
        fun unlockedAt(era: Era): List<ActivityId> = entries.filter { it.era.index <= era.index }
    }
}
