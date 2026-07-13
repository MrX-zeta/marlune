package com.luis.marlune.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fuente única de verdad del color de Marlune.
 *
 * Escala neutra (fondos y texto): NUNCA cambia con color dinámico, garantiza contraste.
 * Acento: único punto que mueve el color dinámico (play, toggles activos, marea).
 * Marea: segundo tono aqua, reservado a la firma de progreso.
 */

// --- Escala neutra (scaffold) ---
val MarluneBackground = Color(0xFF0A0910)
val MarluneSurface = Color(0xFF15131E)
val MarluneSurfaceElevated = Color(0xFF1F1C2B)
val MarluneDivider = Color(0xFF2B2839)

// --- Texto (jerarquía de contraste) ---
val MarluneTextPrimary = Color(0xFFF3F1F8)
val MarluneTextSecondary = Color(0xFFABA6BC)
val MarluneTextTertiary = Color(0xFF7C7791)

// --- Acento (marca + fallback del color dinámico) ---
val MarluneAccent = Color(0xFF8E7DF0)
val MarluneAccentVivid = Color(0xFFA99BFF)
val MarluneAccentMuted = Color(0xFF3A3357)

// --- Marea (único segundo tono) ---
val MarluneMarea = Color(0xFF6FD8C6)
