package com.heirloom.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.LogKind
import com.heirloom.engine.sim.OfflineSummary

/** The "While you were away" sheet — the emotional core of a check-in game. */
@Composable
fun AwaySummaryDialog(
    summary: OfflineSummary,
    config: BalanceConfig,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("While you were away…", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Line("Gone ${formatRealDuration(summary.realSecondsElapsed)} — " +
                    "${summary.gameDaysSimulated.toInt()} days passed on the homestead.")
                if (summary.yearsPassed > 0) {
                    Line("${summary.yearsPassed} year(s), ${summary.seasonsPassed} season(s) turned.")
                } else if (summary.seasonsPassed > 0) {
                    Line("${summary.seasonsPassed} season(s) turned.")
                }
                val r = summary.resourcesGained
                if (r.food != 0.0) Line("Food ${signed(r.food)}")
                if (r.materials != 0.0) Line("Materials ${signed(r.materials)}")
                if (r.money != 0.0) Line("Money ${signed(r.money)}")
                if (r.standing != 0.0) Line("Standing ${signed(r.standing)}")
                if (summary.activityLevelsGained > 0) Line("Work improved ${summary.activityLevelsGained} level(s).")
                if (summary.skillLevelsGained > 0) Line("Skills grew ${summary.skillLevelsGained} level(s).")
                val events = summary.logEntries.count { it.kind == LogKind.RANDOM_EVENT }
                if (events > 0) Line("$events thing(s) happened — see the chronicle.")
                if (summary.cappedByLimit) {
                    Line("The wagons could only carry ${"%.0f".format(summary.realSecondsCounted / 3600)}h of progress.")
                }
                if (summary.lifeEnded) {
                    Line("")
                    Line(
                        "The pioneer's journey has ended. +${summary.posterityEarned.compact()} Posterity awaits.",
                        emphasize = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (summary.lifeEnded) "Pay respects" else "Carry on") }
        },
    )
}

@Composable
private fun Line(text: String, emphasize: Boolean = false) {
    Text(
        text,
        style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

private fun signed(value: Double): String = (if (value > 0) "+" else "") + value.compact()
