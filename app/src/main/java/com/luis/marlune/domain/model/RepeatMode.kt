package com.luis.marlune.domain.model

/** Modo de repetición de la cola de reproducción. */
enum class RepeatMode {
    OFF,
    ALL,
    ONE;

    /** Siguiente modo en el ciclo OFF → ALL → ONE → OFF. */
    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}
