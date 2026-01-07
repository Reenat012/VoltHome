package ru.mugalimov.volthome.core.theme

import androidx.compose.ui.graphics.Color

/**
 * Public API для доступа к токенам.
 * Экраны позже будут переключены на эти методы.
 */
object VhColors {

    // Единая точка правды. Все HEX строго тут.
    val tokens: VhColorTokens = VhColorTokens(
        // Surfaces
        bg = Color(0xFF0F1115),
        surface = Color(0xFF161A22),
        surfaceAlt = Color(0xFF1C2130),
        surfaceDisabled = Color(0xFF141821),
        divider = Color(0xFF262C3A),
        overlay = Color(0x80000000),
        scrim = Color(0x99000000),

        // Text
        textPrimary = Color(0xFFE6E9F0),
        textSecondary = Color(0xFFA9B0C2),
        textMuted = Color(0xFF7A8194),
        textDisabled = Color(0xFF555B6A),
        textOnAccent = Color(0xFFFFFFFF),

        // Borders
        border = Color(0xFF2E3445),
        borderStrong = Color(0xFF3E455C),
        borderDisabled = Color(0xFF232838),
        focusRing = Color(0xFF4A6CF7),

        // Accent
        primary = Color(0xFF4A6CF7),
        primaryMuted = Color(0xFF2F3E7A),
        primarySurface = Color(0xFF1E2748),

        // Status
        info = Color(0xFF4C8ED9),
        success = Color(0xFF3FAE6B),
        warning = Color(0xFFD6A43C),
        error = Color(0xFFD15454),
        infoSurface = Color(0xFF1C2A3D),
        warningSurface = Color(0xFF2A2415),
        errorSurface = Color(0xFF2A1A1A),

        // Phases
        phaseA = Color(0xFFE1C45A),
        phaseB = Color(0xFF5DBB8A),
        phaseC = Color(0xFFE27A7A),
        phase3 = Color(0xFF9AA0AE),
        phaseSurfaceA = Color(0xFF2A2616),
        phaseSurfaceB = Color(0xFF1E2A23),
        phaseSurfaceC = Color(0xFF2A1E1E),
        phaseBorderA = Color(0xFFBFA94A),
        phaseBorderB = Color(0xFF4D9C74),
        phaseBorderC = Color(0xFFC46868),

        // UX-specific
        dragTarget = Color(0xFF2D3A66),
        dragActive = Color(0xFF3A4FA3),
        highlight = Color(0xFF33406E),
    )

    // -------- Phase API --------

    fun phase(phase: UiPhase): Color = when (phase) {
        UiPhase.A -> tokens.phaseA
        UiPhase.B -> tokens.phaseB
        UiPhase.C -> tokens.phaseC
        UiPhase.THREE -> tokens.phase3
    }

    fun phaseSurface(phase: UiPhase): Color = when (phase) {
        UiPhase.A -> tokens.phaseSurfaceA
        UiPhase.B -> tokens.phaseSurfaceB
        UiPhase.C -> tokens.phaseSurfaceC
        UiPhase.THREE -> tokens.surfaceAlt
    }

    fun phaseBorder(phase: UiPhase): Color = when (phase) {
        UiPhase.A -> tokens.phaseBorderA
        UiPhase.B -> tokens.phaseBorderB
        UiPhase.C -> tokens.phaseBorderC
        UiPhase.THREE -> tokens.border
    }

    // -------- Status API --------

    fun status(status: UiStatus): Color = when (status) {
        UiStatus.INFO -> tokens.info
        UiStatus.SUCCESS -> tokens.success
        UiStatus.WARNING -> tokens.warning
        UiStatus.ERROR -> tokens.error
    }

    fun statusSurface(status: UiStatus): Color = when (status) {
        UiStatus.INFO -> tokens.infoSurface
        UiStatus.SUCCESS -> tokens.primarySurface // нейтральная “OK” подложка без отдельного successSurface
        UiStatus.WARNING -> tokens.warningSurface
        UiStatus.ERROR -> tokens.errorSurface
    }
}