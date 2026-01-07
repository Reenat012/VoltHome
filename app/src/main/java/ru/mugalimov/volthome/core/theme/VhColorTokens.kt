package ru.mugalimov.volthome.core.theme

import androidx.compose.ui.graphics.Color

/**
 * Единый набор токенов цветовой системы VoltHome.
 * ВСЕ HEX живут только здесь (core/theme).
 */
data class VhColorTokens(
    // Surfaces
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceDisabled: Color,
    val divider: Color,
    val overlay: Color,
    val scrim: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val textOnAccent: Color,

    // Borders
    val border: Color,
    val borderStrong: Color,
    val borderDisabled: Color,
    val focusRing: Color,

    // Accent (single)
    val primary: Color,
    val primaryMuted: Color,
    val primarySurface: Color,

    // Status
    val info: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val infoSurface: Color,
    val warningSurface: Color,
    val errorSurface: Color,

    // Phases (engineering entities)
    val phaseA: Color,
    val phaseB: Color,
    val phaseC: Color,
    val phase3: Color,
    val phaseSurfaceA: Color,
    val phaseSurfaceB: Color,
    val phaseSurfaceC: Color,
    val phaseBorderA: Color,
    val phaseBorderB: Color,
    val phaseBorderC: Color,

    // UX-specific
    val dragTarget: Color,
    val dragActive: Color,
    val highlight: Color,
)