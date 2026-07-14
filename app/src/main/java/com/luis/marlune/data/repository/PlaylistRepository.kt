package com.luis.marlune.data.repository

import com.luis.marlune.data.database.PlaylistDao
import com.luis.marlune.data.database.PlaylistEntity
import com.luis.marlune.domain.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Gestión de las listas de reproducción (Room). Solo las listas en sí (crear/listar/renombrar/
 * borrar); añadir/quitar canciones llega en el siguiente sub-paso. Sin red.
 */
class PlaylistRepository(private val dao: PlaylistDao) {

    /** Listas del usuario con su conteo (más reciente primero). */
    val playlists: Flow<List<Playlist>> =
        dao.playlistsWithCount().map { rows ->
            rows.map { Playlist(id = it.playlist.id, name = it.playlist.name, songCount = it.songCount) }
        }

    /** Nombre de una lista (para el título de su detalle). */
    fun playlistName(id: Long): Flow<String?> = dao.playlist(id).map { it?.name }

    suspend fun create(name: String): Long =
        dao.insert(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    suspend fun delete(id: Long) = dao.delete(id)
}
