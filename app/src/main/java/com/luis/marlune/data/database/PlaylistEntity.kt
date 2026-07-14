package com.luis.marlune.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una lista de reproducción del usuario (solo nombre; sin carátula custom por ahora). */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * Relación muchos-a-muchos lista↔canción, referenciando la pista por su `_ID` de MediaStore (no
 * duplica metadatos) y con un campo de ORDEN. Se prepara ya; las canciones se añaden en el siguiente
 * sub-paso. Borrar la lista arrastra sus relaciones (`ON DELETE CASCADE`).
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlist_id", "song_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlist_id")],
)
data class PlaylistSongEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "song_id") val songId: Long,
    val position: Int,
)
