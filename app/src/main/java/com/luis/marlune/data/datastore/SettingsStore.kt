package com.luis.marlune.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Ajustes de la app (DataStore). Por ahora solo el opt-in de letras por internet, **desactivado por
 * defecto**: con él en `false`, la app NO hace ninguna petición de red (sigue siendo 100% local).
 */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    /** Buscar letras en internet (LRCLIB). Por defecto `false` (offline). */
    val internetLyrics: Flow<Boolean> = dataStore.data.map { it[Keys.INTERNET_LYRICS] ?: false }

    suspend fun setInternetLyrics(enabled: Boolean) {
        Log.d("MarluneLyrics", "ajuste internet_lyrics = $enabled")
        dataStore.edit { it[Keys.INTERNET_LYRICS] = enabled }
    }

    private object Keys {
        val INTERNET_LYRICS = booleanPreferencesKey("internet_lyrics")
    }
}
