package com.luis.marlune.ui.home

import androidx.lifecycle.ViewModel
import com.luis.marlune.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

/**
 * ViewModel de Inicio.
 *
 * De momento sirve datos de ejemplo de la biblioteca LOCAL; la fuente real (MediaStore /
 * historial de reproducción) llegará en la capa `data/`. Se encarga de deduplicar el historial
 * reciente antes de exponerlo a la UI.
 */
class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private fun buildInitialState(): HomeUiState {
        // El historial de origen puede traer repetidos (misma pista escuchada varias veces).
        val rawRecent = listOf(
            Track(id = 1L, title = "Bruma", artist = "Lún", durationMs = 198_000L),
            Track(id = 2L, title = "Costa dormida", artist = "Maréas", durationMs = 224_000L),
            Track(id = 1L, title = "Bruma", artist = "Lún", durationMs = 198_000L), // duplicado
            Track(id = 3L, title = "Vidrio", artist = "Nocta", durationMs = 176_000L),
            Track(id = 4L, title = "Reflejo", artist = "Aiko", durationMs = 205_000L),
            Track(id = 5L, title = "Sal", artist = "Lún", durationMs = 189_000L),
        )

        val recent = rawRecent.dedupById()

        return HomeUiState(
            greeting = greetingForHour(LocalTime.now().hour),
            continueListening = ContinueListeningUi(
                track = recent.first(),
                progress = 0.38f,
                isPlaying = false,
            ),
            recentTracks = recent,
        )
    }

    /** Elimina pistas repetidas conservando el primer (más reciente) encuentro. */
    private fun List<Track>.dedupById(): List<Track> =
        distinctBy { it.id }

    private fun greetingForHour(hour: Int): Greeting = when (hour) {
        in 5..11 -> Greeting.MORNING
        in 12..19 -> Greeting.AFTERNOON
        else -> Greeting.NIGHT
    }
}
