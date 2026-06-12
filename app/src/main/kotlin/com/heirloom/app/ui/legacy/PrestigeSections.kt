package com.heirloom.app.ui.legacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heirloom.app.game.GameViewModel
import com.heirloom.app.ui.compact
import com.heirloom.app.ui.findActivity
import com.heirloom.app.ui.main.SectionTitle
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.VentureId
import com.heirloom.engine.sim.HeirloomChecker
import com.heirloom.engine.sim.PlayerActions

/** The Family Ventures shop — shared by the live Family tab and the Legacy screen. */
@Composable
fun VentureShopSection(state: GameState, vm: GameViewModel) {
    Column {
        SectionTitle("Family Ventures — spend Posterity")
        VentureId.entries.forEach { venture ->
            VentureRow(state, venture, vm)
        }
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

private val Gold = androidx.compose.ui.graphics.Color(0xFFC9A227)

/**
 * Earned heirlooms with flavor; locked ones as silhouettes with their condition.
 * Supporters get the golden frame; non-supporters get exactly one quiet shelf slot
 * mentioning the pack (the only other surface is the Town settings card).
 */
@Composable
fun HeirloomShelfSection(state: GameState, vm: GameViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column {
        SectionTitle("Heirloom shelf — ${state.heirlooms.size}/${HeirloomId.entries.size}")
        HeirloomId.entries.forEach { heirloom ->
            val earned = state.hasHeirloom(heirloom)
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(
                    when {
                        earned && state.supporter -> "❖ ${heirloom.displayName}"
                        earned -> heirloom.displayName
                        else -> "◌ ${heirloom.displayName}"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        earned && state.supporter -> Gold
                        earned -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    if (earned) heirloom.flavor else HeirloomChecker.conditionText(heirloom, vm.config),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!state.supporter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.findActivity()?.let { vm.supporterStore.launchPurchase(it) }
                    }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    "❖ The Family Legacy",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Gold.copy(alpha = 0.7f),
                )
                Text(
                    "A one-time gift to the maker: no ads, 2× yields, a golden shelf.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
