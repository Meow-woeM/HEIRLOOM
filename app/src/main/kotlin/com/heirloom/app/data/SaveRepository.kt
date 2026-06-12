package com.heirloom.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.heirloom.engine.model.GameState
import com.heirloom.engine.serialization.SaveCodec
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GameState as versioned JSON in Preferences DataStore. Every write first rolls the
 * current save into a backup slot, so a torn/corrupt primary always has a fallback —
 * loading tries primary, then backup.
 */
@Singleton
class SaveRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keySave = stringPreferencesKey("save_json")
    private val keyBackup = stringPreferencesKey("save_json_backup")

    suspend fun load(): GameState? {
        val prefs = dataStore.data.first()
        return prefs[keySave]?.let(SaveCodec::decodeOrNull)
            ?: prefs[keyBackup]?.let(SaveCodec::decodeOrNull)
    }

    suspend fun save(state: GameState) {
        val text = SaveCodec.encode(state)
        dataStore.edit { prefs ->
            prefs[keySave]?.let { prefs[keyBackup] = it }
            prefs[keySave] = text
        }
    }

    /** Settings: export the raw save for the player to keep. */
    suspend fun exportJson(): String? = dataStore.data.first()[keySave]

    /** Settings: import a save; refuses anything the codec can't decode. */
    suspend fun importJson(text: String): GameState? {
        val state = SaveCodec.decodeOrNull(text) ?: return null
        save(state)
        return state
    }

    suspend fun wipe() {
        dataStore.edit { prefs ->
            prefs.remove(keySave)
            prefs.remove(keyBackup)
        }
    }
}
