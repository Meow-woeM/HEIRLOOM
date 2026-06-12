package com.heirloom.app.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heirloom.app.data.SaveRepository
import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.SkillId
import com.heirloom.engine.model.VentureId
import com.heirloom.engine.sim.OfflineProgressCalculator
import com.heirloom.engine.sim.OfflineSummary
import com.heirloom.engine.sim.PlayerActions
import com.heirloom.engine.sim.TickEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GameUiState(
    val state: GameState? = null, // null while loading
    val offlineSummary: OfflineSummary? = null, // non-null -> show the away sheet
)

/**
 * Owns the loop: StateFlow<GameState> as the single source of truth, a 1-second ticker
 * while foregrounded, offline catch-up on every return, autosave every 30s and on stop.
 * All gameplay mutations delegate to the pure engine.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val repo: SaveRepository,
    val config: BalanceConfig,
) : ViewModel() {

    private val _ui = MutableStateFlow(GameUiState())
    val ui: StateFlow<GameUiState> = _ui.asStateFlow()

    private var tickerJob: Job? = null
    private var booted = false
    private var foreground = false

    init {
        viewModelScope.launch {
            val loaded = repo.load()
            var state = loaded ?: GameState.newGame(config, System.currentTimeMillis())
            var summary: OfflineSummary? = null
            if (loaded != null) {
                val caughtUp = catchUp(loaded)
                state = caughtUp.first
                summary = caughtUp.second
            }
            state = state.copy(lastRealTimeMillis = System.currentTimeMillis())
            _ui.value = GameUiState(state, summary)
            repo.save(state)
            booted = true
            if (foreground) startTicker()
            autosaveLoop()
        }
    }

    /** Fast-forward the away time; only absences worth talking about get a sheet. */
    private fun catchUp(state: GameState): Pair<GameState, OfflineSummary?> {
        val now = System.currentTimeMillis()
        if (state.lastRealTimeMillis <= 0L || now <= state.lastRealTimeMillis) return state to null
        val elapsedSeconds = (now - state.lastRealTimeMillis) / 1000.0
        val result = OfflineProgressCalculator.apply(state, elapsedSeconds, config)
        val summary = result.summary.takeIf { elapsedSeconds >= config.offlineSummaryMinAwaySeconds }
        return result.state to summary
    }

    // -------------------------------------------------------------- lifecycle

    fun onAppForeground() {
        foreground = true
        if (!booted) return
        val current = _ui.value.state ?: return
        val (state, summary) = catchUp(current)
        _ui.update {
            it.copy(
                state = state.copy(lastRealTimeMillis = System.currentTimeMillis()),
                offlineSummary = summary ?: it.offlineSummary,
            )
        }
        startTicker()
    }

    fun onAppBackground() {
        foreground = false
        tickerJob?.cancel()
        tickerJob = null
        if (!booted) return
        val stamped = _ui.value.state?.copy(lastRealTimeMillis = System.currentTimeMillis()) ?: return
        _ui.update { it.copy(state = stamped) }
        viewModelScope.launch { repo.save(stamped) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var lastMillis = System.currentTimeMillis()
            while (isActive) {
                delay(1_000L)
                val now = System.currentTimeMillis()
                val dtDays = (now - lastMillis) / 1000.0 * config.gameDaysPerRealSecond
                lastMillis = now
                mutate { TickEngine.tick(it, dtDays, config).state.copy(lastRealTimeMillis = now) }
            }
        }
    }

    private fun autosaveLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                _ui.value.state?.let { repo.save(it) }
            }
        }
    }

    /** Apply a pure engine action; an action whose guard raced a tick is simply dropped. */
    private fun mutate(block: (GameState) -> GameState) {
        _ui.update { ui ->
            val current = ui.state ?: return@update ui
            val next = try {
                block(current)
            } catch (_: IllegalArgumentException) {
                current
            } catch (_: IllegalStateException) {
                current
            }
            ui.copy(state = next)
        }
    }

    // ---------------------------------------------------------------- actions

    fun selectActivity(id: ActivityId) = mutate { PlayerActions.selectActivity(it, id, config) }
    fun selectSkill(id: SkillId) = mutate { PlayerActions.selectSkill(it, id) }
    fun selectSecondSkill(id: SkillId?) = mutate { PlayerActions.selectSecondSkill(it, id, config) }
    fun advanceEra() = mutate { PlayerActions.advanceEra(it, config) }
    fun retire() = mutate { PlayerActions.retire(it, config) }
    fun beginNextGeneration() = mutate { PlayerActions.startNextGeneration(it, config) }
    fun buyVenture(id: VentureId) = mutate { PlayerActions.buyVenture(it, id, config) }
    fun foundMonument() = mutate { PlayerActions.foundMonument(it, config) }
    fun setAutomation(enabled: Boolean, priorities: List<ActivityId>) =
        mutate { PlayerActions.setAutomation(it, enabled, priorities, config) }

    /** Engine half of "a traveler lends a hand" — M5's ad/billing layer gates the button. */
    fun grantTravelerBoost() {
        val epochDay = System.currentTimeMillis() / 86_400_000L
        mutate { PlayerActions.watchRewardedAd(it, epochDay, config).state }
    }

    fun setSupporter(active: Boolean) = mutate { it.copy(supporter = active) }

    fun dismissOfflineSummary() = _ui.update { it.copy(offlineSummary = null) }

    override fun onCleared() {
        // Best-effort final stamp; autosave has us covered within 30s regardless.
        super.onCleared()
    }
}
