package com.luis.marlune.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.luis.marlune.domain.model.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "playback_session")

/**
 * Sesión persistida en crudo: ids de la cola (en orden), índice actual, posición (ms), los modos
 * de reproducción (shuffle/repeat) y los METADATOS de la pista actual (título/artista/carátula) —
 * estos últimos para que el widget pueda pintar la última pista SIN despertar la biblioteca
 * (proceso recién nacido tras un reinicio). La restauración real sigue resolviendo por ids.
 */
data class StoredSession(
    val ids: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val title: String = "",
    val artist: String = "",
    val artworkUri: String? = null,
)

/**
 * Persistencia de la sesión de reproducción con DataStore (clave-valor; sin duplicar metadatos: la
 * cola se guarda como los `_ID` de MediaStore). Simple y sin migraciones. Sin red.
 *
 * El delegado `preferencesDataStore` es singleton por proceso: varias instancias de [SessionStore]
 * (UI y servicio) comparten el mismo DataStore sin conflicto.
 */
class SessionStore(context: Context) {

    private val dataStore = context.applicationContext.sessionDataStore

    val session: Flow<StoredSession?> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.QUEUE].orEmpty()
        val ids = raw.split(",").mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) {
            null
        } else {
            StoredSession(
                ids = ids,
                index = prefs[Keys.INDEX] ?: 0,
                positionMs = prefs[Keys.POSITION] ?: 0L,
                shuffle = prefs[Keys.SHUFFLE] ?: false,
                repeatMode = RepeatMode.entries.firstOrNull { it.name == prefs[Keys.REPEAT] }
                    ?: RepeatMode.OFF,
                title = prefs[Keys.TITLE].orEmpty(),
                artist = prefs[Keys.ARTIST].orEmpty(),
                artworkUri = prefs[Keys.ARTWORK],
            )
        }
    }

    /** Snapshot completo de la sesión (cola + índice + posición + modos + metadatos de la actual). */
    suspend fun save(
        ids: List<Long>,
        index: Int,
        positionMs: Long,
        shuffle: Boolean,
        repeatMode: RepeatMode,
        title: String,
        artist: String,
        artworkUri: String?,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.QUEUE] = ids.joinToString(",")
            prefs[Keys.INDEX] = index
            prefs[Keys.POSITION] = positionMs
            prefs[Keys.SHUFFLE] = shuffle
            prefs[Keys.REPEAT] = repeatMode.name
            prefs[Keys.TITLE] = title
            prefs[Keys.ARTIST] = artist
            if (artworkUri != null) prefs[Keys.ARTWORK] = artworkUri else prefs.remove(Keys.ARTWORK)
        }
    }

    /** Borra la sesión guardada (cola vacía real: se quitó todo / stop definitivo). */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val QUEUE = stringPreferencesKey("queue_ids")
        val INDEX = intPreferencesKey("index")
        val POSITION = longPreferencesKey("position_ms")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT = stringPreferencesKey("repeat_mode")
        val TITLE = stringPreferencesKey("title")
        val ARTIST = stringPreferencesKey("artist")
        val ARTWORK = stringPreferencesKey("artwork_uri")
    }
}
