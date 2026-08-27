package ru.mugalimov.volthome.domain.use_case.reconfiguration

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.reconfiguration.ProjectReconfigurationBackupStore
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.PhaseMode

class UndoProjectReconfigurationUseCase @Inject constructor(
    private val backupStore: ProjectReconfigurationBackupStore,
    private val explicationRepository: ExplicationRepository,
    private val manualSessionRepository: ManualEditSessionRepository,
    private val preferencesRepository: PreferencesRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(projectId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val pid = projectId.trim()
            require(pid.isNotBlank()) { "Сначала выберите проект" }
            check(!manualSessionRepository.isManualActive(pid)) {
                "Сначала завершите ручное редактирование"
            }
            val snapshot = backupStore.restore(pid) ?: error("Резервная копия не найдена")
            // Решения распределителя хранятся только в памяти и относятся к уже
            // отменённому расчёту. Очищаем их, чтобы UI не объяснял старое состояние.
            explicationRepository.setLastDistributionDecisions(emptyList())
            val restoredMode = snapshot.setup?.phase_mode
                ?.let { runCatching { PhaseMode.valueOf(it) }.getOrNull() }
            if (restoredMode != null) preferencesRepository.setPhaseMode(restoredMode)
        }
    }

    fun hasBackup(projectId: String): Boolean = backupStore.hasBackup(projectId)

    fun backupCreatedAt(projectId: String): Long? = backupStore.backupCreatedAt(projectId)
}
