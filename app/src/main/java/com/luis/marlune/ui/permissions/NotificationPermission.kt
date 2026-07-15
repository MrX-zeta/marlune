package com.luis.marlune.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.marlune.di.rememberAppPrefsStore
import kotlinx.coroutines.launch

/**
 * Pide el permiso de notificaciones (Android 13+) la PRIMERA vez que hay reproducción activa
 * ([hasStartedPlayback]) —cuando cobra sentido: controlar la música desde la barra— y **solo una
 * vez** (flag persistido). Si se deniega, la reproducción sigue igual (solo se pierde la notificación);
 * no se vuelve a insistir. En APIs anteriores a 13 no hace nada.
 */
@Composable
fun RequestNotificationPermissionOnFirstPlay(hasStartedPlayback: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val prefs = rememberAppPrefsStore()
    val scope = rememberCoroutineScope()
    val alreadyRequested by prefs.notificationRequested.collectAsStateWithLifecycle(initialValue = null)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(hasStartedPlayback, alreadyRequested) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (hasStartedPlayback && alreadyRequested == false && !granted) {
            scope.launch { prefs.setNotificationRequested() } // marca antes → jamás se vuelve a pedir
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
