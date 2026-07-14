package com.luis.marlune.ui.navigation

/**
 * Rutas de navegación (Navigation Compose). Los destinos de primer nivel coinciden con las pestañas
 * de la barra inferior ([MarluneDestination]); el resto son pantallas de detalle apiladas encima.
 */
object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"

    const val LIKED = "liked"
    const val RECENTLY_ADDED = "recently_added"
    const val HISTORY = "history"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"

    const val ALBUM_ARG = "albumId"
    const val ARTIST_ARG = "artistId"
    const val PLAYLIST_ARG = "playlistId"
    const val ALBUM_DETAIL = "album/{$ALBUM_ARG}"
    const val ARTIST_DETAIL = "artist/{$ARTIST_ARG}"
    const val PLAYLIST_DETAIL = "playlist/{$PLAYLIST_ARG}"

    fun album(id: Long) = "album/$id"
    fun artist(id: Long) = "artist/$id"
    fun playlist(id: Long) = "playlist/$id"
}

/**
 * Pestaña inferior resaltada según la ruta actual. Álbumes/Artistas y sus detalles cuelgan de
 * Biblioteca; Me gusta / Añadidas / historial cuelgan de Inicio.
 */
fun tabForRoute(route: String?): MarluneDestination = when (route) {
    Routes.LIBRARY,
    Routes.ALBUMS, Routes.ARTISTS, Routes.PLAYLISTS,
    Routes.ALBUM_DETAIL, Routes.ARTIST_DETAIL, Routes.PLAYLIST_DETAIL,
    -> MarluneDestination.LIBRARY
    Routes.SEARCH -> MarluneDestination.SEARCH
    else -> MarluneDestination.HOME
}

/** Ruta de primer nivel de cada pestaña. */
fun MarluneDestination.route(): String = when (this) {
    MarluneDestination.HOME -> Routes.HOME
    MarluneDestination.LIBRARY -> Routes.LIBRARY
    MarluneDestination.SEARCH -> Routes.SEARCH
}
