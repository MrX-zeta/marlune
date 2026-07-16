package com.luis.marlune.ui.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
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

// Umbrales por ALTURA real (SizeMode.Exact): <110 compacto (4x1, horizontal) · 110–180 medio (4x2,
// horizontal) · >=180 grande (4x3+/casi cuadrado, vertical). Subido a 180 para que un 4x2 alto NO
// caiga en el vertical: el 4x2 se queda horizontal y solo el grande/máximo usa el vertical centrado.
private val FullThreshold = 110.dp
private val LargeThreshold = 180.dp

/** Padding horizontal del contenido completo (por lado). También el margen mínimo controles↔borde. */
private const val FULL_HPAD = 12f
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
        val favorites = (context.applicationContext as MarluneApplication).container.favoritesRepository
        provideContent { WidgetContent(favorites) }
    }
}

@Composable
private fun WidgetContent(favorites: FavoritesRepository) {
    val snapshot by WidgetPlaybackBus.state.collectAsState()

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
        WeightedControl { PlayPauseButton(s.isPlaying, 56.dp, accent) }
        WeightedControl {
            ControlButton(R.drawable.ic_widget_skip_next, context.getString(R.string.player_next), TextSecondary, 48.dp, 24.dp) {
                actionRunCallback<WidgetNextAction>()
            }
        }
    }
}

/**
 * Layout B (medio): HORIZONTAL como el mini-player. Carátula cuadrada a la IZQUIERDA que LLENA el alto
 * del bloque superior (ya no flota con hueco muerto); a su derecha, título y artista (1 línea, elipsis)
 * en un bloque con defaultWeight que absorbe el ancho sobrante (se recortan antes las letras que los
 * controles). Los controles van DEBAJO, a todo el ancho —elegido así para que quepan los 5 con áreas de
 * 48/56 dp (a la derecha del texto solo cabrían 3 en la columna estrecha)—.
 */
@Composable
private fun FullLayout(s: WidgetPlaybackState, isFavorite: Boolean, accent: Color) {
    val context = LocalContext.current
    val openNowPlaying = GlanceModifier.clickable(
        actionStartActivity(openAppIntent(context, nowPlaying = true)),
        rippleOverride = R.drawable.widget_ripple_none,
    )
    val innerWidthDp = LocalSize.current.width.value - FULL_HPAD * 2f
    // Los 5 controles con áreas de 48/56 dp solo si hay ancho: 5 celdas de peso; el play necesita >= 56.
    // Si no, se ocultan shuffle y me gusta (no se aprietan ni se cortan): anterior/play/siguiente.
    val showShuffleFav = innerWidthDp >= 280f
    // La carátula llena el alto del bloque superior (alto − padding − fila de controles): sin flotar.
    val artDim = (LocalSize.current.height.value - 72f).coerceIn(48f, 120f)

    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = FULL_HPAD.dp, vertical = 8.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(s.artwork, size = artDim.dp, corner = 12.dp, modifier = openNowPlaying)
            Spacer(GlanceModifier.width(14.dp))
            Column(modifier = GlanceModifier.defaultWeight().then(openNowPlaying)) {
                Text(s.title, maxLines = 1, style = TitleStyle)
                Spacer(GlanceModifier.height(3.dp))
                Text(s.artist, maxLines = 1, style = ArtistStyle)
            }
        }
        // Fila de controles repartida con defaultWeight: se adapta al ancho real, sin recortes.
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
                ControlButton(R.drawable.ic_widget_skip_previous, context.getString(R.string.player_previous), TextSecondary, 48.dp, 24.dp) {
                    actionRunCallback<WidgetPreviousAction>()
                }
            }
            WeightedControl { PlayPauseButton(s.isPlaying, 56.dp, accent) }
            WeightedControl {
                ControlButton(R.drawable.ic_widget_skip_next, context.getString(R.string.player_next), TextSecondary, 48.dp, 24.dp) {
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
 * Layout C (grande / casi cuadrado): disposición VERTICAL. Carátula cuadrada CENTRADA arriba (crece con
 * el alto real), y DEBAJO el título (hasta 2 líneas) y el artista a TODO el ancho del widget —así un
 * título normal cabe entero y uno largo tiene dos líneas antes de recortar; Glance no puede desplazar
 * texto, hay que darle ancho—. La fila de controles queda al pie. El bloque superior toma el alto libre
 * con defaultWeight (los controles nunca se recortan). Texto centrado, para casar con la carátula centrada.
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
    // Carátula cuadrada: llena el bloque superior (alto − padding − texto − controles) y crece con el
    // widget, acotada por el ancho. Al ir en un bloque con defaultWeight, si el alto aprieta se recorta
    // ANTES que los controles (que quedan siempre al pie).
    val artDim = (size.height.value - 156f).coerceIn(72f, minOf(innerWidthDp, 200f))

    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = LARGE_HPAD.dp, vertical = 12.dp)) {
        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(s.artwork, size = artDim.dp, corner = 14.dp, modifier = openNowPlaying)
            Spacer(GlanceModifier.height(12.dp))
            Text(s.title, maxLines = 2, style = LargeTitleStyle, modifier = GlanceModifier.fillMaxWidth().then(openNowPlaying))
            Spacer(GlanceModifier.height(3.dp))
            Text(s.artist, maxLines = 1, style = LargeArtistStyle, modifier = GlanceModifier.fillMaxWidth())
        }
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
                ControlButton(R.drawable.ic_widget_skip_previous, context.getString(R.string.player_previous), TextSecondary, 48.dp, 24.dp) {
                    actionRunCallback<WidgetPreviousAction>()
                }
            }
            WeightedControl { PlayPauseButton(s.isPlaying, 56.dp, accent) }
            WeightedControl {
                ControlButton(R.drawable.ic_widget_skip_next, context.getString(R.string.player_next), TextSecondary, 48.dp, 24.dp) {
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
 * o no hay) con el icono Rounded en #0A0910. Se usan drawables RESOURCE (no bitmaps): al alternar cambia
 * el resId, así `setImageViewResource` repinta bien en MIUI. El icono de play va algo mayor. Área ~56 dp.
 */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, boxSize: Dp, accent: Color) {
    val context = LocalContext.current
    val circle = boxSize - 8.dp // deja un anillo táctil alrededor del círculo visible
    Box(
        modifier = GlanceModifier.size(boxSize)
            .clickable(actionRunCallback<WidgetPlayPauseAction>(), rippleOverride = R.drawable.widget_ripple_none),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier.size(circle).background(fixed(accent)).cornerRadius(circle / 2),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                contentDescription = context.getString(if (isPlaying) R.string.player_pause else R.string.player_play),
                colorFilter = ColorFilter.tint(fixed(Bg)),
                modifier = GlanceModifier.size(if (isPlaying) 24.dp else 28.dp),
            )
        }
    }
}
