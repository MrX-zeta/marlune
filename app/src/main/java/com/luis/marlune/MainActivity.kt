package com.luis.marlune

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.luis.marlune.ui.navigation.MarluneApp
import com.luis.marlune.ui.theme.MarluneTheme
import com.luis.marlune.ui.widget.EXTRA_OPEN_NOW_PLAYING

class MainActivity : ComponentActivity() {

    // Petición de abrir Now Playing (llega del widget vía extra del Intent). Es un State para que un
    // `onNewIntent` (app ya viva) también dispare la expansión, no solo el arranque en frío.
    private val openNowPlaying = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openNowPlaying.value = intent.readOpenNowPlaying()
        setContent {
            MarluneTheme {
                val request by openNowPlaying
                MarluneApp(
                    openNowPlaying = request,
                    onNowPlayingConsumed = {
                        openNowPlaying.value = false
                        // Consumo REAL: al rotar, la Activity renace y relee getIntent(); si el
                        // extra siguiera en él, re-expandiría Now Playing tras cada rotación.
                        intent?.removeExtra(EXTRA_OPEN_NOW_PLAYING)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.readOpenNowPlaying()) openNowPlaying.value = true
    }

    private fun Intent?.readOpenNowPlaying(): Boolean =
        this?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true
}
