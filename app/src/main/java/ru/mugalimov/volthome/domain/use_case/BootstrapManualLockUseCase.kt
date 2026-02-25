package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository

/**
 * Single entry-point bootstrap-хук.
 *
 * Требование коммита:
 * - стартовать из одного VM (ExplicationViewModel)
 * - запускать "ровно один раз на pid" (persisted через bootstrapVersion)
 *
 * Сейчас это скелет:
 * - проверяет bootstrapVersion
 * - если < CURRENT_BOOTSTRAP_VERSION -> пишет версию и логирует START
 *
 * В следующих коммитах сюда можно добавить реальную boot-логику (reconcile manual state, etc),
 * но только после того, как ownership API закреплён.
 */
class BootstrapManualLockUseCase @Inject constructor(
    private val ownershipRepo: ProjectOwnershipRepository
) {

    companion object {
        private const val TAG = "MANUAL_BOOTSTRAP"
        private const val CURRENT_BOOTSTRAP_VERSION = 1
    }

    suspend fun execute(projectId: String) {
        val pid = projectId.trim()
        if (pid.isBlank()) return

        val current = ownershipRepo.getBootstrapVersion(pid)

        // Если уже бустрапили эту версию — ничего не делаем.
        if (current >= CURRENT_BOOTSTRAP_VERSION) return

        // Лог должен появиться ровно один раз на pid (до тех пор, пока версия не повысится).
        Log.w(TAG, "BOOTSTRAP START pid=$pid fromVer=$current toVer=$CURRENT_BOOTSTRAP_VERSION")

        // Фиксируем версию (persisted).
        ownershipRepo.setBootstrapVersion(pid, CURRENT_BOOTSTRAP_VERSION)

        // Здесь будет реальная bootstrap-логика позже.
        // Сейчас intentionally empty.
    }
}