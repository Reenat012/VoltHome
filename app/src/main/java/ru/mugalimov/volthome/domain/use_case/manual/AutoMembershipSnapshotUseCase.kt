package ru.mugalimov.volthome.domain.use_case.manual

/**
 * Commit 3: Read-only AUTO snapshot.
 *
 * Требование: использовать тот же источник параметров, что и AUTO rebuild,
 * но НЕ писать в БД и НЕ трогать coordinator/save usecase.
 *
 * Возвращаем канонический membership:
 * deviceId -> canonicalGroupKeyHash
 */
interface AutoMembershipSnapshotUseCase {

    suspend fun computeExpectedMembership(projectId: String): Map<Long, Long>
}