package com.luis.marlune.ui.search.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Campo de búsqueda de la biblioteca local.
 *
 * Foco sutil: el borde y el icono tiñen al acento en 150 ms (`animateColorAsState`), sin escala
 * ni rebote. El tipeo es instantáneo: `onValueChange` propaga el texto sin ninguna animación
 * que lo retrase. Enter dispara [onSubmit] (guardar reciente); la equis limpia.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val spec = if (reducedMotion) snap<androidx.compose.ui.graphics.Color>() else tween(150)
    val borderColor by animateColorAsState(
        targetValue = if (focused) MarluneTheme.colors.accent else MarluneTheme.colors.divider,
        animationSpec = spec,
        label = "searchBorder",
    )
    val leadingColor by animateColorAsState(
        targetValue = if (focused) MarluneTheme.colors.accent else MarluneTheme.colors.textTertiary,
        animationSpec = spec,
        label = "searchLeading",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MarluneTheme.colors.surfaceElevated,
    ) {
        Row(
            // Altura estándar de barra (54 dp), contenido centrado, sin padding vertical:
            // se lee como una sola barra de un renglón, no como una tarjeta.
            modifier = Modifier
                .height(54.dp)
                .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = leadingColor,
                modifier = Modifier.size(22.dp),
            )
            Box(modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    interactionSource = interaction,
                    singleLine = true,
                    textStyle = MarluneTheme.typography.bodyLarge.copy(color = MarluneTheme.colors.textPrimary),
                    cursorBrush = SolidColor(MarluneTheme.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        style = MarluneTheme.typography.bodyLarge,
                        color = MarluneTheme.colors.textTertiary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.search_clear),
                    tint = MarluneTheme.colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClear)
                        .padding(2.dp)
                        .size(20.dp),
                )
            }
        }
    }
}
