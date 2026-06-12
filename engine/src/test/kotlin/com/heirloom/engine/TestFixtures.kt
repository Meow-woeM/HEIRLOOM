package com.heirloom.engine

import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.GameState

object T {
    /** The real shipped balance — never mocked. */
    val cfg = BalanceConfig.DEFAULT

    /** Real balance with random events silenced, for exact-value determinism. */
    val quiet = cfg.copy(eventChancePerDay = 0.0)

    fun newState(config: BalanceConfig = quiet, seed: Long = 7L): GameState =
        GameState.newGame(config, seed)
}
