package com.heirloom.app.ui.town

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heirloom.app.game.GameViewModel
import com.heirloom.app.ui.findActivity
import com.heirloom.engine.model.GameState
import kotlinx.coroutines.launch

private val Gold = Color(0xFFC9A227)

/**
 * "The Family Legacy" Supporter Pack. Surfaced gently — this card and one shelf slot
 * on the Legacy screen; it never interrupts play.
 */
@Composable
fun SupporterCard(state: GameState, vm: GameViewModel) {
    val context = LocalContext.current
    val price by vm.supporterStore.priceText.collectAsStateWithLifecycle()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("❖ The Family Legacy", style = MaterialTheme.typography.titleLarge, color = Gold)
            Spacer(Modifier.height(4.dp))
            if (state.supporter) {
                Text(
                    "The family thanks you. No ads, double yields, and the shelf gleams golden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Support the maker, once: removes all ads (travelers help freely), a permanent " +
                        "2× to all yields, and a golden frame for the heirloom shelf.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    context.findActivity()?.let { vm.supporterStore.launchPurchase(it) }
                }) {
                    Text(price?.let { "Become a Supporter · $it" } ?: "Become a Supporter")
                }
            }
        }
    }
}

/** Tick-speed display, save export/import, and art attribution. */
@Composable
fun SettingsCard(state: GameState, vm: GameViewModel) {
    val config = vm.config
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("Almanac & Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            val secondsPerDay = 1.0 / config.gameDaysPerRealSecond
            val minutesPerYear = config.daysPerYear * secondsPerDay / 60.0
            Text(
                "Time flows at 1 day ≈ ${secondsPerDay.toInt()}s · 1 year ≈ ${minutesPerYear.toInt()} min. " +
                    "Away progress is banked up to " +
                    "${com.heirloom.engine.sim.OfflineProgressCalculator.offlineCapHours(state, config).toInt()}h.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val json = vm.exportSave()
                        if (json != null) {
                            clipboard.setText(AnnotatedString(json))
                            message = "Save copied to the clipboard."
                        } else {
                            message = "Nothing to export yet."
                        }
                    }
                }) { Text("Export save") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { confirmImport = true }) { Text("Import save") }
            }
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Art: placeholder vista scenes are drawn in code; licensed pixel-art packs can be " +
                    "dropped into assets/vista (see ATTRIBUTION.md in the repository).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Import save from clipboard?") },
            text = {
                Text(
                    "This replaces the current family with the save on the clipboard. " +
                        "There is no undo — export the current save first if it matters.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    val text = clipboard.getText()?.text
                    if (text.isNullOrBlank()) {
                        message = "The clipboard is empty."
                    } else {
                        vm.importSave(text) { ok ->
                            message = if (ok) "Save imported." else "That wasn't a valid Heirloom save."
                        }
                    }
                }) { Text("Replace everything") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text("Keep my family") }
            },
        )
    }
}
