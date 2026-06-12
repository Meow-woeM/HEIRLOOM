package com.heirloom.app.ui.vista

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.heirloom.engine.model.VistaStage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The vista asset swap pipeline: licensed pixel-art scenes can be dropped into
 * `assets/vista/` (one PNG per stage, listed in `manifest.json`) without touching code.
 * Missing manifest entries fall back to the programmatic placeholder painter.
 * See ATTRIBUTION.md for sourcing/licensing rules (CC0 preferred, never NC/ND).
 */
object VistaAssets {
    private const val DIR = "vista"
    private const val MANIFEST_PATH = "$DIR/manifest.json"

    @Volatile
    private var manifest: Map<VistaStage, String>? = null
    private val bitmapCache = mutableMapOf<VistaStage, ImageBitmap?>()

    fun stageBitmap(context: Context, stage: VistaStage): ImageBitmap? {
        synchronized(bitmapCache) {
            bitmapCache[stage]?.let { return it }
        }
        val file = loadManifest(context)[stage] ?: return null
        val bitmap = try {
            context.assets.open("$DIR/$file").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
        synchronized(bitmapCache) { bitmapCache[stage] = bitmap }
        return bitmap
    }

    private fun loadManifest(context: Context): Map<VistaStage, String> {
        manifest?.let { return it }
        val parsed = try {
            val text = context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
            val stages = Json.parseToJsonElement(text).jsonObject["stages"]?.jsonObject
            VistaStage.entries.mapNotNull { stage ->
                stages?.get(stage.name)?.jsonPrimitive?.contentOrNull?.let { stage to it }
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
        manifest = parsed
        return parsed
    }
}
