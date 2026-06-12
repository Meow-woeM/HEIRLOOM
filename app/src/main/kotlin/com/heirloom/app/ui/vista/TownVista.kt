package com.heirloom.app.ui.vista

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.heirloom.engine.balance.BalanceConfig
import com.heirloom.engine.model.GameState

/**
 * The Town Vista: the settlement's permanent growth across the whole save. It NEVER
 * resets — decorative seasoning above a numbers game, not a world. Static layered
 * scene; licensed pixel art is swapped in via assets/vista (FilterQuality.None —
 * pixel art must never be bilinear-blurred).
 */
@Composable
fun TownVista(state: GameState, config: BalanceConfig, modifier: Modifier = Modifier) {
    val stage = state.vistaStage
    val season = state.season(config)
    val context = LocalContext.current
    Box(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Crossfade(targetState = stage, animationSpec = tween(900), label = "vistaStage") { current ->
            val bitmap = remember(current) { VistaAssets.stageBitmap(context, current) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = current.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                )
            } else {
                VistaPlaceholder(current, season, state.monuments, Modifier.fillMaxSize())
            }
        }
        Text(
            "${stage.displayName} · ${season.displayName}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xEEFAF4E6),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
