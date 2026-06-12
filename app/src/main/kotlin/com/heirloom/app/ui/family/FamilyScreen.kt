package com.heirloom.app.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heirloom.app.game.GameViewModel
import com.heirloom.app.ui.compact
import com.heirloom.app.ui.legacy.HeirloomShelfSection
import com.heirloom.app.ui.legacy.VentureShopSection
import com.heirloom.engine.model.GameState
import com.heirloom.engine.sim.GenerationManager
import com.heirloom.engine.sim.PlayerActions

/** The living line's prestige view: posterity counter, retire preview, shop, shelf. */
@Composable
fun FamilyScreen(state: GameState, vm: GameViewModel) {
    val config = vm.config
    val preview = GenerationManager.posterityPreview(state, config)
    val passive = 1.0 + config.posterityYieldBonusPerPoint * state.posterity
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("The Line", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Generation ${state.generation} · ${state.stats.generationsCompleted} lives remembered",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.posterity.compact()} Posterity",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "All yields ×${"%.2f".format(passive)} from posterity held",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Retire now for +${preview.compact()}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (PlayerActions.canRetire(state, config)) "The heir is ready."
                                else "Heirs may take over from age ${config.retirementMinAgeYears.toInt()}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = vm::retire,
                            enabled = PlayerActions.canRetire(state, config),
                        ) { Text("Retire") }
                    }
                }
            }
        }
        item { VentureShopSection(state, vm) }
        item { HeirloomShelfSection(state, vm) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
