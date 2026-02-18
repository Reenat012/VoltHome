package ru.mugalimov.volthome.domain.use_case

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-flight (Mutex) per projectId для структурных (STRUCTURE) операций.
 *
 * Зачем:
 * - Любая операция, которая трогает groups + group_device_join (rebuild/diff-commit),
 *   должна выполняться строго последовательно для одного projectId.
 *
 * Что НЕ делает:
 * - Не заменяет DB-sanity. Это только защита от гонок.
 */
@Singleton
class ProjectStructuralWriteMutex @Inject constructor() {

    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(projectId: String): Mutex =
        locks.getOrPut(projectId) { Mutex() }

    suspend fun <T> withLock(projectId: String, block: suspend () -> T): T {
        return lockFor(projectId).withLock { block() }
    }
}