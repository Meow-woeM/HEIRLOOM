package com.heirloom.engine.model

import kotlinx.serialization.Serializable

/**
 * Skills train one at a time (a second slot is a Monument unlock), gain XP from game-days
 * passing, and reset on death. Lifetime peaks feed heirloom conditions.
 */
@Serializable
enum class SkillId(val displayName: String, val description: String) {
    GRIT("Grit", "Global work speed"),
    HUSBANDRY("Husbandry", "Food-activity yields"),
    CRAFTSMANSHIP("Craftsmanship", "Materials yields and cheaper building"),
    THRIFT("Thrift", "All costs reduced"),
    COMMUNITY("Community", "Standing gain; opens town life"),
}

/** Shared level/XP progress for both activities and skills. XP is toward the NEXT level. */
@Serializable
data class LevelProgress(val level: Int = 0, val xp: Double = 0.0)
