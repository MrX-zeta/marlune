package com.luis.marlune.ui.navigation

import com.luis.marlune.ui.home.LibraryShortcut

/**
 * Rutas de navegación (Navigation Compose). Los destinos de primer nivel coinciden con las pestañas
 * de la barra inferior ([MarluneDestination]); el resto son pantallas de detalle apiladas encima.
 */
object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"

    const val LIKED = "liked"
    const val HISTORY = "history"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"

    const val ALBUM_ARG = "albumId"
    const val ARTIST_ARG = "artistId"
    const val ALBUM_DETAIL = "album/{$ALBUM_ARG}"
    const val ARTIST_DETAIL = "artist/{$ARTIST_ARG}"

    fun album(id: Long) = "album/$id"
    fun artist(id: Long) = "artist/$id"
}

/** Ruta de la tarjeta de Inicio. */
fun LibraryShortcut.route(): String = when (this) {
    LibraryShortcut.LIKED -> Routes.LIKED
    LibraryShortcut.PLAYLISTS -> Routes.PLAYLISTS
    LibraryShortcut.ALBUMS -> Routes.ALBUMS
    LibraryShortcut.ARTISTS -> Routes.ARTISTS
}

/** Pestaña inferior resaltada según la ruta actual (los detalles cuelgan de Inicio). */
fun tabForRoute(route: String?): MarluneDestination = when (route) {
    Routes.LIBRARY -> MarluneDestination.LIBRARY
    Routes.SEARCH -> MarluneDestination.SEARCH
    else -> MarluneDestination.HOME
}

/** Ruta de primer nivel de cada pestaña. */
fun MarluneDestination.route(): String = when (this) {
    MarluneDestination.HOME -> Routes.HOME
    MarluneDestination.LIBRARY -> Routes.LIBRARY
    MarluneDestination.SEARCH -> Routes.SEARCH
}
