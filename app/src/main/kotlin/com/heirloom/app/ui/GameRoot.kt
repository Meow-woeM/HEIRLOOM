package com.heirloom.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heirloom.app.game.GameViewModel
import com.heirloom.app.ui.legacy.LegacyScreen
import com.heirloom.app.ui.main.MainScreen

@Composable
fun GameRoot(viewModel: GameViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val state = ui.state
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Unpacking the wagon…", style = MaterialTheme.typography.titleLarge)
            }
            state.isLegacyPending -> LegacyScreen(state, viewModel)
            else -> MainScreen(state, viewModel)
        }
    }
    val summary = ui.offlineSummary
    if (summary != null && state != null) {
        AwaySummaryDialog(
            summary = summary,
            config = viewModel.config,
            onDismiss = viewModel::dismissOfflineSummary,
        )
    }
}
