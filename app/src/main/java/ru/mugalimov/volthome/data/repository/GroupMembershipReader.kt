package ru.mugalimov.volthome.data.repository

/**
 * Commit 3: Read-only reader membership.
 *
 * Задачи:
 * - Получить фактический membership проекта из БД (только чтение)
 * - Использовать ЕДИНЫЙ критерий "active devices" через DAO (никаких локальных фильтров по месту)
 * - Нормализовать membership:
 *   - исключаем unassigned (join отсутствует / groupId<=0) — по факту join-таблица и так даёт только assigned
 *   - игнорируем группы без устройств
 *   - порядок не влияет
 * - Детект "device в двух группах" => Outcome.BadDuplicateDevice (diff считается true)
 */
interface GroupMembershipReader {

    sealed class Outcome {
        data class Ok(
            val membership: Map<Long, Long> // deviceId -> canonicalGroupKeyHash (или groupSignatureId)
        ) : Outcome()

        data class BadDuplicateDevice(
            val deviceId: Long,
            val count: Int
        ) : Outcome()
    }

    /**
     * Read-only снимок membership проекта.
     */
    suspend fun readActualMembership(projectId: String): Outcome

    /**
     * Детерминированное сравнение membership (expected vs actual):
     * - сравниваем только membership, не порядок
     * - игнорируем несемантические поля
     */
    fun isMembershipDiff(
        expected: Map<Long, Long>,
        actual: Map<Long, Long>
    ): Boolean
}