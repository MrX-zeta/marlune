package com.luis.marlune.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Pantalla que explica por qué Marlune necesita el acceso a la música y ofrece la acción
 * adecuada: pedir el permiso, o —si está denegado permanentemente— abrir Ajustes.
 */
@Composable
fun PermissionRationaleScreen(
    state: AudioPermissionUiState,
    modifier: Modifier = Modifier,
) {
    val permanentlyDenied = state.status == AudioPermissionStatus.PermanentlyDenied

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MarluneTheme.colors.accent,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.permission_title),
                style = MarluneTheme.typography.headlineSmall,
                color = MarluneTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (permanentlyDenied) R.string.permission_body_settings else R.string.permission_body,
                ),
                style = MarluneTheme.typography.bodyMedium,
                color = MarluneTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { if (permanentlyDenied) state.openSettings() else state.request() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarluneTheme.colors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(
                        if (permanentlyDenied) R.string.permission_open_settings else R.string.permission_grant,
                    ),
                )
            }
        }
    }
}
