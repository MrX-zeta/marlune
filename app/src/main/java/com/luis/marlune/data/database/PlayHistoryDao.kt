package com.luis.marlune.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Acceso al historial de reproducción. */
@Dao
interface PlayHistoryDao {

    /** Inserta o actualiza (por `songId`) la entrada con su último timestamp. Dedupe por pista. */
    @Upsert
    suspend fun upsert(entry: PlayHistoryEntity)

    /** Historial más reciente primero, ya deduplicado por pista (una fila por canción). */
    @Query("SELECT * FROM play_history ORDER BY last_played_at DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<PlayHistoryEntity>>
}
