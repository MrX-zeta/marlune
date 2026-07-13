package com.luis.marlune.ui.home

import androidx.compose.runtime.Immutable
import com.luis.marlune.domain.model.Track

/** Franja del día para el saludo protagonista de la cabecera. */
enum class Greeting { MORNING, AFTERNOON, NIGHT }

/**
 * Pista en curso para la card "Continuar escuchando".
 * `progress` alimenta la marea (a amplitud reducida); `isPlaying` decide si ondula o calma.
 */
@Immutable
data class ContinueListeningUi(
    val track: Track,
    val progress: Float,
    val isPlaying: Boolean,
)

/**
 * Estado de la pantalla de Inicio.
 *
 * `recentTracks` ya viene deduplicado por el ViewModel (misma canción no se repite).
 * Todo procede de la biblioteca LOCAL; no hay catálogos remotos.
 */
@Immutable
data class HomeUiState(
    val greeting: Greeting,
    val continueListening: ContinueListeningUi?,
    val recentTracks: List<Track>,
) {
    companion object {
        private val sampleTracks = listOf(
            Track(id = 1L, title = "Bruma", artist = "Lún", durationMs = 198_000L),
            Track(id = 2L, title = "Costa dormida", artist = "Maréas", durationMs = 224_000L),
            Track(id = 3L, title = "Vidrio", artist = "Nocta", durationMs = 176_000L),
            Track(id = 4L, title = "Reflejo", artist = "Aiko", durationMs = 205_000L),
            Track(id = 5L, title = "Sal", artist = "Lún", durationMs = 189_000L),
        )

        /** Estado de ejemplo para previews. */
        val Preview = HomeUiState(
            greeting = Greeting.NIGHT,
            continueListening = ContinueListeningUi(
                track = sampleTracks.first(),
                progress = 0.38f,
                isPlaying = true,
            ),
            recentTracks = sampleTracks,
        )
    }
}

/** Accesos rápidos del grid. "Listas" sustituye a "Descargas" (biblioteca 100 % local). */
enum class LibraryShortcut { LIKED, PLAYLISTS, ALBUMS, ARTISTS }
