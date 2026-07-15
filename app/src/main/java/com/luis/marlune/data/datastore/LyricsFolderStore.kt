package com.luis.marlune.data.datastore

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lyricsFolderDataStore by preferencesDataStore(name = "lyrics_folder")

/**
 * Persiste el CONJUNTO de árboles SAF (carpetas de letras) que el usuario concede con
 * `ACTION_OPEN_DOCUMENT_TREE`. Multi-carpeta: el usuario puede tener su música repartida en varias
 * carpetas; cada una se concede una vez (el permiso persistente se toma aparte). Sin permisos amplios.
 */
class LyricsFolderStore(context: Context) {

    private val dataStore = context.applicationContext.lyricsFolderDataStore

    val folders: Flow<Set<Uri>> = dataStore.data.map { prefs ->
        prefs[Keys.FOLDERS].orEmpty().mapNotNull { it.takeIf(String::isNotBlank)?.let(Uri::parse) }.toSet()
    }

    suspend fun add(uri: Uri) {
        dataStore.edit { prefs ->
            prefs[Keys.FOLDERS] = prefs[Keys.FOLDERS].orEmpty() + uri.toString()
        }
    }

    suspend fun remove(uri: Uri) {
        dataStore.edit { prefs ->
            prefs[Keys.FOLDERS] = prefs[Keys.FOLDERS].orEmpty() - uri.toString()
        }
    }

    private object Keys {
        val FOLDERS = stringSetPreferencesKey("tree_uris")
    }
}
