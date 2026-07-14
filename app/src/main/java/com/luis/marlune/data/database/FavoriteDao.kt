package com.luis.marlune.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Acceso a favoritos ("Me gusta"). */
@Dao
interface FavoriteDao {

    @Upsert
    suspend fun add(entry: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun remove(songId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    /** Ids de todas las favoritas, reactivo (para el estado del corazón por pista). */
    @Query("SELECT songId FROM favorites")
    fun favoriteIds(): Flow<List<Long>>

    /** Favoritas más recientes primero (para resolver a canciones y contar). */
    @Query("SELECT * FROM favorites ORDER BY liked_at DESC")
    fun favorites(): Flow<List<FavoriteEntity>>
}
