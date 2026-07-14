package com.luis.marlune.data.repository

import com.luis.marlune.data.database.FavoriteDao
import com.luis.marlune.data.database.FavoriteEntity
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Favoritos ("Me gusta"): guarda en Room SOLO referencias (`_ID` + timestamp) y las resuelve a
 * [Song] contra la biblioteca real (MediaStore). Un favorito cuya pista ya no existe se OMITE con
 * gracia. Sin red.
 */
class FavoritesRepository(
    private val dao: FavoriteDao,
    music: MusicRepository,
) {

    /** Conjunto reactivo de ids favoritas, para el estado del corazón de la pista actual. */
    val favoriteIds: Flow<Set<Long>> = dao.favoriteIds().map { it.toSet() }

    /** Canciones favoritas reales, más reciente primero (sin las ya borradas de MediaStore). */
    val favoriteSongs: Flow<List<Song>> =
        combine(dao.favorites(), music.library) { favorites, libraryState ->
            val byId = (libraryState as? LibraryState.Content)?.songs.orEmpty().associateBy { it.id }
            favorites.mapNotNull { byId[it.songId] }
        }

    /** Alterna el favorito de una pista, persistiéndolo. */
    suspend fun toggle(songId: Long) {
        if (dao.isFavorite(songId)) {
            dao.remove(songId)
        } else {
            dao.add(FavoriteEntity(songId = songId, likedAt = System.currentTimeMillis()))
        }
    }
}
