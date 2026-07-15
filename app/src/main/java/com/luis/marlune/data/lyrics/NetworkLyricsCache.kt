package com.luis.marlune.data.lyrics

import android.content.Context
import com.luis.marlune.domain.model.LyricLine
import com.luis.marlune.domain.model.Lyrics
import java.io.File

/**
 * Caché en disco de las letras descargadas de internet, en almacenamiento PRIVADO de la app
 * (`filesDir/lyrics_cache`, sin permisos, no toca las carpetas del usuario). Se cachea por id de
 * canción: `<id>.lrc` (sincronizada, formato LRC) o `<id>.txt` (plana). "Borrar caché" vacía la
 * carpeta.
 */
class NetworkLyricsCache(context: Context) {

    private val dir: File = File(context.applicationContext.filesDir, "lyrics_cache")

    fun get(songId: Long): Lyrics? {
        val lrc = File(dir, "$songId.lrc")
        if (lrc.exists()) return runCatching { LrcParser.parse(lrc.readText()) }.getOrNull()
        val txt = File(dir, "$songId.txt")
        if (txt.exists()) {
            val lines = runCatching { txt.readText() }.getOrNull()?.lineSequence()
                ?.map { it.trim() }?.filter { it.isNotEmpty() }?.map { LyricLine(null, it) }?.toList()
            return lines?.takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = false) }
        }
        return null
    }

    /** Guarda el LRC sincronizado (preferente) o el texto plano. */
    fun put(songId: Long, syncedLrc: String?, plainText: String?) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            when {
                !syncedLrc.isNullOrBlank() -> File(dir, "$songId.lrc").writeText(syncedLrc)
                !plainText.isNullOrBlank() -> File(dir, "$songId.txt").writeText(plainText)
            }
        }
    }

    fun clear() {
        runCatching { dir.deleteRecursively() }
    }
}
