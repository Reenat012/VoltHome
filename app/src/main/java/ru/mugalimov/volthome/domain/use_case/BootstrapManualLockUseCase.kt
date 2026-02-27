package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.GroupMembershipReader
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.use_case.manual.AutoMembershipSnapshotUseCase

/**
 * Commit 3: BootstrapManualLockUseCase
 *
 * Требования:
 * - SKIP при manualActive=true (версию НЕ ставим)
 * - Read-only: никаких side effects на структуру/координаторы/auto-save
 * - Версию ставим ТОЛЬКО после успешной diff-процедуры (diff true/false неважно)
 * - Детерминированный diff: сравниваем membership, порядок игнорируем, unassigned/empty groups игнорируем
 * - Если device встречается в join в >1 группе => diff=true, lock=true, reason=BAD_JOIN_DUPLICATE_DEVICE
 * - При исключении: FAILED, версию НЕ меняем
 *
 * ВАЖНО: bootstrapVersion фиксируется через ProjectOwnershipRepository.
 */
class BootstrapManualLockUseCase @Inject constructor(
    private val ownershipRepo: ProjectOwnershipRepository,
    private val manualRepo: ManualEditSessionRepository,
    private val membershipReader: GroupMembershipReader,
    private val autoSnapshotUseCase: AutoMembershipSnapshotUseCase,
) {

    companion object {
        private const val TAG = "MANUAL_LOCK"
        private const val CURRENT_BOOTSTRAP_VERSION = 1
    }

    suspend fun execute(projectId: String) {
        val pid = projectId.trim()
        if (pid.isBlank()) return

        // 3.1 SKIP если manualActive=true (версию НЕ ставим)
        // ВАЖНО: используем репозиторий ручных сессий, а не marker.
        if (manualRepo.isManualActive(pid)) {
            Log.w(TAG, "BOOTSTRAP SKIP pid=$pid reason=manualActive")
            return
        }

        val current = ownershipRepo.getBootstrapVersion(pid)
        if (current >= CURRENT_BOOTSTRAP_VERSION) {
            // Уже бустрапили эту версию — ничего не делаем.
            return
        }

        Log.w(TAG, "BOOTSTRAP START pid=$pid fromVer=$current toVer=$CURRENT_BOOTSTRAP_VERSION")

        try {
            // ===== Read-only DB snapshot (actual membership) =====
            val actual = membershipReader.readActualMembership(pid)

            // Если reader обнаружил bad join (device в двух группах), он уже вернёт flagged outcome.
            if (actual is GroupMembershipReader.Outcome.BadDuplicateDevice) {
                Log.w(
                    TAG,
                    "BACKFILL pid=$pid reason=BAD_JOIN_DUPLICATE_DEVICE deviceId=${actual.deviceId} count=${actual.count}"
                )
                // Структура сломана/не системная => ownership lock включаем.
                ownershipRepo.setManualLock(pid, true)

                // ✅ ВЕРСИЮ СТАВИМ ТОЛЬКО ПОСЛЕ УСПЕШНОЙ DIFF-ПРОЦЕДУРЫ
                ownershipRepo.setBootstrapVersion(pid, CURRENT_BOOTSTRAP_VERSION)
                Log.w(TAG, "BOOTSTRAP DONE pid=$pid diff=true (badJoinDuplicateDevice)")
                return
            }

            require(actual is GroupMembershipReader.Outcome.Ok) {
                "Unexpected membershipReader outcome: $actual"
            }

            // ===== Read-only AUTO snapshot (expected membership) =====
            // Это должна быть чистая процедура (как AUTO rebuild, но без сохранения).
            val expected = autoSnapshotUseCase.computeExpectedMembership(pid)

            val diff = membershipReader.isMembershipDiff(expected = expected, actual = actual.membership)

            if (diff) {
                // diff=true => проект не соответствует системной структуре => manual lock включаем
                ownershipRepo.setManualLock(pid, true)
            }

            // 3.2 Версию ставим только после успешного compare (diff true/false — не важно)
            ownershipRepo.setBootstrapVersion(pid, CURRENT_BOOTSTRAP_VERSION)

            Log.w(TAG, "BOOTSTRAP DONE pid=$pid diff=$diff")
        } catch (t: Throwable) {
            // 3.2 FAILED => версию НЕ меняем
            Log.e(TAG, "BOOTSTRAP FAILED pid=$pid error=${t.message}", t)
        }
    }
}