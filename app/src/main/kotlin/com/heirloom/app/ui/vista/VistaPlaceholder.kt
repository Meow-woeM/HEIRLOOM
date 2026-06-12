package com.heirloom.app.ui.vista

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.heirloom.engine.model.Season
import com.heirloom.engine.model.VistaStage

/**
 * Programmatic vista scenes: flat-color silhouette skylines on a 360x140 design grid,
 * sky tinted by season. Stands in until licensed pixel art lands in assets/vista/.
 */
@Composable
fun VistaPlaceholder(
    stage: VistaStage,
    season: Season,
    monuments: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val sx = size.width / 360f
        val sy = size.height / 140f

        fun rect(x: Float, y: Float, w: Float, h: Float, color: Color) =
            drawRect(color, Offset(x * sx, y * sy), Size(w * sx, h * sy))

        fun circle(cx: Float, cy: Float, r: Float, color: Color) =
            drawCircle(color, r * sx, Offset(cx * sx, cy * sy))

        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Color) {
            val p = Path().apply {
                moveTo(x1 * sx, y1 * sy); lineTo(x2 * sx, y2 * sy); lineTo(x3 * sx, y3 * sy); close()
            }
            drawPath(p, color)
        }

        // ---- sky & ground, tinted by season ----
        val (skyTop, skyBottom) = when (season) {
            Season.SPRING -> Color(0xFF9DC4AE) to Color(0xFFE2EDD3)
            Season.SUMMER -> Color(0xFF8FB6D6) to Color(0xFFEFE6C8)
            Season.FALL -> Color(0xFFC99E63) to Color(0xFFF0DCB4)
            Season.WINTER -> Color(0xFFAEB6BE) to Color(0xFFE8EBEE)
        }
        val ground = when (season) {
            Season.SPRING -> Color(0xFF7C9B6B)
            Season.SUMMER -> Color(0xFF96A468)
            Season.FALL -> Color(0xFFA08252)
            Season.WINTER -> Color(0xFFD9DEE3)
        }
        drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom)), Offset.Zero, size)
        rect(0f, 110f, 360f, 30f, ground)

        val far = Color(0xFF6B5B48)
        val near = Color(0xFF3A2E20)
        val canvasCloth = Color(0xFFD9C9A8)
        val glow = Color(0xFFE8A23C)
        val smoke = Color(0x66FFFFFF)

        // distant hills on every stage
        tri(-30f, 110f, 60f, 70f, 150f, 110f, far.copy(alpha = 0.45f))
        tri(220f, 110f, 305f, 62f, 390f, 110f, far.copy(alpha = 0.45f))

        fun wagon(x: Float, scale: Float) {
            rect(x, 92f - 14f * scale, 40f * scale, 14f * scale, near)
            rect(x - 2f * scale, 92f - 28f * scale, 44f * scale, 15f * scale, canvasCloth)
            circle(x + 8f * scale, 104f - 6f * scale, 6f * scale, near)
            circle(x + 32f * scale, 104f - 6f * scale, 6f * scale, near)
        }

        fun pine(x: Float, h: Float) {
            tri(x - 8f, 110f, x, 110f - h, x + 8f, 110f, near)
            rect(x - 1.5f, 104f, 3f, 6f, near)
        }

        fun house(x: Float, w: Float, h: Float, lit: Boolean) {
            rect(x, 110f - h, w, h, near)
            tri(x - 2f, 110f - h, x + w / 2f, 110f - h - w * 0.4f, x + w + 2f, 110f - h, near)
            if (lit) rect(x + w * 0.55f, 110f - h * 0.6f, 5f, 6f, glow)
        }

        fun falseFront(x: Float, w: Float, h: Float, lit: Boolean) {
            rect(x, 110f - h, w, h, near)
            rect(x - 1.5f, 110f - h - 4f, w + 3f, 4f, near)
            if (lit) rect(x + w / 2f - 2.5f, 110f - h + 6f, 5f, 6f, glow)
        }

        fun tower(x: Float, w: Float, h: Float, windows: Int) {
            rect(x, 110f - h, w, h, near)
            repeat(windows) { i ->
                rect(x + w * 0.25f, 110f - h + 7f + i * 14f, 3.5f, 4.5f, glow)
                rect(x + w * 0.62f, 110f - h + 7f + i * 14f, 3.5f, 4.5f, glow)
            }
        }

        fun statues(count: Int) {
            repeat(count.coerceIn(0, 6)) { i ->
                val x = 308f + (i % 3) * 16f
                val y = if (i < 3) 110f else 118f
                rect(x, y - 14f, 4f, 14f, near)
                rect(x - 2f, y, 8f, 3f, near)
            }
        }

        when (stage) {
            VistaStage.WAGON_CAMP -> {
                wagon(64f, 1f)
                wagon(160f, 0.8f)
                rect(126f, 102f, 10f, 3f, near) // logs
                circle(131f, 98f, 4f, glow) // campfire
                circle(131f, 88f, 3f, smoke)
                pine(262f, 34f); pine(292f, 42f); pine(318f, 30f)
            }

            VistaStage.HOMESTEAD -> {
                house(76f, 50f, 26f, lit = true)
                rect(96f, 96f, 8f, 14f, near) // door
                repeat(5) { i -> rect(160f + i * 22f, 114f, 14f, 2.5f, ground.copy(alpha = 0.55f).compositeOverInk()) }
                repeat(6) { i -> rect(150f + i * 18f, 102f, 2.5f, 8f, near) } // fence posts
                pine(300f, 40f); pine(326f, 30f)
                wagon(20f, 0.7f)
            }

            VistaStage.VILLAGE -> {
                // mill with crossed blades
                rect(58f, 64f, 26f, 46f, near)
                tri(56f, 64f, 71f, 50f, 86f, 64f, near)
                drawLine(near, Offset(71f * sx, 56f * sy), Offset(94f * sx, 32f * sy), 3f * sx)
                drawLine(near, Offset(48f * sx, 34f * sy), Offset(94f * sx, 56f * sy), 3f * sx)
                falseFront(120f, 36f, 24f, lit = true) // general store
                rect(184f, 82f, 24f, 28f, near) // chapel
                tri(182f, 82f, 196f, 60f, 210f, 82f, near)
                rect(194f, 64f, 3f, 8f, near) // spire
                house(240f, 30f, 20f, lit = false)
                house(282f, 30f, 22f, lit = true)
            }

            VistaStage.FRONTIER_TOWN -> {
                falseFront(40f, 34f, 34f, lit = true)
                falseFront(82f, 40f, 44f, lit = false)
                falseFront(130f, 36f, 38f, lit = true)
                falseFront(174f, 44f, 30f, lit = true)
                falseFront(226f, 34f, 40f, lit = false)
                // water tower
                rect(290f, 70f, 22f, 16f, near)
                repeat(2) { i -> rect(292f + i * 16f, 86f, 3f, 24f, near) }
                // rail line
                rect(0f, 124f, 360f, 2.5f, near)
                repeat(18) { i -> rect(6f + i * 20f, 127f, 8f, 2f, near.copy(alpha = 0.7f)) }
            }

            VistaStage.FOUNDED_CITY -> {
                // courthouse with dome and columns
                rect(140f, 72f, 80f, 38f, near)
                circle(180f, 70f, 14f, near)
                rect(176f, 50f, 3f, 8f, near)
                repeat(4) { i -> rect(148f + i * 18f, 82f, 5f, 28f, canvasCloth.copy(alpha = 0.85f)) }
                tower(58f, 30f, 40f, 2)
                tower(98f, 32f, 52f, 3)
                tower(236f, 32f, 48f, 2)
                statues(monuments)
            }

            VistaStage.OLD_CITY -> {
                tower(36f, 38f, 56f, 3)
                tower(84f, 42f, 70f, 4)
                tower(136f, 38f, 62f, 3)
                tower(184f, 44f, 76f, 4)
                // smokestacks
                rect(244f, 36f, 7f, 74f, near)
                circle(248f, 30f, 6f, smoke); circle(256f, 22f, 8f, smoke)
                rect(264f, 48f, 7f, 62f, near)
                // trolley on its line
                rect(0f, 124f, 360f, 2f, near)
                rect(296f, 112f, 30f, 12f, near)
                rect(300f, 115f, 6f, 5f, glow); rect(312f, 115f, 6f, 5f, glow)
                statues(monuments)
            }

            VistaStage.MODERN_CITY -> {
                tower(28f, 30f, 64f, 4)
                tower(66f, 36f, 88f, 5)
                tower(110f, 30f, 72f, 4)
                tower(148f, 40f, 100f, 6)
                tower(196f, 34f, 80f, 5)
                tower(238f, 30f, 60f, 4)
                statues(monuments)
            }

            VistaStage.FUTURE_CITY -> {
                tri(40f, 110f, 56f, 8f, 72f, 110f, near)
                tri(100f, 110f, 122f, 16f, 144f, 110f, near)
                tri(170f, 110f, 186f, 4f, 202f, 110f, near)
                tri(230f, 110f, 248f, 22f, 266f, 110f, near)
                repeat(5) { i -> rect(52f + i * 46f, 96f - i % 2 * 30f, 3f, 4f, glow) }
                // sky transit arc with a pod
                drawArc(
                    color = smoke,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(-40f * sx, 20f * sy),
                    size = Size(440f * sx, 200f * sy),
                    style = Stroke(width = 2.5f * sx),
                )
                rect(160f, 28f, 14f, 6f, near)
                statues(monuments)
            }
        }
    }
}

/** Field-row stripes need to read darker than grass regardless of season. */
private fun Color.compositeOverInk(): Color = Color(
    red = red * 0.55f,
    green = green * 0.55f,
    blue = blue * 0.5f,
    alpha = 1f,
)
