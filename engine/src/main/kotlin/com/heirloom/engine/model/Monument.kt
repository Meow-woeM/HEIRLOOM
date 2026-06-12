package com.heirloom.engine.model

import kotlinx.serialization.Serializable

/**
 * Mechanics unlocked by founding Monuments (the deep reset). The schedule
 * (which monument count unlocks what) lives in BalanceConfig.mechanicUnlocks.
 */
@Serializable
enum class MechanicUnlock(val displayName: String, val description: String) {
    AUTOMATION("Standing Orders", "Auto-switch activities by a priority list"),
    STARTING_ERA_BUMP("Old Money", "Each new line starts one era ahead"),
    SECOND_SKILL("Night Study", "Train a second skill at half speed"),
    EVENT_CHOICES("Family Counsel", "Bad events are met head-on and softened"),
    OFFLINE_CAP_BONUS("Long Roads", "+6 hours offline progress cap"),
}

/**
 * The Town Vista stage — the permanent, never-resetting pixel scene at the top of the
 * main screen. Advances on first-time era milestones and Monument count.
 */
@Serializable
enum class VistaStage(val stageNumber: Int, val displayName: String) {
    WAGON_CAMP(1, "Wagon Camp"),
    HOMESTEAD(2, "Homestead"),
    VILLAGE(3, "Village"),
    FRONTIER_TOWN(4, "Frontier Town"),
    FOUNDED_CITY(5, "Founded City"),
    OLD_CITY(6, "Old City"),
    MODERN_CITY(7, "Modern City"),
    FUTURE_CITY(8, "Future City"),
}
