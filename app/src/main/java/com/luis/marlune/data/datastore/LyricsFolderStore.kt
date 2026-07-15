package com.luis.marlune.data.datastore

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lyricsFolderDataStore by preferencesDataStore(name = "lyrics_folder")

/**
 * Persiste el árbol SAF (carpeta de letras) que el usuario concede una vez con
 * `ACTION_OPEN_DOCUMENT_TREE`. Guardamos solo el tree URI; el permiso persistente se toma aparte con
 * `takePersistableUriPermission`. Sin red, sin permisos amplios.
 */
class LyricsFolderStore(context: Context) {

    private val dataStore = context.applicationContext.lyricsFolderDataStore

    val folderUri: Flow<Uri?> = dataStore.data.map { prefs ->
        prefs[Keys.FOLDER]?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    suspend fun setFolder(uri: Uri?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.FOLDER) else prefs[Keys.FOLDER] = uri.toString()
        }
    }

    private object Keys {
        val FOLDER = stringPreferencesKey("tree_uri")
    }
}
