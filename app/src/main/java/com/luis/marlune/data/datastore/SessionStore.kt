package com.luis.marlune.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "playback_session")

/** Sesión persistida en crudo: ids de la cola (en orden), índice actual y posición (ms). */
data class StoredSession(
    val ids: List<Long>,
    val index: Int,
    val positionMs: Long,
)

/**
 * Persistencia de la sesión de reproducción con DataStore (clave-valor; sin duplicar metadatos: la
 * cola se guarda como los `_ID` de MediaStore). Simple y sin migraciones. Sin red.
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
            )
        }
    }

    suspend fun save(ids: List<Long>, index: Int, positionMs: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.QUEUE] = ids.joinToString(",")
            prefs[Keys.INDEX] = index
            prefs[Keys.POSITION] = positionMs
        }
    }

    private object Keys {
        val QUEUE = stringPreferencesKey("queue_ids")
        val INDEX = intPreferencesKey("index")
        val POSITION = longPreferencesKey("position_ms")
    }
}
