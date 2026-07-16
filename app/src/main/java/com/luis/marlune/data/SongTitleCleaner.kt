package com.luis.marlune.data

import com.luis.marlune.domain.model.Song
import java.text.Normalizer
import java.util.Locale

/**
 * Limpiador de títulos/artistas para PRESENTACIÓN — ÚNICA fuente de verdad. Los archivos descargados de
 * YouTube (SnapTube y similares) traen títulos sucios y con convenciones INCONSISTENTES: a veces
 * "Artista - Título", a veces "Título - Artista", con viñetas/barras ("Rocky IV • Burning Heart",
 * "Phillip Phillips // Gone"), feats ("Salmo 23 feat Marco Barrientos"), remixes/versiones ("Particles
 * (slowed)"), "En Vivo", sellos (NCS, copyright free), créditos "Prod. @…", sufijos "_private", etc.
 *
 * Como NO hay una regla de posición fiable, se PUNTÚA qué segmento parece el TÍTULO (palabras función
 * "of/it/me/you/mi/yo…", indicadores "theme/soundtrack…", y un plus si llevaba un descriptor de versión:
 * "(slowed)" se le pone a las canciones, no a los artistas) y se descartan los segmentos de METADATOS
 * (formato, versión/remix, "en vivo", subtítulos, sello, género) y los ARTISTAS conocidos (por el tag del
 * archivo o por la biblioteca). Se aplica UNA vez al derivar la biblioteca (fuera del hilo principal);
 * los originales quedan en [Song.rawTitle]/[Song.rawArtist] para que la búsqueda funcione sobre ambos.
 */
object SongTitleCleaner {

