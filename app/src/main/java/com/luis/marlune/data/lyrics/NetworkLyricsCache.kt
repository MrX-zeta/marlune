package com.luis.marlune.data.lyrics

import android.content.Context
import com.luis.marlune.domain.model.LyricLine
import com.luis.marlune.domain.model.Lyrics
import java.io.File

/** Cuántas letras descargadas hay en caché y cuánto ocupan (para mostrarlo en Ajustes). */
data class LyricsCacheStats(val count: Int, val bytes: Long)

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

    // --- Elección MANUAL del usuario (gana sobre la caché automática de red). ---
    // Se guarda en la MISMA carpeta con sufijo `.manual`: `<id>.manual.lrc`/`.manual.txt` (la letra) y
    // `<id>.manual.id` (el id de LRCLIB elegido, para marcar cuál está activo). "Borrar caché" (clear)
    // los elimina junto al resto.

    /** Letra elegida a mano para la canción, o `null` si no hay elección. */
    fun getManual(songId: Long): Lyrics? {
        val lrc = File(dir, "$songId.manual.lrc")
        if (lrc.exists()) return runCatching { LrcParser.parse(lrc.readText()) }.getOrNull()
        val txt = File(dir, "$songId.manual.txt")
        if (txt.exists()) {
            val lines = runCatching { txt.readText() }.getOrNull()?.lineSequence()
                ?.map { it.trim() }?.filter { it.isNotEmpty() }?.map { LyricLine(null, it) }?.toList()
            return lines?.takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = false) }
        }
        return null
    }

    /** Id de LRCLIB de la elección activa (para marcarla en la lista), o `null`. */
    fun getManualId(songId: Long): Long? =
        File(dir, "$songId.manual.id").takeIf { it.exists() }
            ?.let { runCatching { it.readText().trim().toLong() }.getOrNull() }

    /** Guarda la elección manual: la letra (LRC sincronizado o texto plano) y el id de LRCLIB. */
    fun putManual(songId: Long, lrcLibId: Long, syncedLrc: String?, plainText: String?) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            // Limpia una elección previa (podía ser del otro tipo) antes de escribir la nueva.
            clearManual(songId)
            when {
                !syncedLrc.isNullOrBlank() -> File(dir, "$songId.manual.lrc").writeText(syncedLrc)
                !plainText.isNullOrBlank() -> File(dir, "$songId.manual.txt").writeText(plainText)
            }
            File(dir, "$songId.manual.id").writeText(lrcLibId.toString())
        }
    }

    /** Descarta la elección manual de la canción (vuelve a regir la resolución automática). */
    fun clearManual(songId: Long) {
        runCatching {
            File(dir, "$songId.manual.lrc").delete()
            File(dir, "$songId.manual.txt").delete()
            File(dir, "$songId.manual.id").delete()
        }
    }

    fun clear() {
        runCatching { dir.deleteRecursively() }
    }

    /** Nº de letras cacheadas (archivos `.lrc`/`.txt`) y bytes totales. */
    fun stats(): LyricsCacheStats {
        val files = dir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".lrc", true) || it.name.endsWith(".txt", true)) }
            .orEmpty()
        return LyricsCacheStats(count = files.size, bytes = files.sumOf { it.length() })
    }
}
