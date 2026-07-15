package com.luis.marlune.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Lista con su conteo de canciones (por ahora 0; las canciones llegan en el siguiente sub-paso). */
data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "song_count") val songCount: Int,
)

/** Operaciones sobre las listas (gestión de las listas en sí). */
@Dao
interface PlaylistDao {

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** Borra la lista (sus relaciones caen por CASCADE). No toca canciones ni archivos. */
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "SELECT p.*, COUNT(ps.song_id) AS song_count FROM playlists p " +
            "LEFT JOIN playlist_songs ps ON ps.playlist_id = p.id " +
            "GROUP BY p.id ORDER BY p.created_at DESC",
    )
    fun playlistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    // --- Canciones dentro de una lista (relación con orden) ---

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlist_id = :playlistId AND song_id = :songId)")
    suspend fun contains(playlistId: Long, songId: Long): Boolean

    /** Siguiente valor de orden (al final de la lista). */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert
    suspend fun insertSong(entry: PlaylistSongEntity)

    /** Quita la relación (no borra la canción ni el archivo). */
    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    /** Quita la canción de TODAS las listas (al borrar su archivo del dispositivo). */
    @Query("DELETE FROM playlist_songs WHERE song_id = :songId")
    suspend fun removeSongEverywhere(songId: Long)

    /** Ids de las canciones de la lista, en el ORDEN en que se añadieron. */
    @Query("SELECT song_id FROM playlist_songs WHERE playlist_id = :playlistId ORDER BY position ASC")
    fun songIds(playlistId: Long): Flow<List<Long>>
}
