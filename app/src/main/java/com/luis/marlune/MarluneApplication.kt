package com.luis.marlune

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.luis.marlune.di.AppContainer

/**
 * Application de Marlune: crea y retiene el [AppContainer] (DI manual) para toda la app y configura
 * el [ImageLoader] singleton de Coil con caché de memoria + disco. Así, al cambiar de chip o navegar,
 * las carátulas ya cargadas salen de memoria (sin recargar ni parpadear); la clave es la propia URI.
 */
class MarluneApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // hasta 25 % de la memoria de la app para carátulas
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024) // 64 MB de caché de carátulas en disco
                    .build()
            }
            .crossfade(true)
            .build()
}
