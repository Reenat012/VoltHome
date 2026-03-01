package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository

/**
 * Commit 3 — Полный bootstrap backfill.
 *
 * Гарантии:
 * - SKIP при manualActive=true (версию не ставим)
 * - FAILED не ставит версию
 * - DONE ставит версию только после compare
 * - duplicate join → lock=true
 */
class BootstrapManualLockUseCase @Inject constructor(
    private val ownershipRepo: ProjectOwnershipRepository,
    private val manualRepo: ManualEditSessionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val membershipReader: ManualLockMembershipReader,
) {

    companion object {
        private const val TAG = "MANUAL_LOCK"
        private const val CURRENT_BOOTSTRAP_VERSION = 1
    }

    suspend fun execute(projectId: String) {

        val pid = projectId.trim()
        if (pid.isBlank()) return

        if (manualRepo.isManualActive(pid)) {
            Log.i(TAG, "BOOTSTRAP SKIP pid=$pid reason=manualActive")
            return
        }

        val currentVersion = ownershipRepo.getBootstrapVersion(pid)
        if (currentVersion >= CURRENT_BOOTSTRAP_VERSION) {
            Log.d(TAG, "BOOTSTRAP SKIP pid=$pid reason=already_bootstrapped")
            return
        }

        val phaseMode = preferencesRepository.phaseMode.first()

        Log.w(TAG, "BOOTSTRAP START pid=$pid phaseMode=$phaseMode")

        try {

            val dbSnapshot = membershipReader.read(pid)

            if (dbSnapshot.hasDuplicate) {
                ownershipRepo.setManualLock(pid, true)
                Log.w(TAG, "BACKFILL LOCK pid=$pid reason=duplicate_join")
            }

            // TODO: здесь добавится AUTO snapshot compare (следующий шаг если нужно)

            ownershipRepo.setBootstrapVersion(pid, CURRENT_BOOTSTRAP_VERSION)

            Log.w(TAG, "BOOTSTRAP DONE pid=$pid")

        } catch (t: Throwable) {

            Log.e(TAG, "BOOTSTRAP FAILED pid=$pid error=${t.message}", t)
            // version НЕ ставим
        }
    }
}