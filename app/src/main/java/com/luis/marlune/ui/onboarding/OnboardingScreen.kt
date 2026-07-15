package com.luis.marlune.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.permissions.AudioPermissionUiState
import com.luis.marlune.ui.permissions.PermissionRationaleScreen
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme
import kotlinx.coroutines.launch

/**
 * Onboarding de DOS pantallas, solo en el primer arranque:
 *  1. Bienvenida (marca + qué es Marlune) con "Continuar".
 *  2. Explicación del permiso de música ANTES de pedirlo (reutiliza [PermissionRationaleScreen], que
 *     ya maneja pedir / reintentar / abrir Ajustes). Al conceder, [onCompleted] marca el onboarding.
 *
 * No menciona letras ni red (función secundaria; se descubre en su sitio). Indicador de 2 puntos;
 * transición horizontal ≤300 ms sin rebote; respeta el movimiento reducido.
 */
@Composable
fun OnboardingFlow(
    permission: AudioPermissionUiState,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // Concedido el permiso (desde la pág. 2) → onboarding completado.
    LaunchedEffect(permission.isGranted) {
        if (permission.isGranted) onCompleted()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                userScrollEnabled = false, // el avance lo conduce el botón "Continuar"
            ) { page ->
                if (page == 0) {
                    WelcomePage(
                        onContinue = {
                            scope.launch {
                                if (reducedMotion) pagerState.scrollToPage(1)
                                else pagerState.animateScrollToPage(1, animationSpec = tween(240, easing = FastOutSlowInEasing))
                            }
                        },
                    )
                } else {
                    // Reutiliza la pantalla de explicación existente (pedir / reintentar / Ajustes).
                    PermissionRationaleScreen(state = permission)
                }
            }
            OnboardingDots(
                count = 2,
                selected = pagerState.currentPage,
                modifier = Modifier.navigationBarsPadding().padding(bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun WelcomePage(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MarluneTheme.colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MarluneTheme.colors.accent,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MarluneTheme.typography.headlineSmall,
            color = MarluneTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MarluneTheme.typography.bodyMedium,
            color = MarluneTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(
                containerColor = MarluneTheme.colors.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}

/** Indicador discreto de 2 puntos: el activo se ensancha y se tiñe de acento (o snap si mov. reducido). */
@Composable
private fun OnboardingDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            val active = i == selected
            val width by animateDpAsState(
                targetValue = if (active) 20.dp else 8.dp,
                animationSpec = if (reducedMotion) snap() else tween(220, easing = FastOutSlowInEasing),
                label = "dotWidth",
            )
            val color by animateColorAsState(
                targetValue = if (active) MarluneTheme.colors.accent else MarluneTheme.colors.accentMuted,
                animationSpec = if (reducedMotion) snap() else tween(220),
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
