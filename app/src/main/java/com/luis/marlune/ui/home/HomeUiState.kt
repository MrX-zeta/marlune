package com.luis.marlune.ui.home

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.luis.marlune.domain.model.Song

/** Franja del día para el saludo protagonista de la cabecera. */
enum class Greeting { MORNING, AFTERNOON, NIGHT }

/** Info mínima para la card "Continuar" (sesión guardada): carátula + título de la última pista. */
@Immutable
data class ContinueInfo(val title: String, val artworkUri: Uri?)

/**
 * Estado de la pantalla de Inicio.
 *
 * `recent` es "Escuchado hace poco" desde el historial real (Room), más reciente primero y sin
 * duplicados. `isLoading` cubre el arranque (shimmer); `libraryEmpty`, un dispositivo sin música
 * (distinto de historial vacío). `continueSession` es null si no hay sesión guardada (la card se
 * oculta).
 */
@Immutable
data class HomeUiState(
    val greeting: Greeting,
    val recent: List<Song>,
    val isLoading: Boolean,
    val libraryEmpty: Boolean,
    val likedCount: Int,
    val continueSession: ContinueInfo?,
) {
    companion object {
        /** Estado de ejemplo para previews (no se usa en ejecución). */
        val Preview = HomeUiState(
            greeting = Greeting.NIGHT,
            recent = List(5) { i ->
                sampleSong(i.toLong() + 1, listOf("Bruma", "Costa dormida", "Vidrio", "Reflejo", "Sal")[i], "Lún")
            },
            isLoading = false,
            libraryEmpty = false,
            likedCount = 3,
            continueSession = ContinueInfo("Marea nocturna", null),
        )

        private fun sampleSong(id: Long, title: String, artist: String) = Song(
            id = id,
            title = title,
            artist = artist,
            artistId = id,
            album = title,
            albumId = id,
            durationMs = 198_000L,
            trackNumber = 1,
            year = 2024,
            genre = null,
            dateAdded = id,
            displayName = "$title.mp3",
            contentUri = Uri.EMPTY,
            artworkUri = null,
        )
    }
}
