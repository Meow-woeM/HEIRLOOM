package com.heirloom.engine.model

import kotlinx.serialization.Serializable

/**
 * Family Ventures: permanent purchases that consume Posterity (reducing its passive
 * +%/point bonus — the classic spend-vs-hold tension). All ventures reset on Monument.
 * Costs, rank caps, and effect sizes live in BalanceConfig.
 */
@Serializable
enum class VentureId(val displayName: String, val description: String) {
    SAWMILL_SHARE("Sawmill Share", "+50% materials yields per rank"),
    CATTLE_BRAND("Cattle Brand", "+50% food yields per rank"),
    SEAT_AT_THE_BANK("Seat at the Bank", "+75% money yields per rank"),
    PREACHERS_CIRCUIT("Preacher's Circuit", "+50% standing gain per rank"),
    SCHOOLHOUSE_FUND("Schoolhouse Fund", "+25% skill XP per rank"),
    FAMILY_PLOT("Family Plot", "+5 years lifespan per tier"),
    WAGON_TRAIN("Wagon Train", "+12 hours offline cap per tier"),
    LETTERS_WEST("Letters West", "Each heir starts with seed resources"),
    TOOL_SHED("Tool Shed", "Activities start at +2 levels per tier"),
    FAMILY_DOCTOR("Family Doctor", "Heirs start 2 years younger per tier"),
    HOMESTEAD_ACT_FILING("Homestead Act Filing", "Era advancement costs -20%"),
    OLD_ALMANAC("Old Almanac", "Winter penalties softened by 25%"),
}
