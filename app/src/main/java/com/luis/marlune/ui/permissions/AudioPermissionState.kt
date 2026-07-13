package com.luis.marlune.ui.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleStartEffect

/** Estado de permiso expuesto a la UI, con acciones para pedirlo o abrir Ajustes. */
@Immutable
data class AudioPermissionUiState(
    val status: AudioPermissionStatus,
    val request: () -> Unit,
    val openSettings: () -> Unit,
) {
    val isGranted: Boolean get() = status == AudioPermissionStatus.Granted
}

/**
 * Gestiona el permiso de audio en runtime: lo solicita una vez al entrar, distingue denegado de
 * denegado permanentemente ("no volver a preguntar", vía `shouldShowRequestPermissionRationale`) y
 * reevalúa al volver a primer plano (p. ej. tras concederlo en Ajustes).
 */
@Composable
fun rememberAudioPermissionState(): AudioPermissionUiState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var status by rememberSaveable {
        mutableStateOf(
            if (isAudioPermissionGranted(context)) AudioPermissionStatus.Granted else AudioPermissionStatus.Denied,
        )
    }
    var requestedOnce by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = when {
            granted -> AudioPermissionStatus.Granted
            activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, requiredAudioPermission) ->
                AudioPermissionStatus.PermanentlyDenied
            else -> AudioPermissionStatus.Denied
        }
    }

    val requestPermission: () -> Unit = { launcher.launch(requiredAudioPermission) }

    // Solicita una vez al entrar si aún no está concedido.
    LaunchedEffect(Unit) {
        if (!requestedOnce && !isAudioPermissionGranted(context)) {
            requestedOnce = true
            requestPermission()
        }
    }

    // Reevalúa al volver a primer plano (tras conceder en Ajustes) sin volver a lanzar el diálogo.
    LifecycleStartEffect(Unit) {
        if (isAudioPermissionGranted(context)) status = AudioPermissionStatus.Granted
        onStopOrDispose { }
    }

    return remember(status) {
        AudioPermissionUiState(
            status = status,
            request = requestPermission,
            openSettings = { context.openAppSettings() },
        )
    }
}
