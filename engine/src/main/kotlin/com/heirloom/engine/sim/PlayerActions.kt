package com.heirloom.engine.sim

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.EventLogEntry
import com.heirloom.engine.model.EventType
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.model.MechanicUnlock
import com.heirloom.engine.model.SkillId
import com.heirloom.engine.model.VentureId

/**
 * Everything the player can do, as pure (state) -> state functions. Each action has a
 * `can...` companion the UI uses for enablement; the action itself `require()`s it.
 */
object PlayerActions {

    fun isActivityUnlocked(state: GameState, id: ActivityId, config: BalanceConfig): Boolean =
        id.era.index <= state.era.index &&
            state.skillLevel(SkillId.COMMUNITY) >= (config.communityGates[id] ?: 0)

    fun selectActivity(state: GameState, id: ActivityId, config: BalanceConfig): GameState {
        require(!state.isLegacyPending) { "Cannot work while the Legacy screen is pending" }
        require(isActivityUnlocked(state, id, config)) { "$id is not unlocked yet" }
        return state.copy(activeActivity = id)
    }

    fun selectSkill(state: GameState, id: SkillId): GameState {
        require(!state.isLegacyPending) { "Cannot train while the Legacy screen is pending" }
        val second = if (state.secondSkill == id) state.activeSkill else state.secondSkill
        return state.copy(activeSkill = id, secondSkill = second?.takeIf { it != id })
    }

    fun canUseSecondSkill(state: GameState, config: BalanceConfig): Boolean =
        state.hasMechanic(MechanicUnlock.SECOND_SKILL, config)

    fun selectSecondSkill(state: GameState, id: SkillId?, config: BalanceConfig): GameState {
        require(!state.isLegacyPending) { "Cannot train while the Legacy screen is pending" }
        if (id == null) return state.copy(secondSkill = null)
        require(canUseSecondSkill(state, config)) { "Second skill slot is not unlocked" }
        require(id != state.activeSkill) { "Already training $id in the first slot" }
        return state.copy(secondSkill = id)
    }

    // ---------------------------------------------------------------- eras

    fun canAdvanceEra(state: GameState, config: BalanceConfig): Boolean {
        if (state.isLegacyPending) return false
        val next = state.era.next ?: return false
        return state.resources.covers(CostCalculator.eraCost(state, next, config))
    }

    fun advanceEra(state: GameState, config: BalanceConfig): GameState {
        require(canAdvanceEra(state, config)) { "Cannot advance era" }
        val next = state.era.next!!
        val cost = CostCalculator.eraCost(state, next, config)
        val logs = mutableListOf(
            EventLogEntry(
                kind = LogKind.ERA_ADVANCED,
                atTotalGameDays = state.totalGameDays,
                generation = state.generation,
                detail = next.name,
            ),
        )
        var s = state.copy(
            resources = state.resources - cost,
            era = next,
            maxEraThisLife = maxOf(state.maxEraThisLife, next),
            maxEraEver = maxOf(state.maxEraEver, next),
        )
        // The railroad reaches town the moment the era does — once per life, big trade boost.
        if (next == com.heirloom.engine.model.Era.RAILROAD && !s.railroadArrivedThisLife) {
            s = EventSystem.fire(s, EventType.RAILROAD_ARRIVES, config, logs)
        }
        s = HeirloomChecker.check(s, config, logs)
        return appendLog(s, logs, config)
    }

    // ------------------------------------------------------------ life flow

    fun canRetire(state: GameState, config: BalanceConfig): Boolean =
        GenerationManager.canRetire(state, config)

    fun retire(state: GameState, config: BalanceConfig): GameState {
        val logs = mutableListOf<EventLogEntry>()
        val s = GenerationManager.retire(state, config, logs)
        return appendLog(s, logs, config)
    }

    fun startNextGeneration(state: GameState, config: BalanceConfig): GameState =
        GenerationManager.startNextGeneration(state, config)

    // ------------------------------------------------------------- ventures

    /** Posterity price of the next rank, or null when maxed out. */
    fun ventureCost(state: GameState, id: VentureId, config: BalanceConfig): Double? =
        config.ventureSpecs.getValue(id).costForRank(state.ventureRank(id))

    /** Ventures are buyable any time — the Legacy screen is just the natural place. */
    fun canBuyVenture(state: GameState, id: VentureId, config: BalanceConfig): Boolean {
        val cost = ventureCost(state, id, config) ?: return false
        return state.posterity >= cost
    }

    fun buyVenture(state: GameState, id: VentureId, config: BalanceConfig): GameState {
        require(canBuyVenture(state, id, config)) { "Cannot buy $id" }
        val cost = ventureCost(state, id, config)!!
        return state.copy(
            posterity = state.posterity - cost,
            ventures = state.ventures + (id to state.ventureRank(id) + 1),
        )
    }

    // ------------------------------------------------------------ monuments

    fun canFoundMonument(state: GameState, config: BalanceConfig): Boolean =
        MonumentManager.canFound(state, config)

    fun foundMonument(state: GameState, config: BalanceConfig): GameState =
        MonumentManager.found(state, config)

    // ----------------------------------------------------------- automation

    fun setAutomation(
        state: GameState,
        enabled: Boolean,
        priorities: List<ActivityId>,
        config: BalanceConfig,
    ): GameState {
        if (enabled) {
            require(state.hasMechanic(MechanicUnlock.AUTOMATION, config)) { "Automation is not unlocked" }
        }
        return state.copy(automationEnabled = enabled, automationPriorities = priorities)
    }

    // --------------------------------------------- rewarded ads / traveler

    /**
     * "A traveler lends a hand": +[BalanceConfig.rewardedAdHours] of instant progress,
     * limited per real day. The caller supplies the real epoch day so the engine stays
     * clock-free. Supporters press the same button for free — same engine path.
     */
    fun canWatchAd(state: GameState, nowEpochDay: Long, config: BalanceConfig): Boolean {
        if (state.isLegacyPending) return false
        val used = if (nowEpochDay != state.lastAdEpochDay) 0 else state.adsUsedToday
        return used < config.maxRewardedAdsPerDay
    }

    fun watchRewardedAd(state: GameState, nowEpochDay: Long, config: BalanceConfig): OfflineResult {
        require(canWatchAd(state, nowEpochDay, config)) { "No traveler boosts left today" }
        val used = if (nowEpochDay != state.lastAdEpochDay) 0 else state.adsUsedToday
        val counted = state.copy(
            adsUsedToday = used + 1,
            lastAdEpochDay = nowEpochDay,
            stats = state.stats.copy(adsWatched = state.stats.adsWatched + 1),
        )
        return OfflineProgressCalculator.apply(
            counted,
            config.rewardedAdHours * 3600.0,
            config,
            bypassCap = true,
        )
    }

    internal fun appendLog(state: GameState, logs: List<EventLogEntry>, config: BalanceConfig): GameState =
        if (logs.isEmpty()) state
        else state.copy(eventLog = (state.eventLog + logs).takeLast(config.eventLogMaxEntries))
}
