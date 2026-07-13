package com.luis.marlune.ui.home

import androidx.compose.runtime.Immutable
import com.luis.marlune.domain.model.Track

/** Franja del día para el saludo protagonista de la cabecera. */
enum class Greeting { MORNING, AFTERNOON, NIGHT }

/**
 * Estado de la pantalla de Inicio.
 *
 * `recentTracks` ya viene deduplicado por el ViewModel (misma canción no se repite).
 * Todo procede de la biblioteca LOCAL; no hay catálogos remotos. La reanudación de la última
 * pista la cubre el mini-player persistente, por lo que no hay card "Continuar escuchando".
 */
@Immutable
data class HomeUiState(
    val greeting: Greeting,
    val recentTracks: List<Track>,
) {
    companion object {
        /** Estado de ejemplo para previews. */
        val Preview = HomeUiState(
            greeting = Greeting.NIGHT,
            recentTracks = listOf(
                Track(id = 1L, title = "Bruma", artist = "Lún", durationMs = 198_000L),
                Track(id = 2L, title = "Costa dormida", artist = "Maréas", durationMs = 224_000L),
                Track(id = 3L, title = "Vidrio", artist = "Nocta", durationMs = 176_000L),
                Track(id = 4L, title = "Reflejo", artist = "Aiko", durationMs = 205_000L),
                Track(id = 5L, title = "Sal", artist = "Lún", durationMs = 189_000L),
            ),
        )
    }
}

/** Accesos rápidos del grid. "Listas" sustituye a "Descargas" (biblioteca 100 % local). */
enum class LibraryShortcut { LIKED, PLAYLISTS, ALBUMS, ARTISTS }
