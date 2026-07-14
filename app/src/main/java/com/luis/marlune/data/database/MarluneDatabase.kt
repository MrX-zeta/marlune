package com.luis.marlune.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base de datos local de Marlune (Room). Guarda SOLO lo propio de la app (por ahora, el historial);
 * las canciones NO se duplican: se referencian por `_ID` de MediaStore. Sin red.
 */
@Database(entities = [PlayHistoryEntity::class], version = 1, exportSchema = false)
abstract class MarluneDatabase : RoomDatabase() {
    abstract fun playHistoryDao(): PlayHistoryDao
}
