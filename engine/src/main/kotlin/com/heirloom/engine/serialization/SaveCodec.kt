package com.heirloom.engine.serialization

import com.heirloom.engine.model.GameState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SaveCorruptedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Versioned JSON save schema. Decoding parses to a JsonObject first, walks the
 * migration map from the stored version up to [CURRENT_VERSION], then deserializes.
 * The app layer keeps a backup of the last good save as the corruption fallback.
 */
object SaveCodec {
    const val CURRENT_VERSION = 1

    /**
     * Migration from version N to N+1, keyed by N. When the schema changes:
     * bump CURRENT_VERSION and register a transform here. Never delete old entries.
     */
    private val MIGRATIONS: Map<Int, (JsonObject) -> JsonObject> = mapOf(
        // Example shape (none needed yet — schema is at v1):
        // 1 to { obj -> JsonObject(obj + ("newField" to JsonPrimitive(0))) },
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: GameState): String =
        json.encodeToString(GameState.serializer(), state.copy(saveVersion = CURRENT_VERSION))

    fun decode(text: String): GameState = decodeWith(text, MIGRATIONS)

    fun decodeOrNull(text: String): GameState? = try {
        decode(text)
    } catch (_: Exception) {
        null
    }

    /** Internal seam so tests can exercise the migration walk with synthetic migrations. */
    internal fun decodeWith(text: String, migrations: Map<Int, (JsonObject) -> JsonObject>): GameState {
        val parsed = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw SaveCorruptedException("Save is not valid JSON", e)
        }
        var obj = parsed
        var version = obj["saveVersion"]?.jsonPrimitive?.intOrNull
            ?: throw SaveCorruptedException("Save has no version stamp")
        if (version > CURRENT_VERSION) {
            throw SaveCorruptedException("Save version $version is newer than this build ($CURRENT_VERSION)")
        }
        while (version < CURRENT_VERSION) {
            val migration = migrations[version]
                ?: throw SaveCorruptedException("No migration path from version $version")
            obj = migration(obj)
            version++
            obj = JsonObject(obj + ("saveVersion" to JsonPrimitive(version)))
        }
        return try {
            json.decodeFromJsonElement(GameState.serializer(), obj)
        } catch (e: Exception) {
            throw SaveCorruptedException("Save failed to deserialize", e)
        }
    }
}
