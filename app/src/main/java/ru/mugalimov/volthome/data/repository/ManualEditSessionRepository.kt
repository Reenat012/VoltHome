package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
import ru.mugalimov.volthome.domain.model.manual.ManualEditSession
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState

interface ManualEditSessionRepository {

    fun observeSession(projectId: String): Flow<ManualEditSession?>

    /** ✅ SoT для manualActive: только repo и только по projectId. */
    fun isManualActive(projectId: String): Boolean

    /** Сессия конкретного проекта (sync guard/check). */
    fun getSession(projectId: String): ManualEditSession?

    /**
     * ⚠️ Legacy/совместимость: НЕ использовать как SoT.
     * Вернёт активную сессию, если она ровно одна.
     */
    @Deprecated("Не использовать как SoT. Используйте getSession(projectId)/isManualActive(projectId).")
    fun getActiveSession(): ManualEditSession?

    /** Вход в manual: создаём in-memory base/draft и включаем manualModeActive. */
    suspend fun enterManualMode(projectId: String, baseState: ProjectEditState)

    /**
     * ✅ Выход из manual.
     *
     * ВАЖНО: реализация обязана очищать persisted marker даже если in-memory сессии нет
     * (например, после kill-process или рассинхрона).
     * После этого in-memory сессия проекта должна быть удалена (если была).
     */
    suspend fun exitManualMode(projectId: String)

    /** Применение действия к draftState строго по projectId. */
    suspend fun apply(projectId: String, action: ManualEditAction)

    /**
     * ✅ Commit 2: MANUAL последствия.
     * Новые deviceId после insert должны попасть в draft.unassignedDeviceIds
     * строго в рамках projectIdRecorded.
     */
    suspend fun addInsertedDevicesToUnassigned(
        projectId: String,
        insertedDeviceIds: List<Long>,
        opId: String
    )

    /**
     * ⚠️ Legacy: оставляем, чтобы не ломать старые места вызова.
     * Делегирует в apply(projectIdOfActiveSession,...)
     */
    @Deprecated("Используйте apply(projectId, action).")
    suspend fun apply(action: ManualEditAction)
}