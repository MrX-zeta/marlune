package com.luis.marlune.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.luis.marlune.MarluneApplication
import com.luis.marlune.R
import com.luis.marlune.data.repository.FavoritesRepository
import com.luis.marlune.di.AppContainer
import com.luis.marlune.ui.theme.accentFromArtwork
import kotlinx.coroutines.flow.first

// --- Paleta del widget (fuente de verdad; el widget es SIEMPRE oscuro) ---
// Un ColorProvider fijo (de un solo color) es independiente del tema del sistema: fuerza ese color
// tanto en claro como en oscuro, que es justo lo que queremos.
private val Bg = Color(0xFF0A0910)
private val TextPrimary = Color(0xFFF3F1F8)
private val TextSecondary = Color(0xFFABA6BC)
private val TextTertiary = Color(0xFF7C7791)
private val Accent = Color(0xFF8E7DF0) // acento de marca (fallback del play/pausa)
private val AccentVivid = Color(0xFFA99BFF)

private fun fixed(color: Color) = ColorProvider(color)

private val TitleStyle = TextStyle(color = ColorProvider(TextPrimary), fontSize = 14.sp, fontWeight = FontWeight.Medium)
private val ArtistStyle = TextStyle(color = ColorProvider(TextSecondary), fontSize = 12.sp, fontWeight = FontWeight.Normal)
// Tipos algo mayores para el tamaño grande (el texto escala con la carátula héroe), centrados.
private val LargeTitleStyle = TextStyle(color = ColorProvider(TextPrimary), fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
private val LargeArtistStyle = TextStyle(color = ColorProvider(TextSecondary), fontSize = 13.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)

// Umbrales por ALTURA real (SizeMode.Exact): <120 compacto (tira: carátula + 3 controles, sin texto)
// · 120–239 MEDIANO (horizontal: carátula ALTA a la izquierda, texto+controles a la derecha) ·
// >=240 MÁXIMO (vertical centrado). El vertical queda reservado al tamaño grande de verdad: en los
// medianos "casi cuadrados" la carátula competía con el texto por el alto y quedaba pequeña.
private val FullThreshold = 120.dp
private val LargeThreshold = 240.dp

/** Padding horizontal del layout grande (por lado). */
private const val LARGE_HPAD = 14f

/**
 * Widget de pantalla de inicio de Marlune con Jetpack Glance. Lee el estado del [WidgetPlaybackBus]
 * (publicado por el servicio de reproducción), NO del `MediaController` de la UI: así no se congela al
 * cerrar la app. Se actualiza SOLO por eventos empujados desde el servicio (pista, play/pausa, shuffle,
 * me gusta); no muestra progreso, así que no hay refrescos periódicos en reposo.
 */
class MarluneWidget : GlanceAppWidget() {

    // Exact: LocalSize entrega el tamaño REAL (no un bucket), para decidir cuántos controles caben.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as MarluneApplication).container
        // RESPALDO con el servicio MUERTO (proceso recién nacido: tras un reinicio, tras un kill):
        // la última pista de la SESIÓN GUARDADA, en pausa, en vez de caer a "Toca para abrir". Su
        // play REANUDA de verdad (intent de botón multimedia → onPlaybackResumption). Los metadatos
        // vienen del propio store — sin despertar la biblioteca. Si el bus tiene pista, manda el bus.
        val fallback = storedSessionSnapshot(context, container)
        provideContent { WidgetContent(container.favoritesRepository, fallback) }
    }

    /** Snapshot pintable desde la sesión guardada; `null` si no hay sesión (o el bus ya manda). */
    private suspend fun storedSessionSnapshot(
        context: Context,
        container: AppContainer,
    ): WidgetPlaybackState? {
        if (WidgetPlaybackBus.state.value.hasItem) return null // servicio vivo: su estado es la verdad
        val stored = runCatching { container.sessionStore.session.first() }.getOrNull() ?: return null
        if (stored.title.isBlank()) return null // sesión sin metadatos (formato previo): vacío honesto
        val mediaId = stored.ids.getOrNull(stored.index.coerceIn(0, stored.ids.lastIndex))?.toString()
        val artwork = loadWidgetArtwork(context, stored.artworkUri?.let(Uri::parse), mediaId)
        return WidgetPlaybackState(
            hasItem = true,
            mediaId = mediaId,
            title = stored.title,
            artist = stored.artist,
            artwork = artwork,
            accentArgb = artwork?.let { accentFromArtwork(it)?.toArgb() },
            isPlaying = false,
            shuffle = stored.shuffle,
        )
    }
}

