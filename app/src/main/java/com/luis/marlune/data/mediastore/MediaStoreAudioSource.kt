package com.luis.marlune.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Fuente de la biblioteca sobre MediaStore. Consulta en `Dispatchers.IO` filtrando `IS_MUSIC != 0`,
 * construye content URIs (nunca rutas) y expone la biblioteca como Flow que **se re-emite solo**
 * cuando el sistema indexa cambios (canción copiada/borrada), vía un `ContentObserver`. Admite
 * además un refresco manual (red de seguridad) y un escaneo puntual con `MediaScannerConnection`.
 * Sin red.
 */
class MediaStoreAudioSource(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    private val audioCollection: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    /** Señal de refresco manual (pull-to-refresh / escaneo). Extra buffer para no perder el disparo. */
    private val manualRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Biblioteca observable: emite al empezar, cada vez que el `ContentObserver` avisa de un cambio
     * y ante un refresco manual; re-consulta MediaStore en IO. Desregistra el observer al cancelarse
     * el scope.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSongs(): Flow<List<Song>> {
        val systemChanges: Flow<Unit> = callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            resolver.registerContentObserver(audioCollection, true, observer)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }
        return merge(systemChanges, manualRefresh)
            .onStart { emit(Unit) } // carga inicial
            .mapLatest { querySongs() } // re-consulta en cada cambio; cancela una consulta obsoleta
            .flowOn(Dispatchers.IO)
    }

    /** Fuerza una re-consulta de MediaStore sin esperar a un cambio del sistema. */
    fun requestRefresh() {
        manualRefresh.tryEmit(Unit)
    }

    /**
     * Red de seguridad para archivos que el sistema aún no indexó: pide al `MediaScanner` que
     * revise los directorios públicos de audio. Suspende hasta que el escaneo termina; es
     * best-effort (con scoped storage no siempre hay acceso a rutas), así que nunca lanza.
     * Tras escanear, dispara un refresco para reflejar lo recién indexado.
     */
    suspend fun rescanPublicMedia() {
        val targets = buildList {
            add(Environment.DIRECTORY_MUSIC)
            add(Environment.DIRECTORY_DOWNLOADS)
            add(Environment.DIRECTORY_PODCASTS)
        }.mapNotNull { dir ->
            runCatching { Environment.getExternalStoragePublicDirectory(dir) }.getOrNull()
        }.filter { it.exists() }.map { it.absolutePath }.toTypedArray()

        if (targets.isNotEmpty()) {
            runCatching {
                suspendCancellableCoroutine { cont ->
                    var remaining = targets.size
                    MediaScannerConnection.scanFile(appContext, targets, null) { _, _ ->
                        remaining--
                        if (remaining <= 0 && cont.isActive) cont.resume(Unit)
                    }
                }
            }
        }
        requestRefresh()
    }

    /** Consulta única de MediaStore (usada por el auto-refresco y por el escaneo manual). */
    fun querySongs(): List<Song> {
        val genres = runCatching { queryGenres() }.getOrDefault(emptyMap())

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = ArrayList<Song>()
        resolver.query(audioCollection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                songs += Song(
                    id = id,
                    title = cursor.getString(titleCol).orEmpty(),
                    artist = cursor.getString(artistCol).orEmpty(),
                    artistId = cursor.getLong(artistIdCol),
                    album = cursor.getString(albumCol).orEmpty(),
                    albumId = albumId,
                    durationMs = cursor.getLong(durationCol),
                    trackNumber = cursor.getInt(trackCol),
                    year = cursor.getInt(yearCol),
                    genre = genres[id],
                    dateAdded = cursor.getLong(dateAddedCol),
                    contentUri = ContentUris.withAppendedId(audioCollection, id),
                    artworkUri = ContentUris.withAppendedId(albumArtCollection, albumId),
                )
            }
        }
        return songs
    }

    /** Género por canción: consulta aparte a `MediaStore.Audio.Genres` y sus miembros. */
    private fun queryGenres(): Map<Long, String> {
        val songToGenre = HashMap<Long, String>()
        val genresUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
        resolver.query(
            genresUri,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null, null, null,
        )?.use { genreCursor ->
            val genreIdCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val genreNameCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
            while (genreCursor.moveToNext()) {
                val genreId = genreCursor.getLong(genreIdCol)
                val genreName = genreCursor.getString(genreNameCol) ?: continue
                val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                resolver.query(
                    membersUri,
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                    null, null, null,
                )?.use { memberCursor ->
                    val audioIdCol = memberCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                    while (memberCursor.moveToNext()) {
                        songToGenre[memberCursor.getLong(audioIdCol)] = genreName
                    }
                }
            }
        }
        return songToGenre
    }

    private companion object {
        // content URI de carátulas de álbum (se le anexa el ALBUM_ID).
        val albumArtCollection: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
