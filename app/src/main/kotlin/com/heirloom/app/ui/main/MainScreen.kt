package com.heirloom.app.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heirloom.app.game.GameViewModel
import com.heirloom.app.ui.compact
import com.heirloom.app.ui.findActivity
import com.heirloom.app.ui.stamp
import com.heirloom.app.ui.title
import com.heirloom.app.ui.vista.TownVista
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.GameState
import com.heirloom.engine.model.ResourceType
import com.heirloom.engine.model.Season
import com.heirloom.engine.model.SkillId
import com.heirloom.engine.sim.CostCalculator
import com.heirloom.engine.sim.Curves
import com.heirloom.engine.sim.GenerationManager
import com.heirloom.engine.sim.PlayerActions
import com.heirloom.engine.sim.YieldCalculator

@Composable
fun MainScreen(state: GameState, vm: GameViewModel) {
    val config = vm.config
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TownVista(state, config) }
        item { HeaderCard(state, vm) }
        item { ResourceBar(state, vm) }
        item { LatestNews(state, vm) }
        item { EraCard(state, vm) }
        item { TravelerCard(state, vm) }
        if (PlayerActions.canRetire(state, config) || GenerationManager.posterityPreview(state, config) > 0) {
            item { RetireCard(state, vm) }
        }
        item { SectionTitle("Work — one pair of hands") }
        items(ActivityId.entries.filter { it.era.index <= state.era.index }) { activity ->
            ActivityRow(state, activity, vm)
        }
        item { SectionTitle("Training — one mind at a time") }
        items(SkillId.entries) { skill ->
            SkillRow(state, skill, vm)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeaderCard(state: GameState, vm: GameViewModel) {
    val config = vm.config
    val season = state.season(config)
    val year = Season.yearAtDay(state.totalGameDays, config)
    val deathAge = GenerationManager.deathAgeYears(state, config)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(state.era.displayName, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Generation ${state.generation} · ${season.displayName}, Year $year",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Age ${state.ageYears(config).toInt()} of a hoped-for ~${deathAge.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.posterity > 0 || state.monuments > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (state.posterity > 0) append("Posterity ${state.posterity.compact()}")
                        if (state.monuments > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${state.monuments} Monument(s)")
                        }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.starving) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "The larder is empty — everyone is weak with hunger.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.springBountyActive && season == Season.SPRING) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Spring Bounty — the winter was kind, all work +25%.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ResourceBar(state: GameState, vm: GameViewModel) {
    val upkeep = YieldCalculator.dailyFoodUpkeep(state, vm.config)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ResourceCell("Food", state.resources.food, "−${upkeep.compact()}/day")
            ResourceCell("Materials", state.resources.materials)
            ResourceCell("Money", state.resources.money)
            ResourceCell("Standing", state.resources.standing)
        }
    }
}

