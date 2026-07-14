package com.luis.marlune.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local de Marlune (Room). Guarda SOLO lo propio de la app (historial, favoritos,
 * listas); las canciones NO se duplican: se referencian por `_ID` de MediaStore. Sin red.
 */
@Database(
    entities = [
        PlayHistoryEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class MarluneDatabase : RoomDatabase() {
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao

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

        /** v2 → v3: añade listas y la relación lista↔canción (con orden), sin tocar el resto. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_songs` " +
                        "(`playlist_id` INTEGER NOT NULL, `song_id` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, PRIMARY KEY(`playlist_id`, `song_id`), " +
                        "FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlist_id` " +
                        "ON `playlist_songs` (`playlist_id`)",
                )
            }
        }
    }
}
