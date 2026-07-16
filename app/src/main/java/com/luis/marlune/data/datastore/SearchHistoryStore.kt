package com.luis.marlune.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

/**
 * Búsquedas recientes persistidas con DataStore (clave-valor). Se guardan como una sola cadena con
 * los términos separados por salto de línea (el campo es de un solo renglón, así que un término
 * nunca contiene `\n`): orden estable, del más reciente al más antiguo, sin duplicados. Simple y
 * sin migraciones. Sin red.
 */
class SearchHistoryStore(context: Context) {

    private val dataStore = context.applicationContext.searchHistoryDataStore

    /** Términos recientes, del más reciente al más antiguo (máx. [MAX_TERMS]). */
    val terms: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[Keys.TERMS].orEmpty().split("\n").filter { it.isNotBlank() }
    }

    /** Añade un término al frente: si ya existía (sin distinguir mayúsculas) sube, no se duplica. */
    suspend fun add(term: String) {
        val clean = term.trim()
        if (clean.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.TERMS].orEmpty().split("\n").filter { it.isNotBlank() }
            val deduped = current.filterNot { it.equals(clean, ignoreCase = true) }
            prefs[Keys.TERMS] = (listOf(clean) + deduped).take(MAX_TERMS).joinToString("\n")
        }
    }

    /** Quita un término concreto de la lista. */
    suspend fun remove(term: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.TERMS].orEmpty().split("\n").filter { it.isNotBlank() }
            prefs[Keys.TERMS] = current.filterNot { it.equals(term, ignoreCase = true) }.joinToString("\n")
        }
    }

    /** Vacía todas las búsquedas recientes. */
    suspend fun clear() {
        dataStore.edit { it.remove(Keys.TERMS) }
    }

    private object Keys {
        val TERMS = stringPreferencesKey("recent_terms")
    }

    companion object {
        /** Tope de términos guardados (los más antiguos se descartan). */
        const val MAX_TERMS = 8
    }
}
