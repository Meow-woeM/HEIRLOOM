package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.EventLogEntry
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.MechanicUnlock
import com.heirloom.engine.model.Resources
import com.heirloom.engine.model.VentureId
import kotlin.math.floor
import kotlin.math.min

/** Feeds the "While you were away" sheet — the emotional core of a check-in game. */
data class OfflineSummary(
    val realSecondsElapsed: Double,
    val realSecondsCounted: Double,
    val cappedByLimit: Boolean,
    val gameDaysSimulated: Double,
    val resourcesGained: Resources,
    val seasonsPassed: Int,
    val yearsPassed: Int,
    val activityLevelsGained: Int,
    val skillLevelsGained: Int,
    val logEntries: List<EventLogEntry>,
    val lifeEnded: Boolean,
    val posterityEarned: Double,
)

data class OfflineResult(val state: GameState, val summary: OfflineSummary)

/**
 * Offline progress is the game's primary production engine. Elapsed real time converts
 * to game-days (same rate as live play) and fast-forwards through the SAME tick function
 * in coarse steps. If the pioneer dies offline, simulation halts there — the player lands
 * on the Legacy screen, and remaining time is forfeited (retiring before a long absence
 * is a real decision).
 */
object OfflineProgressCalculator {
    private const val EPS = 1e-9

    fun offlineCapHours(state: GameState, config: BalanceConfig): Double {
        var hours = config.offlineCapHoursBase
        hours += config.wagonTrainOfflineHoursPerTier * state.ventureRank(VentureId.WAGON_TRAIN)
        if (state.hasMechanic(MechanicUnlock.OFFLINE_CAP_BONUS, config)) {
            hours += config.offlineCapBonusMechanicHours
        }
        return hours
    }

    /**
     * Fast-forwards [elapsedRealSeconds] of away time. [bypassCap] is for rewarded-ad
     * boosts ("a traveler lends a hand"): a fixed grant that ignores the offline cap.
     */
    fun apply(
        state: GameState,
        elapsedRealSeconds: Double,
        config: BalanceConfig,
        bypassCap: Boolean = false,
    ): OfflineResult {
        val before = state
        val capSeconds = offlineCapHours(state, config) * 3600.0
        val elapsed = elapsedRealSeconds.coerceAtLeast(0.0)
        val counted = if (bypassCap) elapsed else min(elapsed, capSeconds)
        var remainingDays = counted * config.gameDaysPerRealSecond

        var s = state
        val logs = mutableListOf<EventLogEntry>()
        while (remainingDays > EPS && !s.isLegacyPending) {
            val step = min(config.offlineStepDays, remainingDays)
            val result = TickEngine.tick(s, step, config)
            s = result.state
            logs += result.newLogEntries
            remainingDays -= step
        }

        val summary = OfflineSummary(
            realSecondsElapsed = elapsed,
            realSecondsCounted = counted,
            cappedByLimit = !bypassCap && elapsed > capSeconds,
            gameDaysSimulated = s.totalGameDays - before.totalGameDays,
            resourcesGained = s.resources - before.resources,
            seasonsPassed = seasonsBetween(before.totalGameDays, s.totalGameDays, config),
            yearsPassed = yearsBetween(before.totalGameDays, s.totalGameDays, config),
            activityLevelsGained = levelsGained(before.activityProgress.mapValues { it.value.level },
                s.activityProgress.mapValues { it.value.level }),
            skillLevelsGained = levelsGained(before.skillProgress.mapValues { it.value.level },
                s.skillProgress.mapValues { it.value.level }),
            logEntries = logs,
            lifeEnded = !before.isLegacyPending && s.isLegacyPending,
            posterityEarned = if (s.isLegacyPending) s.pendingLegacy!!.posterityEarned else 0.0,
        )
        return OfflineResult(s, summary)
    }

    private fun seasonsBetween(fromDay: Double, toDay: Double, config: BalanceConfig): Int =
        (floor(toDay / config.daysPerSeason) - floor(fromDay / config.daysPerSeason)).toInt()

    private fun yearsBetween(fromDay: Double, toDay: Double, config: BalanceConfig): Int =
        (floor(toDay / config.daysPerYear) - floor(fromDay / config.daysPerYear)).toInt()

    private fun <K> levelsGained(before: Map<K, Int>, after: Map<K, Int>): Int =
        after.entries.sumOf { (key, level) -> (level - (before[key] ?: 0)).coerceAtLeast(0) }
}
