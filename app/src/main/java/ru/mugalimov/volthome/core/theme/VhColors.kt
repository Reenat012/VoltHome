package ru.mugalimov.volthome.core.theme

import androidx.compose.ui.graphics.Color

/**
 * Public API для доступа к токенам.
 * Экраны позже будут переключены на эти методы.
 */
object VhColors {

    // Единая точка правды. Все HEX строго тут.
    val tokens: VhColorTokens = VhColorTokens(
        // Surfaces (утверждённая база)
        bg = Color(0xFF0E1116),
        surface = Color(0xFF131822),
        surfaceAlt = Color(0xFF181E2B),      // = surface2 из спеки (в текущей модели это "Alt")
        surfaceDisabled = Color(0xFF131822), // disabled — состояние, не отдельный цвет
        divider = Color(0xFF2C3446),
        overlay = Color(0x80000000),
        scrim = Color(0x99000000),

        // Text (долгая работа, без чисто-белого)
        textPrimary = Color(0xFFE6E9EF),
        textSecondary = Color(0xFFB7BCC7),
        textMuted = Color(0xFF8A909D),
        textDisabled = Color(0xFF5F6470),
        textOnAccent = Color(0xFFFFFFFF),

        // Borders / Focus
        border = Color(0xFF3A4256),          // outline
        borderStrong = Color(0xFF3E455C),
        borderDisabled = Color(0xFF232838),
        focusRing = Color(0xFF7A92FF),

        // Accent (primary аккуратный, не маркетинговый)
        primary = Color(0xFF4F6DE6),
        primaryMuted = Color(0xFF2D3A66),
        primarySurface = Color(0xFF1C233A),

        // Status (не орут, info не путается с primary)
        info = Color(0xFF628FBE),
        success = Color(0xFF4FAE8A),
        warning = Color(0xFFD6B35A),
        error = Color(0xFFD46A6A),
        infoSurface = Color(0xFF1E2736),
        warningSurface = Color(0xFF2B2616),
        errorSurface = Color(0xFF2A1F1F),

        // Phases (архитектура не меняется, только приведение к новой базе)
        phaseA = Color(0xFFE2C66D),
        phaseB = Color(0xFF63B58B),
        phaseC = Color(0xFFD97A7A),
        phase3 = Color(0xFF8B93A6),
        phaseSurfaceA = Color(0xFF262314),
        phaseSurfaceB = Color(0xFF1E2A24),
        phaseSurfaceC = Color(0xFF2A1F1F),
        phaseBorderA = Color(0xFFC4AE5A),
        phaseBorderB = Color(0xFF4F9E78),
        phaseBorderC = Color(0xFFC96E6E),

        // UX-specific (не конфликтуют с primary)
        dragTarget = Color(0xFF2A324A),
        dragActive = Color(0xFF3A4C82),
        highlight = Color(0xFF323B55),
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