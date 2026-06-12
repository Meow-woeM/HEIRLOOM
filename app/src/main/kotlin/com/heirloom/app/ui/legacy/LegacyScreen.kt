package com.heirloom.app.ui.legacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.heirloom.app.game.GameViewModel
import com.heirloom.app.ui.compact
import com.heirloom.engine.model.GameState

/** The forced death/retirement flow: tombstone, Posterity, shopping, then the heir. */
@Composable
fun LegacyScreen(state: GameState, vm: GameViewModel) {
    val config = vm.config
    val legacy = state.pendingLegacy ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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
                    Text(
                        "A lifetime's work: ${legacy.lifetimeEarned.food.compact()} food · " +
                            "${legacy.lifetimeEarned.materials.compact()} materials · " +
                            "${legacy.lifetimeEarned.money.compact()} money",
                        style = MaterialTheme.typography.bodySmall,
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

        item { VentureShopSection(state, vm) }
        item { HeirloomShelfSection(state, vm) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
