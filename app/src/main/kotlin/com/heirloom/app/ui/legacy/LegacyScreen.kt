package com.heirloom.app.ui.legacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.heirloom.app.game.GameViewModel
import com.heirloom.app.ui.compact
import com.heirloom.app.ui.main.SectionTitle
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.VentureId
import com.heirloom.engine.sim.HeirloomChecker
import com.heirloom.engine.sim.PlayerActions

/** Death/retirement flow: tombstone summary, Posterity, the Family Ventures shop, the heir. */
@Composable
fun LegacyScreen(state: GameState, vm: GameViewModel) {
    val config = vm.config
    val legacy = state.pendingLegacy ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("✦", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.outline)
                    Text(
                        "Generation ${legacy.generation}",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (legacy.voluntary) "Retired at ${legacy.ageAtDeathYears.toInt()}, content."
                        else "At rest, aged ${legacy.ageAtDeathYears.toInt()}.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Reached ${legacy.eraReached.displayName} · survived ${legacy.wintersSurvived} winters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "+${legacy.posterityEarned.compact()} Posterity",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${state.posterity.compact()} held · every point lifts all yields by " +
                            "${(config.posterityYieldBonusPerPoint * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (legacy.heirloomsUnlockedThisLife.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "New heirlooms: " + legacy.heirloomsUnlockedThisLife.joinToString { it.displayName },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = vm::beginNextGeneration,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("The heir takes the reins") }
        }

        item { SectionTitle("Family Ventures — spend Posterity") }
        items(VentureId.entries) { venture ->
            VentureRow(state, venture, vm)
        }

        item { SectionTitle("Heirloom shelf — ${state.heirlooms.size}/${HeirloomId.entries.size}") }
        items(HeirloomId.entries) { heirloom ->
            HeirloomRow(state, heirloom, vm)
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun VentureRow(state: GameState, venture: VentureId, vm: GameViewModel) {
    val config = vm.config
    val rank = state.ventureRank(venture)
    val spec = config.ventureSpecs.getValue(venture)
    val cost = PlayerActions.ventureCost(state, venture, config)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val rankText = spec.maxRanks?.let { max -> "$rank/$max" } ?: "$rank"
            Text("${venture.displayName} · $rankText", style = MaterialTheme.typography.bodyLarge)
            Text(
                venture.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (cost != null) {
            OutlinedButton(
                onClick = { vm.buyVenture(venture) },
                enabled = PlayerActions.canBuyVenture(state, venture, config),
            ) { Text(cost.compact()) }
        } else {
            Text("Complete", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HeirloomRow(state: GameState, heirloom: HeirloomId, vm: GameViewModel) {
    val earned = state.hasHeirloom(heirloom)
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            if (earned) heirloom.displayName else "◌ ${heirloom.displayName}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (earned) heirloom.flavor else HeirloomChecker.conditionText(heirloom, vm.config),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
