package com.luis.marlune.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Barra inferior de 3 pestañas.
 *
 * Motion mínimo por ser la acción más frecuente de la app: solo el icono activo reacciona
 * (pequeña escala + color de acento); no hay deslizamiento de página. Con movimiento reducido
 * la escala es instantánea. El acento sigue el color dinámico vía `colorScheme.primary`.
 */
@Composable
fun MarluneBottomBar(
    selected: MarluneDestination,
    onSelect: (MarluneDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current

    NavigationBar(
        modifier = modifier,
        // Superficie más oscura que el mini-player (#1F1C2B) para separar visualmente ambas capas.
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        MarluneDestination.entries.forEach { destination ->
            val isSelected = destination == selected

            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.1f else 1f,
                animationSpec = if (reducedMotion) {
                    snap()
                } else {
                    spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
                },
                label = "navIconScale",
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes),
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MarluneTheme.colors.accent,
                    selectedTextColor = MarluneTheme.colors.accent,
                    indicatorColor = MarluneTheme.colors.accentMuted,
                    unselectedIconColor = MarluneTheme.colors.textTertiary,
                    unselectedTextColor = MarluneTheme.colors.textTertiary,
                ),
            )
        }
    }
}
