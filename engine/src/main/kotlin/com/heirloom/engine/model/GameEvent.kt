package com.heirloom.engine.model

import kotlinx.serialization.Serializable

/**
 * Random events. Roughly one per season, more often in winter. v1 events have no player
 * choices (toast/dialog + log only); the Monument 4 mechanic auto-mitigates negative events.
 */
@Serializable
enum class EventType(
    val displayName: String,
    val blurb: String,
    val isNegative: Boolean,
) {
    DROUGHT("Drought", "The creek runs thin. Food work suffers this season.", true),
    LOCUSTS("Locusts", "A dark cloud with teeth. Part of the stores are gone.", true),
    HARD_WINTER("Hard Winter", "The snow comes early and stays late. The larder empties fast.", true),
    TRAVELING_MERCHANT("Traveling Merchant", "A wagon of wonders pays well for your surplus.", false),
    BARN_RAISING("Barn Raising", "The neighbors arrive with hammers and pie.", false),
    GOOD_RAINS("Good Rains", "Soft, steady, and right on time. The land answers.", false),
    COUNTY_FAIR("County Fair", "Ribbons, races, and a little honest commerce.", false),
    NEWCOMERS("Newcomers", "Fresh wagons on the horizon. The town grows.", false),
    RAILROAD_ARRIVES("The Railroad Arrives", "Steel rails reach town. Everything moves faster now.", false),
}

/** An event with a lasting effect, counting down in game-days. Instant events never become active. */
@Serializable
data class ActiveEvent(
    val type: EventType,
    val remainingDays: Double,
    val mitigated: Boolean = false,
)

/** What a log line is about. RANDOM_EVENT carries an [EventType]; milestones use [detail]. */
@Serializable
enum class LogKind {
    RANDOM_EVENT,
    ERA_ADVANCED,
    HEIRLOOM_UNLOCKED,
    SPRING_BOUNTY,
    DEATH,
    RETIREMENT,
    MONUMENT_FOUNDED,
}

@Serializable
data class EventLogEntry(
    val kind: LogKind,
    val atTotalGameDays: Double,
    val generation: Int,
    val eventType: EventType? = null,
    val detail: String? = null,
    val mitigated: Boolean = false,
)
