package com.luis.marlune.ui.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.components.StaggeredReveal
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Fila de "Búsquedas recientes" con scroll horizontal correcto: el último chip ya no se recorta.
 * Cada término se envuelve en `key(...)`, así un chip nuevo entra con fade+rise (vía
 * [StaggeredReveal]) mientras los existentes permanecen sin re-animar. Cada chip lleva una ✕ para
 * quitar ese término.
 */
@Composable
fun RecentSearchChips(
    terms: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        terms.forEachIndexed { index, term ->
            key(term) {
                StaggeredReveal(index = index) {
                    RecentChip(
                        term = term,
                        onClick = { onSelect(term) },
                        onRemove = { onRemove(term) },
                    )
                }
            }
            // Separación entre chips; el último aporta margen final para no quedar pegado al borde.
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun RecentChip(
    term: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MarluneTheme.colors.surfaceElevated,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = term,
                style = MarluneTheme.typography.labelLarge,
                color = MarluneTheme.colors.textSecondary,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.search_recent_remove, term),
                tint = MarluneTheme.colors.textTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRemove)
                    .padding(2.dp)
                    .size(16.dp),
            )
        }
    }
}
