package com.heirloom.app.ui

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.EventLogEntry
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.Season

fun EventLogEntry.title(): String = when (kind) {
    LogKind.RANDOM_EVENT -> eventType?.displayName ?: "Something happened"
    LogKind.ERA_ADVANCED -> "Onward to " + (eraName(detail) ?: "a new era")
    LogKind.HEIRLOOM_UNLOCKED -> "Heirloom: " + (heirloomName(detail) ?: "a treasure")
    LogKind.SPRING_BOUNTY -> "Spring Bounty"
    LogKind.DEATH -> "A pioneer at rest" + (detail?.let { " — $it" } ?: "")
    LogKind.RETIREMENT -> "Passed the homestead on" + (detail?.let { " — $it" } ?: "")
    LogKind.MONUMENT_FOUNDED -> "Monument ${detail ?: ""} founded!"
}

fun EventLogEntry.subtitle(): String? = when (kind) {
    LogKind.RANDOM_EVENT ->
        (eventType?.blurb ?: "") + if (mitigated) " (The family counsel softened the blow.)" else ""
    LogKind.SPRING_BOUNTY -> "The winter was kind. All work +25% this spring."
    LogKind.MONUMENT_FOUNDED -> "The line is complete. The town will remember."
    else -> null
}

fun EventLogEntry.stamp(config: BalanceConfig): String {
    val year = Season.yearAtDay(atTotalGameDays, config)
    val season = Season.atDay(atTotalGameDays, config)
    return "Year $year, ${season.displayName} · Gen $generation"
}

private fun eraName(detail: String?): String? =
    detail?.let { d -> Era.entries.firstOrNull { it.name == d }?.displayName }

private fun heirloomName(detail: String?): String? =
    detail?.let { d -> HeirloomId.entries.firstOrNull { it.name == d }?.displayName }
