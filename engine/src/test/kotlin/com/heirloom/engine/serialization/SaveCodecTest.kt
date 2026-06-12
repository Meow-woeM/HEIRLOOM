package com.heirloom.engine.serialization

import com.heirloom.engine.T
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.LevelProgress
import com.heirloom.engine.model.SkillId
import com.heirloom.engine.model.VentureId
import com.heirloom.engine.sim.TickEngine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SaveCodecTest {

    private fun richState() = TickEngine.tick(T.newState(), 40.0, T.quiet).state.copy(
        heirlooms = setOf(HeirloomId.FATHERS_TOOLS, HeirloomId.MOTHERS_RECIPES),
        ventures = mapOf(VentureId.SAWMILL_SHARE to 3, VentureId.WAGON_TRAIN to 1),
        skillProgress = mapOf(SkillId.GRIT to LevelProgress(7, 2.5)),
        activityProgress = mapOf(ActivityId.FORAGE to LevelProgress(4, 1.25)),
        posterity = 123.0,
        monuments = 2,
        supporter = true,
    )

    @Test
    fun `a state round-trips exactly`() {
        val state = richState()
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test
    fun `encode stamps the current version`() {
        val text = SaveCodec.encode(richState().copy(saveVersion = -42))
        assertEquals(SaveCodec.CURRENT_VERSION, SaveCodec.decode(text).saveVersion)
    }

    @Test
    fun `garbage input throws and decodeOrNull swallows it`() {
        assertFailsWith<SaveCorruptedException> { SaveCodec.decode("not json at all{{{") }
        assertFailsWith<SaveCorruptedException> { SaveCodec.decode("""{"noVersion":true}""") }
        assertNull(SaveCodec.decodeOrNull("not json at all{{{"))
        assertNull(SaveCodec.decodeOrNull("""[1,2,3]"""))
    }

    @Test
    fun `saves from a newer build are refused, not mangled`() {
        val futuristic = SaveCodec.encode(richState())
            .replace("\"saveVersion\":${SaveCodec.CURRENT_VERSION}", "\"saveVersion\":${SaveCodec.CURRENT_VERSION + 1}")
        assertFailsWith<SaveCorruptedException> { SaveCodec.decode(futuristic) }
    }

    @Test
    fun `unknown fields are ignored for forward compatibility within a version`() {
        val text = SaveCodec.encode(richState())
        val withExtra = text.replaceFirst("{", """{"someFutureField":"hello",""")
        assertEquals(SaveCodec.decode(text), SaveCodec.decode(withExtra))
    }

    @Test
    fun `migrations walk old saves up to the current version`() {
        val state = richState()
        // Pretend this save was written by schema v0 whose field was named "oldPosterity".
        val v0Text = SaveCodec.encode(state)
            .replace("\"saveVersion\":${SaveCodec.CURRENT_VERSION}", "\"saveVersion\":0")
            .replace("\"posterity\":", "\"oldPosterity\":")
        val migrations = mapOf<Int, (JsonObject) -> JsonObject>(
            0 to { obj ->
                JsonObject(obj - "oldPosterity" + ("posterity" to (obj["oldPosterity"] ?: JsonPrimitive(0.0))))
            },
        )
        val decoded = SaveCodec.decodeWith(v0Text, migrations)
        assertEquals(state.posterity, decoded.posterity)
        assertEquals(SaveCodec.CURRENT_VERSION, decoded.saveVersion)
    }

    @Test
    fun `a missing migration step is corruption, not silent data loss`() {
        val v0Text = SaveCodec.encode(richState())
            .replace("\"saveVersion\":${SaveCodec.CURRENT_VERSION}", "\"saveVersion\":0")
        assertFailsWith<SaveCorruptedException> { SaveCodec.decodeWith(v0Text, emptyMap()) }
    }
}
