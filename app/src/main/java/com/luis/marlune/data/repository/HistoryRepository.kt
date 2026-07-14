package com.luis.marlune.data.repository

import com.luis.marlune.data.database.PlayHistoryDao
import com.luis.marlune.data.database.PlayHistoryEntity
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Historial de reproducción: guarda en Room SOLO referencias (`_ID` + timestamp) y las resuelve a
 * [Song] contra la biblioteca real (MediaStore). Una fila por canción (dedupe por clave primaria);
 * más reciente arriba. Una entrada cuya pista ya no existe en MediaStore (borrada) se OMITE con
 * gracia. Sin red.
 */
class HistoryRepository(
    private val dao: PlayHistoryDao,
    music: MusicRepository,
) {

    /** "Escuchado hace poco": pistas reales, más reciente primero, sin duplicados. */
    val recentlyPlayed: Flow<List<Song>> =
        combine(dao.recent(HISTORY_LIMIT), music.library) { history, libraryState ->
            val byId = (libraryState as? LibraryState.Content)?.songs.orEmpty().associateBy { it.id }
            history.mapNotNull { byId[it.songId] } // pistas ya borradas de MediaStore se omiten
        }

    /** Registra (o actualiza) la reproducción de una pista con la marca de tiempo actual. */
    suspend fun recordPlay(songId: Long) {
        dao.upsert(PlayHistoryEntity(songId = songId, lastPlayedAt = System.currentTimeMillis()))
    }

    private companion object {
        const val HISTORY_LIMIT = 50
    }
}
