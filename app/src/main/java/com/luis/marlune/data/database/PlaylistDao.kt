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
}