@Composable
private fun WidgetContent(favorites: FavoritesRepository, fallback: WidgetPlaybackState?) {
    val busSnapshot by WidgetPlaybackBus.state.collectAsState()
    // El bus (servicio vivo) siempre gana; sin él, la sesión guardada; sin ninguna, estado vacío.
    val snapshot = if (busSnapshot.hasItem) busSnapshot else fallback ?: busSnapshot

    Box(modifier = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_background))) {
        if (!snapshot.hasItem) {
            EmptyState()
        } else {
            val favoriteIds by favorites.favoriteIds.collectAsState(emptySet())
            val isFavorite = snapshot.mediaId?.toLongOrNull()?.let { it in favoriteIds } == true
            // Acento dinámico de la carátula (extraído por el servicio); marca si es monocroma o no hay.
            val accent = snapshot.accentArgb?.let { Color(it) } ?: Accent
            val h = LocalSize.current.height
            when {
                h >= LargeThreshold -> LargeLayout(snapshot, isFavorite, accent)
                h >= FullThreshold -> FullLayout(snapshot, isFavorite, accent)
                else -> CompactLayout(snapshot, accent)
            }
        }
    }
}

/**
 * Layout A (mínimo, la tira ancha y baja): la CARÁTULA manda —grande, ocupa el alto— y a su derecha
 * anterior/play/siguiente repartidos con defaultWeight (sin huecos ni pegados al borde). SIN texto (se
 * recortaría a "..." sin informar) y sin shuffle/me gusta (no caben con áreas de 48 dp).
 */
@Composable
private fun CompactLayout(s: WidgetPlaybackState, accent: Color) {
    val context = LocalContext.current
    val openNowPlaying = GlanceModifier.clickable(
        actionStartActivity(openAppIntent(context, nowPlaying = true)),
        rippleOverride = R.drawable.widget_ripple_none,
    )
    val artDim = (LocalSize.current.height.value - 12f).coerceIn(40f, 96f) // llena el alto disponible
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(s.artwork, size = artDim.dp, corner = 10.dp, modifier = openNowPlaying)
        WeightedControl {
            ControlButton(R.drawable.ic_widget_skip_previous, context.getString(R.string.player_previous), TextSecondary, 48.dp, 24.dp) {
                actionRunCallback<WidgetPreviousAction>()
            }
        }
        WeightedControl { PlayPauseButton(s.isPlaying, boxSize = 56.dp, circle = 48.dp, accent = accent) }
        WeightedControl {
            ControlButton(R.drawable.ic_widget_skip_next, context.getString(R.string.player_next), TextSecondary, 48.dp, 24.dp) {
                actionRunCallback<WidgetNextAction>()
            }
        }
    }
}

/**
 * Layout B (MEDIANO): HORIZONTAL con la carátula de protagonista — cuadrada a la IZQUIERDA ocupando
 * casi todo el alto de la tarjeta (8 dp de margen; era lo que se veía pequeño en el casi cuadrado) —
 * y a su derecha una columna (defaultWeight) con el TEXTO arriba y los CONTROLES debajo (anterior ·
 * play/pausa · siguiente, áreas de 48/52 dp), centrados verticalmente. Título y artista: exactamente
 * 1 línea con elipsis manual sobre el ancho REAL del bloque — nunca empujan ni solapan controles.
 */
@Composable
private fun FullLayout(s: WidgetPlaybackState, @Suppress("UNUSED_PARAMETER") isFavorite: Boolean, accent: Color) {
    val context = LocalContext.current
    val openNowPlaying = GlanceModifier.clickable(
        actionStartActivity(openAppIntent(context, nowPlaying = true)),
        rippleOverride = R.drawable.widget_ripple_none,
    )
    val size = LocalSize.current
    // Carátula ALTA: todo el alto menos los márgenes de 8 dp, acotada para no comerse el ancho.
    val artDim = minOf(size.height.value - 16f, size.width.value * 0.45f).coerceAtLeast(64f)
    // Ancho real del bloque de texto: ancho útil − carátula − separación (para la elipsis manual).
    val textWidthDp = (size.width.value - 16f - artDim - 12f).coerceAtLeast(64f)

    Row(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(s.artwork, size = artDim.dp, corner = 12.dp, modifier = openNowPlaying)
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            // El TEXTO va centrado en el alto SOBRANTE (bloque con peso): el aire entre texto y
            // controles crece con la tarjeta y nunca quedan pegados.
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center,
            ) {
                Column(modifier = GlanceModifier.fillMaxWidth().then(openNowPlaying)) {
                    Text(ellipsize(s.title, (textWidthDp / 8f).toInt()), maxLines = 1, style = TitleStyle)
                    Spacer(GlanceModifier.height(3.dp))
                    Text(ellipsize(s.artist, (textWidthDp / 7f).toInt()), maxLines = 1, style = ArtistStyle)
                }
            }
            // Fila de controles con ALTO FIJO de 60 dp y TODO de tamaño FIJO: ningún control usa
            // weight/fillMaxWidth aquí. MIUI aplastaba el play (hexágono) cuando su celda de peso
            // cedía ancho a los laterales; con cajas fijas (48 · 52 · 48 dp) nada puede comprimir
            // el círculo. El aire entre controles son SPACERS FIJOS de 2 dp y el grupo va centrado:
            // el sobrante queda como margen a los lados del grupo, nunca dentro del play. Ojo al
            // tocar este valor: además del spacer hay aire INVISIBLE inherente (~10 dp) porque las
            // cajas táctiles de 48/52 dp son mayores que el icono de 32 y el círculo de 48. La barra
            // del skip va ENGROSADA en los propios drawables (3/24 del viewport): con la original de
            // 2/24, MIUI la difuminaba hasta perderla al reescalar fuera de los 24 dp intrínsecos.
            val controlsGap = 0.dp
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(60.dp).padding(end = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(R.drawable.ic_widget_skip_previous, context.getString(R.string.player_previous), TextSecondary, 48.dp, 30.dp) {
                    actionRunCallback<WidgetPreviousAction>()
                }
                Spacer(GlanceModifier.width(controlsGap))
                PlayPauseButton(s.isPlaying, boxSize = 48.dp, circle = 48.dp, accent = accent)
                Spacer(GlanceModifier.width(controlsGap))
                ControlButton(R.drawable.ic_widget_skip_next, context.getString(R.string.player_next), TextSecondary, 48.dp, 30.dp) {
                    actionRunCallback<WidgetNextAction>()
                }
            }
            // Margen con el borde inferior: los controles no besan el filo de la tarjeta.
            Spacer(GlanceModifier.height(6.dp))
        }
    }
}

