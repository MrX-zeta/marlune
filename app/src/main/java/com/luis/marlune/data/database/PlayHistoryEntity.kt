package com.luis.marlune.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro de historial de reproducción. Referencia la pista por su `_ID` de MediaStore (no
 * duplica metadatos de la canción). Una fila por canción (la clave primaria es [songId]), así que
 * el dedupe es inherente: reproducir la misma pista actualiza su [lastPlayedAt], no crea otra fila.
 */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val songId: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
)
