package com.luis.marlune.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefsDataStore by preferencesDataStore(name = "app_prefs")

/**
 * Flags internos de primera experiencia (DataStore): si el onboarding ya se completó (para no
 * mostrarlo de nuevo) y si ya se pidió el permiso de notificaciones (para pedirlo una sola vez).
 */
class AppPrefsStore(context: Context) {

    private val dataStore = context.applicationContext.appPrefsDataStore

    /** El onboarding ya se completó (bienvenida vista + permiso de música concedido). */
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = true }
    }

    /** El permiso de notificaciones ya se pidió una vez (para no volver a insistir). */
    val notificationRequested: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATION_REQUESTED] ?: false }

    suspend fun setNotificationRequested() {
        dataStore.edit { it[Keys.NOTIFICATION_REQUESTED] = true }
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val NOTIFICATION_REQUESTED = booleanPreferencesKey("notification_requested")
    }
}
