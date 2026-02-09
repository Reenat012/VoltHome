package ru.mugalimov.volthome.domain.model.manual

import ru.mugalimov.volthome.domain.model.Phase

/**
 * Manual edit actions for ProjectEditState.draftState
 */
sealed interface ManualEditAction {

    /** NOP but bumps version. */
    data object NoOp : ManualEditAction

    /** Replace entire draft (tests / batch ops). */
    data class ReplaceDraft(val newDraft: ProjectEditState) : ManualEditAction

    /** Change phase of existing group. */
    data class SetGroupPhase(
        val groupId: Long,
        val phase: Phase
    ) : ManualEditAction

    /**
     * Move device between two existing groups.
     * If source becomes empty -> it will be removed.
     */
    data class MoveDevice(
        val deviceId: Long,
        val fromGroupId: Long,
        val toGroupId: Long
    ) : ManualEditAction

    /**
     * Move device from group to unassigned container.
     * If source becomes empty -> it will be removed.
     */
    data class MoveToUnassigned(
        val deviceId: Long,
        val fromGroupId: Long
    ) : ManualEditAction

    /**
     * Move device from unassigned container into existing group.
     */
    data class MoveFromUnassigned(
        val deviceId: Long,
        val toGroupId: Long
    ) : ManualEditAction

    /**
     * Create a new group (groupNumber = nextGroupNumber) and move device into it.
     * Phase is chosen by minimal phase load (A→B→C).
     * GroupId is a temp negative id (deterministic).
     */
    data class CreateNewGroupAndMove(
        val deviceId: Long
    ) : ManualEditAction

    /**
     * Auto-assign ONLY unassigned devices using scoring:
     * capacity → upgrade → imbalance → tie-break groupNumber, else create new group.
     */
    data object AutoAssignUnassigned : ManualEditAction
}