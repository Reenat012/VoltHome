package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext

/**
 * Single-flight coordinator для структурных операций (writer).
 *
 * Commit 3:
 * - BEGIN/END содержат pid/opName + meta(reason/source/opId)
 * - PANIC timeout
 * - nested execute запрещён через CoroutineContext marker
 */
@Singleton
class StructuralWriteCoordinator @Inject constructor() {

    sealed class Outcome<out T> {
        data class Success<T>(val value: T) : Outcome<T>()
        object Busy : Outcome<Nothing>()
        object Panic : Outcome<Nothing>()
        data class Error(val throwable: Throwable) : Outcome<Nothing>()
    }

    data class Meta(
        val reason: String,
        val source: String,
        val opId: String
    )

    private data class InFlight(
        val opName: String,
        val meta: Meta?
    )

    private val inFlightByProject = ConcurrentHashMap<String, InFlight>()
    private val panicProjects = ConcurrentHashMap<String, Boolean>()

    private val TAG = "STRUCT_WRITER"
    private val TIMEOUT_MS = 60_000L

    private object WriterMarkerKey : CoroutineContext.Key<WriterMarker>
    private class WriterMarker : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*> = WriterMarkerKey
    }

    fun isPanic(projectId: String): Boolean = panicProjects[projectId] == true

    suspend fun <T> execute(
        projectId: String,
        opName: String,
        block: suspend () -> T
    ): Outcome<T> = execute(projectId = projectId, opName = opName, meta = null, block = block)

    suspend fun <T> execute(
        projectId: String,
        opName: String,
        meta: Meta?,
        block: suspend () -> T
    ): Outcome<T> {
        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        if (isPanic(projectId)) {
            Log.e(TAG, "PANIC_BLOCK pid=$projectId op=$opName ${metaToLog(meta)}")
            return Outcome.Panic
        }

        val ctx = currentCoroutineContext()
        if (ctx[WriterMarkerKey] != null) {
            val t = IllegalStateException("Nested structural execute is forbidden")
            Log.e(TAG, "NESTED_FORBIDDEN pid=$projectId op=$opName ${metaToLog(meta)}", t)
            return Outcome.Error(t)
        }

        val prev = inFlightByProject.putIfAbsent(projectId, InFlight(opName = opName, meta = meta))
        if (prev != null) {
            Log.w(
                TAG,
                "REJECT_BUSY pid=$projectId op=$opName busyOp=${prev.opName} " +
                        "busyMeta=${metaToLog(prev.meta)} requestedMeta=${metaToLog(meta)}"
            )
            return Outcome.Busy
        }

        Log.i(TAG, "BEGIN pid=$projectId op=$opName ${metaToLog(meta)}")

        try {
            return withContext(WriterMarker()) {
                try {
                    val value = withTimeout(TIMEOUT_MS) { block() }
                    Log.i(TAG, "END pid=$projectId op=$opName ${metaToLog(meta)}")
                    Outcome.Success(value)
                } catch (t: TimeoutCancellationException) {
                    panicProjects[projectId] = true
                    Log.e(
                        TAG,
                        "PANIC_TIMEOUT pid=$projectId op=$opName timeoutMs=$TIMEOUT_MS ${metaToLog(meta)}",
                        t
                    )
                    Outcome.Panic
                } catch (t: Throwable) {
                    Log.e(TAG, "ERROR pid=$projectId op=$opName ${metaToLog(meta)}", t)
                    Outcome.Error(t)
                }
            }
        } finally {
            inFlightByProject.remove(projectId)
        }
    }

    private fun metaToLog(meta: Meta?): String {
        if (meta == null) return ""
        return "reason=${meta.reason} source=${meta.source} opId=${meta.opId}"
    }
}