package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex

/**
 * Single-flight (Mutex) per projectId для структурных (STRUCTURE) операций.
 *
 * Зачем:
 * - Любая операция, которая трогает groups + group_device_join (rebuild/diff-commit),
 *   должна выполняться строго последовательно для одного projectId.
 *
 * ВАЖНО:
 * - Таймаут в withLockTimeout() ограничивает только ожидание захвата лока,
 *   а не исполнение блока.
 */
@Singleton
class ProjectStructuralWriteMutex @Inject constructor() {

    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(projectId: String): Mutex =
        locks.getOrPut(projectId) { Mutex() }

    suspend fun <T> withLock(projectId: String, block: suspend () -> T): T {
        val m = lockFor(projectId)
        m.lock()
        try {
            return block()
        } finally {
            m.unlock()
        }
    }

    /**
     * Таймаут ТОЛЬКО на ожидание лока.
     *
     * @throws TimeoutCancellationException если lock не был получен за waitMs
     */
    suspend fun <T> withLockTimeout(
        projectId: String,
        waitMs: Long,
        opName: String,
        block: suspend () -> T
    ): T {
        require(projectId.isNotBlank()) { "projectId must be non-blank" }
        require(waitMs > 0) { "waitMs must be > 0" }

        val tag = "STRUCT_LOCK"
        val m = lockFor(projectId)

        val t0 = android.os.SystemClock.elapsedRealtime()
        val locked = try {
            withTimeout(waitMs) {
                m.lock()
                true
            }
        } catch (t: TimeoutCancellationException) {
            val waited = android.os.SystemClock.elapsedRealtime() - t0
            Log.e(tag, "LOCK TIMEOUT op=$opName pid=$projectId waitedMs=$waited waitLimitMs=$waitMs")
            throw t
        }

        if (!locked) {
            // На практике сюда не попадём, но оставим “железно”.
            val waited = android.os.SystemClock.elapsedRealtime() - t0
            Log.e(tag, "LOCK FAILED op=$opName pid=$projectId waitedMs=$waited")
            throw IllegalStateException("Failed to acquire structural lock. pid=$projectId op=$opName")
        }

        val waited = android.os.SystemClock.elapsedRealtime() - t0
        Log.w(tag, "LOCK ACQUIRED op=$opName pid=$projectId waitedMs=$waited")

        try {
            return block()
        } finally {
            m.unlock()
            Log.w(tag, "LOCK RELEASED op=$opName pid=$projectId")
        }
    }
}