/**
 * Layout C (grande / casi cuadrado): disposición VERTICAL. Carátula cuadrada CENTRADA arriba en un
 * bloque con defaultWeight (si el alto aprieta, cede ELLA), y DEBAJO —FUERA del bloque flexible—
 * título y artista con su FILA PROPIA cada uno: exactamente 1 línea con elipsis (regla del widget:
 * sin marquee ni scroll, o cabe o "…"; nunca 2+ líneas comiéndose al artista, nunca texto cortado a
 * media altura). La fila de controles queda al pie. Texto centrado, casando con la carátula.
 */
@Composable
private fun LargeLayout(s: WidgetPlaybackState, isFavorite: Boolean, accent: Color) {
    val context = LocalContext.current
    val openNowPlaying = GlanceModifier.clickable(
        actionStartActivity(openAppIntent(context, nowPlaying = true)),
        rippleOverride = R.drawable.widget_ripple_none,
    )
    val size = LocalSize.current
    val innerWidthDp = size.width.value - LARGE_HPAD * 2f
    val showShuffleFav = innerWidthDp >= 260f
    // Carátula: aproxima el hueco libre (alto − padding − texto+controles ≈168dp, con el play de
    // 64 dp del escalón grande); acotada por ancho.
    val artDim = (size.height.value - 168f).coerceIn(56f, minOf(innerWidthDp, 200f))

    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = LARGE_HPAD.dp, vertical = 12.dp)) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(s.artwork, size = artDim.dp, corner = 14.dp, modifier = openNowPlaying)
        }
        Spacer(GlanceModifier.height(10.dp))
        // 1 línea SIEMPRE, elipsis manual (Glance no expone ellipsize): el título jamás invade al artista.
        Text(
            ellipsize(s.title, (innerWidthDp / 9.5f).toInt()),
            maxLines = 1,
            style = LargeTitleStyle,
            modifier = GlanceModifier.fillMaxWidth().then(openNowPlaying),
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            ellipsize(s.artist, (innerWidthDp / 7.5f).toInt()),
            maxLines = 1,
            style = LargeArtistStyle,
            modifier = GlanceModifier.fillMaxWidth(),
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showShuffleFav) {
                WeightedControl {
                    ControlButton(
                        R.drawable.ic_widget_shuffle,
                        context.getString(R.string.player_shuffle),
                        if (s.shuffle) Accent else TextTertiary, 48.dp, 22.dp,
                    ) { actionRunCallback<WidgetShuffleAction>() }
                }
            }
            WeightedControl {
                ControlButton(R.drawable.ic_widget_skip_previous, context.getString(R.string.player_previous), TextSecondary, 48.dp, 30.dp) {
                    actionRunCallback<WidgetPreviousAction>()
                }
            }
            // Escalón grande: caja de 64 dp con círculo de 56 visibles (sin fila de alto fijo aquí,
            // así que la caja no compite con el contenedor y el círculo no corre riesgo de deformarse).
            // Laterales con icono de 32 dp, IGUAL que en el mediano: es el tamaño validado en device.
            WeightedControl { PlayPauseButton(s.isPlaying, boxSize = 64.dp, circle = 56.dp, accent = accent) }
            WeightedControl {
                ControlButton(R.drawable.ic_widget_skip_next, context.getString(R.string.player_next), TextSecondary, 48.dp, 30.dp) {
                    actionRunCallback<WidgetNextAction>()
                }
            }
            if (showShuffleFav) {
                WeightedControl {
                    ControlButton(
                        if (isFavorite) R.drawable.ic_widget_favorite_filled else R.drawable.ic_widget_favorite,
                        context.getString(if (isFavorite) R.string.player_unlike else R.string.player_like),
                        if (isFavorite) AccentVivid else TextTertiary, 48.dp, 22.dp,
                    ) { actionRunCallback<WidgetFavoriteAction>() }
                }
            }
        }
    }
}

