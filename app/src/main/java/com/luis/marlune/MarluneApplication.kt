package com.luis.marlune

import android.app.Application
import com.luis.marlune.di.AppContainer

/** Application de Marlune: crea y retiene el [AppContainer] (DI manual) para toda la app. */
class MarluneApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