    // Separador FUERTE: uno o más de guiones/barra/viñeta/grado o "//" con espacio alrededor ("A • // B",
    // "A ° B", "A - B"). Requiere espacios (no parte "spider-man"); NO usa "/" simple (aparece en
    // "español / inglés", "AC/DC"), solo "//".
    private val STRONG_SEP = Regex("\\s+(?:(?:[-–—|•·・°]|//)+\\s*)+")
    // Descriptores de VERSIÓN (se le ponen a las CANCIONES): dan prioridad de título al segmento que los
    // lleva. NO incluye formato (official/videoclip/audio…), que puede colgar de cualquiera.
    private val VERSION_DESC = Regex(
        "\\b(remix|version|slowed|sped up|nightcore|reverb|mashup|bootleg|instrumental|karaoke|acoustic" +
            "|acustico|unplugged|remaster(ed)?|8d|bass boost(ed)?|live|cover|en vivo|en directo)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val COMMA_SEP = Regex("\\s*[,，]\\s*")
    // Crédito de feat/producción: se quita desde ahí hasta el final del SEGMENTO ("Lights ft. Zyzz …").
    private val FEAT_PROD = Regex("\\s*\\b(ft|feat|featuring|prod)\\b\\.?.*$", RegexOption.IGNORE_CASE)
    private val PRIVATE_SUFFIX = Regex("[_\\-\\s]*private\\s*$", RegexOption.IGNORE_CASE)
    private val TRAILING_GROUP = Regex("\\s*[\\(\\[]([^\\)\\]]*)[\\)\\]]\\s*$")

    private const val TITLE_BOOST = 2 // plus de "titleness" para un segmento que llevaba descriptor de versión

    // Palabras "función": si un segmento las contiene, parece FRASE-título, no nombre de artista. NO se
    // incluyen artículos (the/a/la/el…) ni conjunciones (and/y): muchos ARTISTAS empiezan por artículo.
    private val FUNCTION_WORDS = setOf(
        "of", "it", "all", "me", "my", "you", "your", "i", "to", "in", "on", "is", "are", "be", "do", "for",
        "with", "at", "by", "from", "this", "that", "we", "us", "our", "if", "so", "out", "into", "like",
        "up", "down", "off", "no", "not",
        "de", "que", "tu", "mi", "por", "con", "lo", "se", "si", "te", "al", "es", "su", "para", "sin", "mas",
        "ya", "nos", "en", "eres", "amor", "yo", "soy", "tus", "mis", "toda", "todo", "todos", "todas",
    )
    private val TITLE_INDICATORS = setOf(
        "theme", "soundtrack", "ost", "bso", "intro", "outro", "interlude", "medley", "suite", "movement",
        "symphony", "concerto", "sonata", "overture", "prelude", "anthem", "score", "main", "tema",
    )
    // Descriptores que EN CUALQUIER parte del segmento lo marcan como metadato (sobre el texto "aplanado"
    // sin acentos: "versión"→"version", "oficial"→"oficial").
    private val META_ANYWHERE = Regex(
        "\\b(" +
            "remix|version|slowed|sped up|nightcore|reverb|mashup|bootleg|instrumental|karaoke|acoustic" +
            "|acustico|unplugged|remaster(ed)?|8d|bass boost(ed)?|en vivo|en directo" +
            "|letra[s]?|lyric[s]?|subtitulad\\w*|subtitle[s]?|sub (espanol|ingles|english|spanish)" +
            "|traducid\\w*|traduccion|oficial|videoclip|official|album" +
            "|ncs|nocopyrightsounds|no copyright|copyright( free)?|free (download|music|release)" +
            "|provided to youtube|out now|premiere|mp3|kbps|m4a|hi res" +
            ")\\b",
        RegexOption.IGNORE_CASE,
    )
    // Términos ambiguos que SOLO cuentan como metadato si son la ÚLTIMA palabra ("Song - Live" sí, pero
    // "Live Your Life" / "Video Games" / "Cover Me" no).
    private val ENDS_LAST = setOf(
        "live", "cover", "edit", "mix", "audio", "video", "visualizer", "hd", "hq", "uhd", "4k", "8k",
        "demo", "vip", "flip", "rework", "dub",
    )
    private val GENRE = setOf(
        "trap", "house", "edm", "lofi", "lo fi", "phonk", "drill", "techno", "hardstyle", "chill", "ambient",
        "synthwave", "dnb", "drum and bass", "hardcore", "trance", "electro", "dubstep", "future bass",
        "deep house", "tech house", "progressive house",
    )

    /**
     * Limpia cada canción de forma DETERMINISTA: el resultado depende SOLO del propio archivo (título +
     * su tag de artista), nunca del resto de la biblioteca — así el título de una canción NO cambia al
     * añadir o borrar OTRAS canciones.
     */
    fun cleanLibrary(songs: List<Song>): List<Song> = songs.map { clean(it) }

    private fun clean(song: Song): Song {
        val usable = isUsableArtist(song.artist, song.displayName)
        // Solo el tag del PROPIO archivo (desglosado por comas/&/feat) reconoce artistas. Sin conjunto de
        // biblioteca: el título es estable y no depende de qué otras canciones existan.
        val tagParts = if (usable) artistNameParts(song.artist) else emptySet()
        fun isArtistSeg(seg: String): Boolean {
            val f = flat(seg)
            return f.isNotEmpty() && f in tagParts
        }

        var artist = song.artist
        // Quita paréntesis de metadatos del título ENTERO ANTES de separar (evita partir dentro de un
        // "(2012 - Remaster)") y trata las comillas como separador. Luego limpia CADA segmento: quita
        // feat/prod y descriptores entre paréntesis, y marca (boost) los que llevaban VERSIÓN (canción).
        val cleaned = stripDownloadSuffixes(song.title).replace(Regex("[\"“”]"), " | ")
            .split(STRONG_SEP).map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { seg ->
                val afterFeat = stripFeatProd(seg)
                val hadVersion = TRAILING_GROUP.find(afterFeat)
                    ?.let { VERSION_DESC.containsMatchIn(flat(it.groupValues[1])) } == true
                val base = stripDownloadSuffixes(afterFeat)
                if (base.isBlank()) null else base to (if (hadVersion) TITLE_BOOST else 0)
            }
            .distinctBy { flat(it.first) } // deduplica segmentos repetidos ("COMO EN EL CIELO" ×2)

        var title: String
        if (cleaned.isEmpty()) {
            title = tidy(stripDownloadSuffixes(stripFeatProd(song.title))).ifBlank { song.title }
            return finalize(song, title, artist)
        }

        var segs = cleaned.filterNot { isMeta(it.first) }
        if (segs.isEmpty()) segs = cleaned
        if (segs.size == 1) {
            title = segs.first().first
        } else {
            // Candidatos = los que NO son artistas conocidos; título = el de mayor puntuación
            // (titleness + boost); empate → el último (convención Artista-Título de YouTube).
            val candidates = segs.filterNot { isArtistSeg(it.first) }.ifEmpty { segs }
            // Título = mayor puntuación (titleness + boost); empate → MÁS palabras (una frase-título suele
            // ser más larga que un nombre de artista); empate final → el último (convención Artista-Título).
            title = candidates.withIndex()
                .maxWith(
                    compareBy(
                        { titleness(it.value.first) + it.value.second },
                        { wordCount(it.value.first) },
                        { it.index },
                    ),
                ).value.first
            if (!usable) {
                artist = segs.map { it.first }.firstOrNull { isArtistSeg(it) }
                    ?: segs.filter { it.first != title }.minByOrNull { titleness(it.first) }?.first
                    ?: song.artist
            }
        }

        // Ya con el título elegido, quita partes por COMA que sean metadatos o artistas conocidos
        // (seguro: nunca descarta palabras del propio título, p. ej. "Gone, Gone, Gone").
        title = stripCommaMeta(title, ::isArtistSeg)
        return finalize(song, title, artist)
    }

    private fun finalize(song: Song, rawTitle: String, rawArtistIn: String): Song {
        var title = tidy(rawTitle)
        val artist = tidy(rawArtistIn)
        if (title.isBlank()) title = song.title // nunca sin nombre
        return if (title == song.title && artist == song.artist) {
            song.copy(rawTitle = song.title, rawArtist = song.artist)
        } else {
            song.copy(title = title, artist = artist, rawTitle = song.title, rawArtist = song.artist)
        }
    }

    /** Puntúa cuánto parece un TÍTULO (frase) frente a un nombre de artista. Mayor = más título. */
    private fun titleness(seg: String): Int {
        val words = flat(seg).split(' ').filter { it.isNotEmpty() }
        var score = 0
        if (words.any { it in FUNCTION_WORDS }) score += 2
        if (words.any { it in TITLE_INDICATORS }) score += 2
        if (words.size > 4) score += 1
        // Terminar en "!" es casi siempre nombre de GRUPO (Wham!, Panic! at the Disco, Hello Seahorse!),
        // no un título: se penaliza levemente para no elegirlo como título en un empate.
        if (seg.trim().endsWith("!")) score -= 1
        return score
    }

    /** Nº de palabras significativas (para el desempate: los títulos suelen ser más largos). */
    private fun wordCount(seg: String): Int = flat(seg).split(' ').count { it.isNotEmpty() }

    /** Un segmento es metadato si es un género, contiene un marcador fuerte, o TERMINA en un descriptor. */
    private fun isMeta(seg: String): Boolean {
        val f = flat(seg)
        if (f.isEmpty()) return false
        if (f in GENRE) return true
        if (META_ANYWHERE.containsMatchIn(f)) return true
        return f.substringAfterLast(' ') in ENDS_LAST
    }

    /** Desglosa un tag de artista en nombres individuales (por comas/&/feat) normalizados. */
    private fun artistNameParts(artist: String): Set<String> =
        artist.split(Regex("[,，/&]|\\bfeat\\b|\\bft\\b|\\bx\\b|\\by\\b|\\band\\b", RegexOption.IGNORE_CASE))
            .map { flat(it) }.filter { it.isNotEmpty() }.toSet()

    /** Quita el crédito de feat/producción de un segmento; vacío si el segmento ERA solo el crédito. */
    private fun stripFeatProd(s: String): String = FEAT_PROD.replace(s, "").trim()

    /** Quita partes por coma que sean metadatos o artistas conocidos; conserva el resto del título. */
    private fun stripCommaMeta(input: String, isArtist: (String) -> Boolean): String {
        val parts = input.split(COMMA_SEP).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return input
        val kept = parts.filterNot { isMeta(it) || isArtist(it) }
        return if (kept.isEmpty() || kept.size == parts.size) input else kept.joinToString(", ")
    }

    /** Elimina, del final, sufijos entre paréntesis/corchetes que sean metadatos y "_private". */
    private fun stripDownloadSuffixes(input: String): String {
        var t = input.trim()
        while (t.isNotEmpty()) {
            val priv = PRIVATE_SUFFIX.find(t)
            if (priv != null) { t = t.substring(0, priv.range.first).trim(); continue }
            val grp = TRAILING_GROUP.find(t) ?: break
            if (isMeta(grp.groupValues[1].trim())) {
                t = t.substring(0, grp.range.first).trim()
            } else {
                break // paréntesis descriptivo no-metadato (p. ej. "(interstellar soundtrack)"): se conserva
            }
        }
        return t
    }

    /** Colapsa espacios y quita separadores/paréntesis HUÉRFANOS (sin pareja) al inicio o al final. */
    private fun tidy(s: String): String {
        var t = s.replace(Regex("\\s+"), " ").trim()
        t = t.trim('-', '–', '—', '|', '•', '·', '・', '/', ',', ' ')
        t = t.trimStart(')', ']').trimEnd('(', '[')
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun isUsableArtist(artist: String, displayName: String): Boolean {
        if (artist.isBlank() || artist.equals("<unknown>", ignoreCase = true)) return false
        val n = norm(artist)
        return n.isNotEmpty() && n != norm(displayName.substringBeforeLast('.'))
    }

    /** Normalización tolerante para comparar NOMBRES: sin acentos, minúsculas, espacios colapsados. */
    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.getDefault())
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Como [norm] pero además reduce cualquier no-alfanumérico a espacio (para casar palabras sueltas). */
    private fun flat(s: String): String = norm(s).replace(Regex("[^a-z0-9]+"), " ").trim()
}
