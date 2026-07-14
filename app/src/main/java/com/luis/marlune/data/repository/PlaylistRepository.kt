package com.luis.marlune.data.repository

import com.luis.marlune.data.database.PlaylistDao
import com.luis.marlune.data.database.PlaylistEntity
import com.luis.marlune.data.database.PlaylistSongEntity
import com.luis.marlune.domain.model.Playlist
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Gestión de las listas de reproducción (Room): las listas en sí y sus canciones (relación con
 * orden). Las canciones se referencian por `_ID` de MediaStore y se resuelven a [Song] contra la
 * biblioteca real (omitiendo las que ya no existan). Sin red.
 */
class PlaylistRepository(
    private val dao: PlaylistDao,
    private val music: MusicRepository,
) {

    /** Listas del usuario con su conteo real (reactivo al añadir/quitar canciones). */
    val playlists: Flow<List<Playlist>> =
        dao.playlistsWithCount().map { rows ->
            rows.map { Playlist(id = it.playlist.id, name = it.playlist.name, songCount = it.songCount) }
        }

    fun playlistName(id: Long): Flow<String?> = dao.playlist(id).map { it?.name }

    /** Canciones de una lista en el ORDEN en que se añadieron (sin las ya borradas de MediaStore). */
    fun playlistSongs(playlistId: Long): Flow<List<Song>> =
        combine(dao.songIds(playlistId), music.library) { ids, libraryState ->
            val byId = (libraryState as? LibraryState.Content)?.songs.orEmpty().associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }

    suspend fun create(name: String): Long =
        dao.insert(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    suspend fun delete(id: Long) = dao.delete(id)

    /** Añade una canción al final de la lista. Devuelve false si ya estaba (no la duplica). */
    suspend fun addSong(playlistId: Long, songId: Long): Boolean {
        if (dao.contains(playlistId, songId)) return false
        dao.insertSong(PlaylistSongEntity(playlistId = playlistId, songId = songId, position = dao.nextPosition(playlistId)))
        return true
    }

    suspend fun removeSong(playlistId: Long, songId: Long) = dao.removeSong(playlistId, songId)

    /** Crea una lista y le añade la canción en un solo paso; devuelve el id de la lista nueva. */
    suspend fun createAndAdd(name: String, songId: Long): Long {
        val id = create(name)
        addSong(id, songId)
        return id
    }
}
