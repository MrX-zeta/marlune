package com.luis.marlune.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.luis.marlune.R

/**
 * Destinos de primer nivel de Marlune, expuestos en la barra inferior.
 * El orden del enum es el orden visual de las pestañas.
 */
enum class MarluneDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Rounded.Home),
    LIBRARY(R.string.nav_library, Icons.Rounded.LibraryMusic),
    SEARCH(R.string.nav_search, Icons.Rounded.Search),
}
