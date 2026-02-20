package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext

/**
 * Commit 1: single-flight coordinator для структурных операций.
 *
 * Контракт:
 * - CAS single-flight per projectId (putIfAbsent)
 * - withTimeout(60s) -> PANIC (до рестарта процесса)
 * - nested execute запрещён (CoroutineContext marker)
 * - BEGIN/END/REJECT_BUSY/PANIC_TIMEOUT логи содержат pid и opName
 */
@Singleton
class StructuralWriteCoordinator @Inject constructor() {

    sealed class Outcome<out T> {
        data class Success<T>(val value: T) : Outcome<T>()
        object Busy : Outcome<Nothing>()
        object Panic : Outcome<Nothing>()
        data class Error(val throwable: Throwable) : Outcome<Nothing>()
    }

    private data class InFlight(val opName: String)

    private val inFlightByProject = ConcurrentHashMap<String, InFlight>()
    private val panicProjects = ConcurrentHashMap<String, Boolean>()

    private val TAG = "STRUCT_WRITER"
    private val TIMEOUT_MS = 60_000L

    /**
     * Маркер для запрета nested calls.
     */
    private object WriterMarkerKey : CoroutineContext.Key<WriterMarker>
    private class WriterMarker : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*> = WriterMarkerKey
    }

    fun isPanic(projectId: String): Boolean = panicProjects[projectId] == true

    suspend fun <T> execute(
        projectId: String,
        opName: String,
        block: suspend () -> T
    ): Outcome<T> {
        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        if (isPanic(projectId)) {
            Log.e(TAG, "PANIC_BLOCK pid=$projectId op=$opName")
            return Outcome.Panic
        }

        val ctx = kotlin.coroutines.coroutineContext
        if (ctx[WriterMarkerKey] != null) {
            val t = IllegalStateException("Nested structural execute is forbidden")
            Log.e(TAG, "NESTED_FORBIDDEN pid=$projectId op=$opName", t)
            return Outcome.Error(t)
        }

        val prev = inFlightByProject.putIfAbsent(projectId, InFlight(opName))
        if (prev != null) {
            Log.w(TAG, "REJECT_BUSY pid=$projectId op=$opName busyOp=${prev.opName}")
            return Outcome.Busy
        }

        Log.i(TAG, "BEGIN pid=$projectId op=$opName")
        try {
            return withContext(WriterMarker()) {
                try {
                    val value = withTimeout(TIMEOUT_MS) { block() }
                    Log.i(TAG, "END pid=$projectId op=$opName")
                    Outcome.Success(value)
                } catch (t: TimeoutCancellationException) {
                    panicProjects[projectId] = true
                    Log.e(TAG, "PANIC_TIMEOUT pid=$projectId op=$opName timeoutMs=$TIMEOUT_MS", t)
                    Outcome.Panic
                } catch (t: Throwable) {
                    Log.e(TAG, "ERROR pid=$projectId op=$opName", t)
                    Outcome.Error(t)
                }
            }
        } finally {
            inFlightByProject.remove(projectId)
        }
    }
}