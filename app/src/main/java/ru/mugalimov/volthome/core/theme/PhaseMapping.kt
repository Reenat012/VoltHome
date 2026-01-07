package ru.mugalimov.volthome.core.theme

import ru.mugalimov.volthome.domain.model.Phase

/**
 * Единственный маппинг domain -> UI contract.
 * Храним в core/theme, чтобы UI не дублировал when(Phase) по экранам.
 */
fun Phase.toUiPhase(): UiPhase = when (this) {
    Phase.A -> UiPhase.A
    Phase.B -> UiPhase.B
    Phase.C -> UiPhase.C
    Phase.THREE_PHASE -> UiPhase.THREE
}