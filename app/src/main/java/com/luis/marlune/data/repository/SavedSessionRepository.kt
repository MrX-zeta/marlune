package com.luis.marlune.data.repository

import com.luis.marlune.data.datastore.SessionStore
import com.luis.marlune.data.datastore.StoredSession
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Sesión guardada resuelta a canciones reales: la cola persistida (`_ID`) se cruza con la biblioteca
 * (MediaStore), OMITIENDO las pistas que ya no existen y reajustando el índice a la pista que estaba
 * sonando. Alimenta la card "Continuar" de Inicio; NO se carga en el player al arrancar.
 */
data class SavedSession(
    val songs: List<Song>,
    val index: Int,
    val positionMs: Long,
) {
    val current: Song? get() = songs.getOrNull(index)
}

class SavedSessionRepository(
    private val store: SessionStore,
    music: MusicRepository,
) {

    val savedSession: Flow<SavedSession?> =
        combine(store.session, music.library) { stored, libraryState ->
            resolve(stored, (libraryState as? LibraryState.Content)?.songs.orEmpty())
        }

    /** Persiste la cola actual (ids), el índice y la posición. */
    suspend fun save(ids: List<Long>, index: Int, positionMs: Long) {
        store.save(ids, index, positionMs)
    }

    private fun resolve(stored: StoredSession?, library: List<Song>): SavedSession? {
        if (stored == null || library.isEmpty()) return null
        val byId = library.associateBy { it.id }

        // Id de la pista que estaba sonando, para reubicar el índice tras omitir las borradas.
        val currentId = stored.ids.getOrNull(stored.index)
        val songs = stored.ids.mapNotNull { byId[it] }
        if (songs.isEmpty()) return null

        val newIndex = songs.indexOfFirst { it.id == currentId }
        return if (newIndex >= 0) {
            // La pista actual sigue existiendo: conserva su posición.
            SavedSession(songs, newIndex, stored.positionMs)
        } else {
            // La pista actual fue borrada: empieza al inicio de la cola restante.
            SavedSession(songs, index = 0, positionMs = 0L)
        }
    }
}
