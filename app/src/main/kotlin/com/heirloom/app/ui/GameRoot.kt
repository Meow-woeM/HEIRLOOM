package com.heirloom.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heirloom.app.game.GameViewModel
import com.heirloom.app.ui.chronicle.ChronicleScreen
import com.heirloom.app.ui.family.FamilyScreen
import com.heirloom.app.ui.legacy.LegacyScreen
import com.heirloom.app.ui.main.MainScreen
import com.heirloom.app.ui.town.TownScreen
import kotlinx.coroutines.delay

private enum class GameTab(val label: String, val glyph: String) {
    HOMESTEAD("Homestead", "⌂"),
    FAMILY("Family", "✦"),
    TOWN("Town", "⚒"),
    CHRONICLE("Chronicle", "✎"),
}

@Composable
fun GameRoot(viewModel: GameViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val state = ui.state
    var tab by rememberSaveable { mutableStateOf(GameTab.HOMESTEAD.name) }

    // The Town Vista's stage-reveal moment: crossfade happens in place; this is the toast.
    var revealText by remember { mutableStateOf<String?>(null) }
    var lastStage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state?.vistaStage) {
        val stage = state?.vistaStage ?: return@LaunchedEffect
        if (lastStage != null && lastStage != stage.name) {
            revealText = "The settlement grows: ${stage.displayName}"
            lastStage = stage.name
            delay(4000)
            revealText = null
        } else {
            lastStage = stage.name
        }
    }

    when {
        state == null -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Unpacking the wagon…", style = MaterialTheme.typography.titleLarge)
            }
        }

        // Death pauses everything: the Legacy screen takes the whole stage.
        state.isLegacyPending -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LegacyScreen(state, viewModel)
        }

        else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    GameTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t.name,
                            onClick = { tab = t.name },
                            icon = { Text(t.glyph, style = MaterialTheme.typography.titleLarge) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (GameTab.valueOf(tab)) {
                    GameTab.HOMESTEAD -> MainScreen(state, viewModel)
                    GameTab.FAMILY -> FamilyScreen(state, viewModel)
                    GameTab.TOWN -> TownScreen(state, viewModel)
                    GameTab.CHRONICLE -> ChronicleScreen(state, viewModel)
                }
            }
        }
    }

    // Overlays: stage reveal toast + the away sheet.
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = revealText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 6.dp,
            ) {
                Text(
                    revealText ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
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
