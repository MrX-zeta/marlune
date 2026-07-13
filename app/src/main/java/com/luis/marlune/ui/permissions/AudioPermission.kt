package com.luis.marlune.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Permiso de audio local según versión: `READ_MEDIA_AUDIO` en API 33+ (acceso completo, sin
 * "audio parcial") y `READ_EXTERNAL_STORAGE` en Android 12 y anteriores.
 */
val requiredAudioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/** Estado del permiso: concedido, denegado (se puede volver a pedir) o denegado permanentemente. */
enum class AudioPermissionStatus { Granted, Denied, PermanentlyDenied }

fun isAudioPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, requiredAudioPermission) == PackageManager.PERMISSION_GRANTED

/** Recupera la Activity host desde un Context envuelto (para `shouldShowRequestPermissionRationale`). */
fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Abre la pantalla de detalles de la app en Ajustes (para conceder tras denegar permanentemente). */
fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
