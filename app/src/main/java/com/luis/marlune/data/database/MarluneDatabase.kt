package com.luis.marlune.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local de Marlune (Room). Guarda SOLO lo propio de la app (historial, favoritos);
 * las canciones NO se duplican: se referencian por `_ID` de MediaStore. Sin red.
 */
@Database(
    entities = [PlayHistoryEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MarluneDatabase : RoomDatabase() {
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        /** v1 → v2: añade la tabla de favoritos, conservando el historial existente. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` " +
                        "(`songId` INTEGER NOT NULL, `liked_at` INTEGER NOT NULL, PRIMARY KEY(`songId`))",
                )
            }
        }
    }
}
