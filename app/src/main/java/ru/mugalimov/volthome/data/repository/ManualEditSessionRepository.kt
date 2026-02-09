package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
import ru.mugalimov.volthome.domain.model.manual.ManualEditSession
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState

interface ManualEditSessionRepository {

    fun observeSession(projectId: String): Flow<ManualEditSession?>

    /** Текущая сессия (если есть) — для синхронных guard/check в VM/Sync слоях. */
    fun getActiveSession(): ManualEditSession?

    /** Вход в manual: создаём in-memory base/draft и включаем manualModeActive. */
    suspend fun enterManualMode(projectId: String, baseState: ProjectEditState)

    /** Выход из manual: сессия удаляется. */
    suspend fun exitManualMode(projectId: String)

    /** Применение действия к draftState (reduce + пост-обработка). */
    suspend fun apply(action: ManualEditAction)
}