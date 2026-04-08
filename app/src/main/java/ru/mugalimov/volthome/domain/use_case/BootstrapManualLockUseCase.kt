package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository

/**
 * Bootstrap persisted manual-lock состояния.
 *
 * ВАЖНО:
 * - если manualActive=true в памяти, bootstrap не вмешивается;
 * - если lock=false и версия актуальна -> можно SKIP;
 * - если lock=true, SKIP запрещён:
 *   нужно хотя бы проверить консистентность и не оставлять проект в "зависшем" состоянии;
 * - duplicate join -> lock=true;
 * - stale lock без активной manual session -> lock=false.
 */
class BootstrapManualLockUseCase @Inject constructor(
    private val ownershipRepo: ProjectOwnershipRepository,
    private val manualRepo: ManualEditSessionRepository,
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
        val lockBefore = ownershipRepo.isManualLock(pid)
        val versionBefore = ownershipRepo.getBootstrapVersion(pid)

        // Если manual session уже реально активна в памяти — ничего не трогаем.
        if (manualRepo.isManualActive(pid)) {
            Log.i(
                TAG,
                "BOOTSTRAP SKIP pid=$pid reason=manualActive " +
                        "lockBefore=$lockBefore versionBefore=$versionBefore"
            )
            return
        }

        /**
         * КРИТИЧЕСКОЕ ПРАВИЛО:
         * - skip допустим только когда lock=false и версия уже актуальна;
         * - если lock=true, bootstrap ОБЯЗАН пройти дальше и проверить,
         *   не завис ли persisted lock без живой manual session.
         */
        if (!lockBefore && versionBefore >= CURRENT_BOOTSTRAP_VERSION) {
            Log.d(
                TAG,
                "BOOTSTRAP SKIP pid=$pid reason=already_bootstrapped_no_lock " +
                        "lockBefore=$lockBefore versionBefore=$versionBefore"
            )
            return
        }

        Log.w(
            TAG,
            "BOOTSTRAP START pid=$pid " +
                    "lockBefore=$lockBefore versionBefore=$versionBefore"
        )

        try {
            val dbSnapshot = membershipReader.read(pid)

            // Флаги для итогового лога.
            var lockChanged = false
            var recoveryAction = "NONE"

            /**
             * 1) Если нашли duplicate membership — это признак manual ownership.
             *    В таком случае lock должен быть true.
             */
            if (dbSnapshot.hasDuplicate) {
                if (!lockBefore) {
                    ownershipRepo.setManualLock(pid, true)
                    lockChanged = true
                }
                recoveryAction = "LOCK_BY_DUPLICATE_JOIN"

                Log.w(
                    TAG,
                    "BACKFILL LOCK pid=$pid reason=duplicate_join"
                )
            } else {
                /**
                 * 2) Если duplicate нет, manual session в памяти тоже нет,
                 *    а persisted lock=true — это stale lock.
                 *
                 *    Его надо снять, иначе проект навсегда застрянет:
                 *    manualLock=true + manualSession=null.
                 */
                if (lockBefore) {
                    ownershipRepo.setManualLock(pid, false)
                    lockChanged = true
                    recoveryAction = "CLEAR_STALE_LOCK"

                    Log.w(
                        TAG,
                        "BACKFILL CLEAR pid=$pid reason=stale_manual_lock_no_membership"
                    )
                }
            }

            // Версию ставим только после успешного прохождения bootstrap.
            ownershipRepo.setBootstrapVersion(pid, CURRENT_BOOTSTRAP_VERSION)

            // Считываем persisted ownership ПОСЛЕ bootstrap.
            val lockAfter = ownershipRepo.isManualLock(pid)
            val versionAfter = ownershipRepo.getBootstrapVersion(pid)

            Log.w(
                TAG,
                "BOOTSTRAP RESULT pid=$pid duplicate=${dbSnapshot.hasDuplicate} " +
                        "action=$recoveryAction lockChanged=$lockChanged " +
                        "lockState=$lockBefore->$lockAfter versionState=$versionBefore->$versionAfter"
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