@Composable
private fun ResourceCell(label: String, amount: Double, note: String? = null) {
    // Numbers glide instead of jumping (Float precision is plenty for display).
    val animated by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(durationMillis = 600),
        label = "resource-$label",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(animated.toDouble().compact(), style = MaterialTheme.typography.titleMedium)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LatestNews(state: GameState, vm: GameViewModel) {
    val latest = state.eventLog.lastOrNull() ?: return
    Text(
        "“${latest.title()}” — ${latest.stamp(vm.config)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun TravelerCard(state: GameState, vm: GameViewModel) {
    val config = vm.config
    val context = androidx.compose.ui.platform.LocalContext.current
    val adsReady by vm.ads.ready.collectAsStateWithLifecycle()
    val epochDay = System.currentTimeMillis() / 86_400_000L
    val used = if (epochDay != state.lastAdEpochDay) 0 else state.adsUsedToday
    val left = (config.maxRewardedAdsPerDay - used).coerceAtLeast(0)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("A traveler lends a hand", style = MaterialTheme.typography.titleMedium)
                Text(
                    "+${config.rewardedAdHours.toInt()} hours of progress · $left left today" +
                        when {
                            state.supporter -> " · yours freely"
                            else -> " · a short ad"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = {
                    if (state.supporter) {
                        vm.grantTravelerBoost()
                    } else {
                        context.findActivity()?.let { activity ->
                            vm.ads.show(activity) { vm.grantTravelerBoost() }
                        }
                    }
                },
                enabled = PlayerActions.canWatchAd(state, epochDay, config) &&
                    (state.supporter || adsReady),
            ) { Text("Welcome them") }
        }
    }
}

@Composable
private fun EraCard(state: GameState, vm: GameViewModel) {
    val config = vm.config
    val next = state.era.next
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            if (next != null) {
                val cost = CostCalculator.eraCost(state, next, config)
                Text("Onward: ${next.displayName}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                CostLine("Materials", cost.materials, state.resources.materials)
                CostLine("Money", cost.money, state.resources.money)
                if (cost.standing > 0) CostLine("Standing", cost.standing, state.resources.standing)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = vm::advanceEra,
                    enabled = PlayerActions.canAdvanceEra(state, config),
                ) { Text("Advance the family") }
            } else {
                val cost = CostCalculator.monumentCost(state, config)
                Text("Found the Monument", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Complete the line. Posterity and Ventures are given to the town; heirlooms endure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                CostLine("Materials", cost.materials, state.resources.materials)
                CostLine("Money", cost.money, state.resources.money)
                CostLine("Standing", cost.standing, state.resources.standing)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = vm::foundMonument,
                    enabled = PlayerActions.canFoundMonument(state, config),
                ) { Text("Erect Monument ${state.monuments + 1}") }
            }
        }
    }
}

@Composable
private fun CostLine(label: String, cost: Double, have: Double) {
    val enough = have >= cost
    Text(
        "$label: ${have.compact()} / ${cost.compact()}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (enough) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun RetireCard(state: GameState, vm: GameViewModel) {
    val config = vm.config
    val preview = GenerationManager.posterityPreview(state, config)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Pass the homestead on", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Retire now for +${preview.compact()} Posterity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = vm::retire,
                enabled = PlayerActions.canRetire(state, config),
            ) {
                Text(if (PlayerActions.canRetire(state, config)) "Retire" else "From age 40")
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Column {
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ActivityRow(state: GameState, activity: ActivityId, vm: GameViewModel) {
    val config = vm.config
    val unlocked = PlayerActions.isActivityUnlocked(state, activity, config)
    val selected = state.activeActivity == activity
    val yieldPerDay = YieldCalculator.dailyYield(state, activity, config)
    val level = state.activityLevel(activity)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = { vm.selectActivity(activity) },
            enabled = unlocked,
        )
        Column(Modifier.weight(1f)) {
            Text(
                activity.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val detail = if (unlocked) {
                "Lv $level · +${yieldPerDay.compact()} ${resourceShort(activity.resource)}/day"
            } else {
                "Needs Community ${config.communityGates[activity] ?: 0}"
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun SkillRow(state: GameState, skill: SkillId, vm: GameViewModel) {
    val config = vm.config
    val selected = state.activeSkill == skill
    val progress = state.skillProgress[skill]
    val level = progress?.level ?: 0
    val xp = progress?.xp ?: 0.0
    val toNext = Curves.xpToNextSkillLevel(level, config)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = { vm.selectSkill(skill) })
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${skill.displayName} $level", style = MaterialTheme.typography.bodyLarge)
                if (state.secondSkill == skill) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "night study",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { (xp / toNext).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, end = 16.dp),
            )
        }
    }
}

private fun resourceShort(type: ResourceType): String = when (type) {
    ResourceType.FOOD -> "food"
    ResourceType.MATERIALS -> "mat"
    ResourceType.MONEY -> "$"
    ResourceType.STANDING -> "standing"
}
