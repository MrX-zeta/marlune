package com.luis.marlune.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryPrefsDataStore by preferencesDataStore(name = "library_prefs")

/**
 * Preferencias de la Biblioteca (DataStore). Guarda el criterio de orden de Canciones como una clave
 * en crudo (String); el mapeo a enum vive en la capa de UI, para no acoplar datos a la presentación.
 */
class LibrarySortStore(context: Context) {

    private val dataStore = context.applicationContext.libraryPrefsDataStore

    /** Clave del orden persistido (o `null` si no se ha elegido ninguno todavía). */
    val sortKey: Flow<String?> = dataStore.data.map { it[Keys.SORT] }

    suspend fun setSortKey(key: String) {
        dataStore.edit { it[Keys.SORT] = key }
    }

    private object Keys {
        val SORT = stringPreferencesKey("songs_sort")
    }
}
