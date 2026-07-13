package com.luis.marlune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.luis.marlune.ui.navigation.MarluneApp
import com.luis.marlune.ui.theme.MarluneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MarluneTheme {
                MarluneApp()
            }
        }
    }
}