/**
 * Elipsis MANUAL a 1 línea: Glance/RemoteViews no expone `ellipsize`, así que el corte con "…" se
 * hace aquí, estimando caracteres por ancho disponible. Preferimos "…" un pelín pronto a un corte
 * seco o a un texto que invada la fila siguiente. [maxChars] ya viene derivado del ancho real.
 */
private fun ellipsize(text: String, maxChars: Int): String =
    if (text.length <= maxChars) text else text.take((maxChars - 1).coerceAtLeast(1)).trimEnd() + "…"

/** Celda de igual peso que centra su control: reparte la fila por el ancho real, sin recortar extremos. */
@Composable
private fun RowScope.WeightedControl(content: @Composable () -> Unit) {
    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { content() }
}

/** Estado vacío: sin pista falsa, solo una nota + "Toca para abrir". Toca en cualquier parte abre la app. */
@Composable
private fun EmptyState() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .clickable(
                actionStartActivity(openAppIntent(context, nowPlaying = false)),
                rippleOverride = R.drawable.widget_ripple_none,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_music_note),
            contentDescription = null,
            colorFilter = ColorFilter.tint(fixed(TextTertiary)),
            modifier = GlanceModifier.size(28.dp),
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(context.getString(R.string.widget_empty), style = ArtistStyle)
    }
}

/** Carátula ya decodificada (la carga el servicio en el bus). Placeholder si no hay. Sin async → sin parpadeo. */
@Composable
private fun Artwork(bitmap: Bitmap?, size: Dp, corner: Dp, modifier: GlanceModifier) {
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).cornerRadius(corner),
        )
    } else {
        Image(
            provider = ImageProvider(R.drawable.widget_art_placeholder),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).cornerRadius(corner),
        )
    }
}

/**
 * Botón de control genérico. Área táctil REAL de [boxSize] (>= 48 dp) aunque el icono mida 22-24 dp:
 * el padding lo da la caja, no el icono. [action] es una lambda que produce la Action DENTRO del
 * clickable (cada control usa un ActionCallback distinto → PendingIntents distintos, sin dedup).
 */
@Composable
private fun ControlButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    boxSize: Dp,
    iconSize: Dp,
    action: () -> androidx.glance.action.Action,
) {
    Box(
        modifier = GlanceModifier.size(boxSize).cornerRadius(boxSize / 2)
            .clickable(action(), rippleOverride = R.drawable.widget_ripple_none),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(fixed(tint)),
            modifier = GlanceModifier.size(iconSize),
        )
    }
}

/**
 * Play/pausa: círculo teñido con el [accent] dinámico de la carátula (o la marca #8E7DF0 si es monocroma
 * o no hay) con el icono Rounded en #0A0910. El círculo es un DRAWABLE OVAL real (widget_circle) teñido
 * por ColorFilter — no un Box con cornerRadius/outline, que algunos launchers (MIUI) rasterizan
 * deformado (hexágono) en contenedores anidados. Drawables RESOURCE (no bitmaps): al alternar cambia el
 * resId, así `setImageViewResource` repinta bien en MIUI. El icono de play va algo mayor.
 *
 * El tamaño es ESCALONADO por layout (Glance no permite proporciones continuas): [boxSize] es el área
 * táctil (>= 48 dp siempre) y [circle] el diámetro visible; los iconos escalan con el círculo.
 */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, boxSize: Dp, circle: Dp, accent: Color) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.size(boxSize)
            .clickable(actionRunCallback<WidgetPlayPauseAction>(), rippleOverride = R.drawable.widget_ripple_none),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_circle),
            contentDescription = null,
            colorFilter = ColorFilter.tint(fixed(accent)),
            modifier = GlanceModifier.size(circle),
        )
        Image(
            provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
            contentDescription = context.getString(if (isPlaying) R.string.player_pause else R.string.player_play),
            colorFilter = ColorFilter.tint(fixed(Bg)),
            modifier = GlanceModifier.size(if (isPlaying) circle / 2 else circle / 2 + 4.dp),
        )
    }
}
