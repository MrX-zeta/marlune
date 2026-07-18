package com.luis.marlune.ui.player

import com.luis.marlune.domain.model.LyricLine
import com.luis.marlune.domain.model.LyricsFolderRequest

/**
 * Estado de la vista de letras del reproductor. La UI solo lo lee; la lectura/parseo vive en la capa
 * de datos ([com.luis.marlune.data.repository.LyricsRepository]).
 */
sealed interface LyricsUiState {
    /** Resolviendo la letra de la pista actual (IO, incluida la posible búsqueda en red). */
    data object Loading : LyricsUiState

    /**
     * La búsqueda en red falló. [offline] = no hay conexión; `false` = error del servicio. Mensaje
     * breve y distinto según el caso; nunca se confunde con "no encontrada" (que es [None]).
     */
    data class Error(val offline: Boolean) : LyricsUiState

    /**
     * No hay letra. Si [request] no es null, la carpeta de esta canción aún no tiene acceso y se
     * ofrece concederlo (con el nombre visible + URI inicial para preseleccionarla). Si es null, o
     * bien ya hay acceso (y simplemente no existe el `.lrc`) o no se pudo deducir la carpeta.
     */
    data class None(val request: LyricsFolderRequest?) : LyricsUiState

    /** Letra plana: texto scrolleable, sin resaltado ni auto-scroll. */
    data class Plain(val lines: List<String>) : LyricsUiState

    /** Letra sincronizada: [activeIndex] = línea activa según la posición (-1 = aún no empezó). */
    data class Synced(val lines: List<LyricLine>, val activeIndex: Int) : LyricsUiState
}

/** Un candidato para la hoja "Cambiar letra": lo justo para mostrarlo y marcar el activo. */
data class LyricsCandidateUi(
    val id: Long,
    val artist: String,
    val album: String,
    val durationSec: Long,
    val synced: Boolean,
)

/**
 * Estado de la hoja de selección de versión de letra. `null` (fuera de este sealed) = hoja cerrada; lo
 * gestiona el ViewModel. Reutiliza la distinción de errores de red del resto de letras.
 */
sealed interface LyricsPickerState {
    data object Loading : LyricsPickerState

    /** Lista lista para elegir; [activeId] = elección manual activa (o `null`). Vacía = "no hay otras". */
    data class Ready(val candidates: List<LyricsCandidateUi>, val activeId: Long?) : LyricsPickerState

    /** Fallo de red al listar: [offline] = sin conexión; `false` = error del servicio. */
    data class Error(val offline: Boolean) : LyricsPickerState
}
