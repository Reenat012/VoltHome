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

        // Считываем persisted ownership ДО bootstrap.
        // Это и есть главный "proof", что было в БД на момент старта.
        val lockBefore = ownershipRepo.isManualLock(pid)
        val versionBefore = ownershipRepo.getBootstrapVersion(pid)

        if (manualRepo.isManualActive(pid)) {
            Log.i(
                TAG,
                "BOOTSTRAP SKIP pid=$pid reason=manualActive " +
                        "lockBefore=$lockBefore versionBefore=$versionBefore"
            )
            return
        }

        if (versionBefore >= CURRENT_BOOTSTRAP_VERSION) {
            Log.d(
                TAG,
                "BOOTSTRAP SKIP pid=$pid reason=already_bootstrapped " +
                        "lockBefore=$lockBefore versionBefore=$versionBefore"
            )
            return
        }

        val phaseMode = preferencesRepository.phaseMode.first()

        Log.w(
            TAG,
            "BOOTSTRAP START pid=$pid phaseMode=$phaseMode " +
                    "lockBefore=$lockBefore versionBefore=$versionBefore"
        )

        try {
            val dbSnapshot = membershipReader.read(pid)

            // Фиксируем, меняли ли ownership-lock внутри bootstrap реально.
            var lockChanged = false

            if (dbSnapshot.hasDuplicate) {
                if (!lockBefore) {
                    ownershipRepo.setManualLock(pid, true)
                    lockChanged = true
                }

                Log.w(TAG, "BACKFILL LOCK pid=$pid reason=duplicate_join")
            }

            // TODO: здесь добавится AUTO snapshot compare (следующий шаг если нужно)

            // Версию ставим только после успешного прохождения bootstrap.
            ownershipRepo.setBootstrapVersion(pid, CURRENT_BOOTSTRAP_VERSION)

            // Считываем persisted ownership ПОСЛЕ bootstrap.
            val lockAfter = ownershipRepo.isManualLock(pid)
            val versionAfter = ownershipRepo.getBootstrapVersion(pid)

            Log.w(
                TAG,
                "BOOTSTRAP RESULT pid=$pid duplicate=${dbSnapshot.hasDuplicate} " +
                        "lockChanged=$lockChanged lockState=$lockBefore->$lockAfter " +
                        "versionState=$versionBefore->$versionAfter"
            )

            Log.w(
                TAG,
                "BOOTSTRAP DONE pid=$pid lockAfter=$lockAfter versionAfter=$versionAfter"
            )

        } catch (t: Throwable) {
            Log.e(
                TAG,
                "BOOTSTRAP FAILED pid=$pid " +
                        "lockBefore=$lockBefore versionBefore=$versionBefore error=${t.message}",
                t
            )
            // version НЕ ставим
        }
    }
}