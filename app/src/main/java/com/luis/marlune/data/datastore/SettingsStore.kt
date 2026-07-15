package com.luis.marlune.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Filtro de PRESENTACIÓN de clips cortos: oculta audios muy breves (tonos, notas de voz). No borra nada. */
data class ShortClipFilter(val enabled: Boolean, val minSeconds: Int) {
    val minDurationMs: Long get() = minSeconds * 1000L
}

/**
 * Ajustes de la app (DataStore): opt-in de letras por internet (**off por defecto**; con él en
 * `false` la app NO hace ninguna petición de red) y filtro de clips cortos de la biblioteca.
 */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    /** Buscar letras en internet (LRCLIB). Por defecto `false` (offline). */
    val internetLyrics: Flow<Boolean> = dataStore.data.map { it[Keys.INTERNET_LYRICS] ?: false }

    suspend fun setInternetLyrics(enabled: Boolean) {
        Log.d("MarluneLyrics", "ajuste internet_lyrics = $enabled")
        dataStore.edit { it[Keys.INTERNET_LYRICS] = enabled }
    }

    /** Filtro de clips cortos. Por defecto ACTIVADO con umbral de 30 s. */
    val shortClipFilter: Flow<ShortClipFilter> = dataStore.data.map {
        ShortClipFilter(
            enabled = it[Keys.SHORT_CLIP_ENABLED] ?: true,
            minSeconds = it[Keys.SHORT_CLIP_MIN_SECONDS] ?: 30,
        )
    }

    suspend fun setShortClipEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SHORT_CLIP_ENABLED] = enabled }
    }

    suspend fun setShortClipMinSeconds(seconds: Int) {
        dataStore.edit { it[Keys.SHORT_CLIP_MIN_SECONDS] = seconds }
    }

    private object Keys {
        val INTERNET_LYRICS = booleanPreferencesKey("internet_lyrics")
        val SHORT_CLIP_ENABLED = booleanPreferencesKey("short_clip_enabled")
        val SHORT_CLIP_MIN_SECONDS = intPreferencesKey("short_clip_min_seconds")
    }
}
