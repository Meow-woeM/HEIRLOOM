package com.heirloom.engine.model

import kotlinx.serialization.Serializable

/**
 * Content eras. Advancing is a within-generation purchase; heirs restart at Era 1
 * unless heirlooms/monument mechanics say otherwise. [index] is 1-based.
 */
@Serializable
enum class Era(val index: Int, val displayName: String) {
    TRAIL(1, "The Trail"),
    CLAIM(2, "The Claim"),
    HOMESTEAD(3, "Homestead"),
    VILLAGE(4, "Village"),
    RAILROAD(5, "Railroad Town"),
    CITY(6, "City & Statehood");

    val next: Era? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun byIndex(index: Int): Era = entries.first { it.index == index }
    }
}
