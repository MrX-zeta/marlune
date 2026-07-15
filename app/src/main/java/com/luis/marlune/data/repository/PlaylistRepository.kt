package com.luis.marlune.data.repository

import com.luis.marlune.data.database.PlaylistDao
import com.luis.marlune.data.database.PlaylistEntity
import com.luis.marlune.data.database.PlaylistSongEntity
import com.luis.marlune.domain.model.Playlist
import com.luis.marlune.domain.model.PlaylistCover
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

    /**
     * Listas del usuario con su conteo real y las carátulas (hasta 4) de sus primeras canciones para
     * el mosaico de portada. Reactivo a añadir/quitar/reordenar canciones y a la biblioteca (las
     * canciones ya borradas de MediaStore se omiten del mosaico, igual que en la lista de la lista).
     */
    val playlists: Flow<List<Playlist>> =
        combine(dao.playlistsWithCount(), dao.allSongRefs(), music.library) { rows, refs, libraryState ->
            val byId = (libraryState as? LibraryState.Content)?.songs.orEmpty().associateBy { it.id }
            val refsByPlaylist = refs.groupBy { it.playlistId } // ya ordenadas por posición
            rows.map { row ->
                val covers = refsByPlaylist[row.playlist.id].orEmpty()
                    .mapNotNull { byId[it.songId] } // solo canciones existentes, en orden
                    .take(4)
                    .map { PlaylistCover(it.id, it.artworkUri) }
                Playlist(id = row.playlist.id, name = row.playlist.name, songCount = row.songCount, covers = covers)
            }
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

    /** Persiste el nuevo orden de las canciones de la lista (en bloque). No toca la cola en curso. */
    suspend fun reorderSongs(playlistId: Long, orderedSongIds: List<Long>) =
        dao.reorderSongs(playlistId, orderedSongIds)

    /** Quita la canción de todas las listas (limpieza silenciosa al borrar su archivo). */
    suspend fun removeSongEverywhere(songId: Long) = dao.removeSongEverywhere(songId)

    /** Crea una lista y le añade la canción en un solo paso; devuelve el id de la lista nueva. */
    suspend fun createAndAdd(name: String, songId: Long): Long {
        val id = create(name)
        addSong(id, songId)
        return id
    }
}
