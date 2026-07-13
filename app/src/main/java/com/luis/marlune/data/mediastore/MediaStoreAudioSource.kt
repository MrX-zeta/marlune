package com.luis.marlune.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/**
 * Fuente de la biblioteca sobre MediaStore. Consulta en `Dispatchers.IO` filtrando `IS_MUSIC != 0`,
 * construye content URIs (nunca rutas) y expone la biblioteca como Flow que **se re-emite solo**
 * cuando el sistema indexa cambios (canción copiada/borrada), vía un `ContentObserver`. Sin red.
 */
class MediaStoreAudioSource(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    private val audioCollection: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    /**
     * Biblioteca observable: emite al empezar y cada vez que el `ContentObserver` avisa de un
     * cambio; re-consulta MediaStore en IO. Desregistra el observer al cancelarse el scope.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSongs(): Flow<List<Song>> {
        val changes: Flow<Unit> = callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            resolver.registerContentObserver(audioCollection, true, observer)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }
        return changes
            .onStart { emit(Unit) } // carga inicial
            .mapLatest { querySongs() } // re-consulta en cada cambio; cancela una consulta obsoleta
            .flowOn(Dispatchers.IO)
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
