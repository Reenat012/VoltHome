package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
import ru.mugalimov.volthome.domain.model.manual.ManualEditSession
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState

interface ManualEditSessionRepository {

    fun observeSession(projectId: String): Flow<ManualEditSession?>

    /** Текущая сессия (если есть) — для синхронных guard/check в VM/Sync слоях. */
    fun getActiveSession(): ManualEditSession?

    suspend fun enterManualMode(projectId: String, baseState: ProjectEditState)

    suspend fun exitManualMode(projectId: String)

    suspend fun apply(action: ManualEditAction)
}