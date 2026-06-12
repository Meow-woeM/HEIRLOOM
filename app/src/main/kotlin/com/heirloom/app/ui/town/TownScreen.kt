package com.heirloom.app.ui.town

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heirloom.app.game.GameViewModel
import com.heirloom.app.ui.compact
import com.heirloom.app.ui.main.SectionTitle
import com.heirloom.app.ui.vista.TownVista
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.MechanicUnlock
import com.heirloom.engine.sim.CostCalculator
import com.heirloom.engine.sim.MonumentManager
import com.heirloom.engine.sim.PlayerActions

/** The era progression map, the Monument plaza, and Standing Orders (automation). */
@Composable
fun TownScreen(state: GameState, vm: GameViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TownVista(state, vm.config) }
        item { MonumentPlaza(state, vm) }
        item { StandingOrders(state, vm) }
        item { SectionTitle("The road west — eras") }
        items(count = Era.entries.size) { index ->
            EraRow(state, Era.entries[index], vm)
        }
        item { SupporterCard(state, vm) }
        item { SettingsCard(state, vm) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MonumentPlaza(state: GameState, vm: GameViewModel) {
    val config = vm.config
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("Monument Plaza", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.monuments == 0) {
                    "No monuments yet. Reach ${Era.CITY.displayName} to found the first."
                } else {
                    "✦".repeat(state.monuments.coerceAtMost(12)) +
                        "  ${state.monuments} of ${config.finalMonument} founded" +
                        if (MonumentManager.isComplete(state, config)) " — the future city stands." else ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.monuments > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!MonumentManager.isComplete(state, config)) {
                val cost = CostCalculator.monumentCost(state, config)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Next monument: ${cost.materials.compact()} materials, ${cost.money.compact()} money, " +
                        "${cost.standing.compact()} standing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            config.mechanicUnlocks.toSortedMap().forEach { (count, mechanic) ->
                val earned = state.monuments >= count
                Text(
                    (if (earned) "✓ " else "◌ ") + "Monument $count — ${mechanic.displayName}: ${mechanic.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (earned) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun StandingOrders(state: GameState, vm: GameViewModel) {
    val config = vm.config
    val unlocked = state.hasMechanic(MechanicUnlock.AUTOMATION, config)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Standing Orders", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (unlocked) {
                            "Auto-switch work daily: food first when stores run low, then your priorities."
                        } else {
                            "Unlocks with the first Monument."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.automationEnabled,
                    onCheckedChange = { on -> vm.setAutomation(on, state.automationPriorities) },
                    enabled = unlocked,
                )
            }
            if (unlocked && state.automationEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap work to add or remove it (in priority order):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActivityId.entries.filter { it.era.index <= state.era.index }.forEach { activity ->
                    val position = state.automationPriorities.indexOf(activity)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (position >= 0) "${position + 1}." else "·",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (position >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            activity.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val next = if (position >= 0) {
                                        state.automationPriorities - activity
                                    } else {
                                        state.automationPriorities + activity
                                    }
                                    vm.setAutomation(true, next)
                                }
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EraRow(state: GameState, era: Era, vm: GameViewModel) {
    val config = vm.config
    val reachedEver = state.maxEraEver.index >= era.index
    val current = state.era == era
    val marker = when {
        current -> "➤"
        reachedEver -> "✓"
        else -> "◌"
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (current) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("$marker  ${era.displayName}", style = MaterialTheme.typography.titleMedium)
            Text(
                ActivityId.forEra(era).joinToString { it.displayName },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val cost = config.eraCosts[era]
            if (cost != null && !reachedEver) {
                Text(
                    "Costs ${cost.materials.compact()} materials, ${cost.money.compact()} money" +
                        (if (cost.standing > 0) ", ${cost.standing.compact()} standing" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
