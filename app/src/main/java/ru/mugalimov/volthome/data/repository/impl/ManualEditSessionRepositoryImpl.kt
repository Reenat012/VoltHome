package ru.mugalimov.volthome.data.repository.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
import ru.mugalimov.volthome.domain.model.manual.ManualEditSession
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualEditSessionRepositoryImpl @Inject constructor() : ManualEditSessionRepository {

    private val sessionFlow = MutableStateFlow<ManualEditSession?>(null)

    override fun observeSession(projectId: String): Flow<ManualEditSession?> {
        return sessionFlow
            .map { s -> if (s?.projectId == projectId) s else null }
            .distinctUntilChanged()
    }

    override fun getActiveSession(): ManualEditSession? = sessionFlow.value

    override suspend fun enterManualMode(projectId: String, baseState: ProjectEditState) {
        val now = System.currentTimeMillis()
        val base = baseState.deepCopy()
        val draft = baseState.deepCopy()

        sessionFlow.value = ManualEditSession(
            projectId = projectId,
            manualModeActive = true,
            baseState = base,
            draftState = draft,
            version = 1L,
            updatedAtEpochMs = now
        )
    }

    override suspend fun exitManualMode(projectId: String) {
        val current = sessionFlow.value
        if (current?.projectId != projectId) return
        sessionFlow.value = null
    }

    override suspend fun apply(action: ManualEditAction) {
        val current = sessionFlow.value ?: return
        if (!current.manualModeActive) return

        val now = System.currentTimeMillis()

        val newDraft = when (action) {
            ManualEditAction.NoOp -> current.draftState

            is ManualEditAction.ReplaceDraft -> action.newDraft.deepCopy()

            is ManualEditAction.SetGroupPhase -> {
                val updatedGroups = current.draftState.groups.map { g ->
                    if (g.groupId == action.groupId) g.copy(phase = action.phase) else g
                }
                current.draftState.copy(groups = updatedGroups)
            }
        }

        sessionFlow.value = current.copy(
            draftState = newDraft,
            version = current.version + 1L,
            updatedAtEpochMs = now
        )
    }
}