package ru.mugalimov.volthome.domain.model.manual

import ru.mugalimov.volthome.domain.model.Phase

/**
 * Commit 1: базовый набор действий для инфраструктуры.
 * Конкретные доменные операции (MoveDevice / AutoAssign / etc.) добавим позже.
 */
sealed interface ManualEditAction {

    /** Ничего не делает, но увеличивает version (удобно для тестов/проверок реактивности). */
    data object NoOp : ManualEditAction

    /** Полная замена draftState (используется в тестах и в будущих use-case пайплайнах). */
    data class ReplaceDraft(val newDraft: ProjectEditState) : ManualEditAction

    /** Commit 3: изменение фазы группы в draft (вместо GroupPhaseOverrideDao). */
    data class SetGroupPhase(
        val groupId: Long,
        val phase: Phase
    ) : ManualEditAction
}