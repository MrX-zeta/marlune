package com.luis.marlune.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Canción marcada como favorita ("Me gusta"). Referencia la pista por su `_ID` de MediaStore (no
 * duplica metadatos); una fila por canción. [likedAt] permite ordenar por cuándo se marcó.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    @ColumnInfo(name = "liked_at") val likedAt: Long,
)
