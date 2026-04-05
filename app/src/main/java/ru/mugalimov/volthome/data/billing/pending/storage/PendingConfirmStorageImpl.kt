package ru.mugalimov.volthome.data.billing.pending.storage

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.billing.pending.model.PendingConfirmRecord
import ru.mugalimov.volthome.data.billing.pending.model.PendingConfirmState
import ru.mugalimov.volthome.data.local.prefs.EncryptedPrefsProvider

@Singleton
class PendingConfirmStorageImpl @Inject constructor(
    private val encryptedPrefsProvider: EncryptedPrefsProvider
) : PendingConfirmStorage {

    companion object {
        private const val TAG = "PendingConfirmStorage"

        /** SharedPreferences key для всего payload. */
        private const val KEY_PENDING_CONFIRM_PAYLOAD = "billing_pending_confirm_payload"

        /** На случай невозможности восстановить битый payload — пишем synthetic invalid record. */
        private const val FALLBACK_UNKNOWN_USER = "__invalid__"
        private const val FALLBACK_UNKNOWN_VALUE = "__invalid__"
    }

    private val io = Dispatchers.IO
    private val gson = Gson()

    /**
     * Обёртка payload, чтобы storage был versioned.
     */
    private data class StoragePayload(
        val version: Int,
        val records: List<PendingConfirmRecord>
    )

    override suspend fun getAll(): List<PendingConfirmRecord> = withContext(io) {
        val prefs = prefs()
        readPayloadSafely(prefs).records
    }

    override suspend fun getByFlowId(flowId: String): PendingConfirmRecord? = withContext(io) {
        val prefs = prefs()
        readPayloadSafely(prefs).records.firstOrNull { it.purchaseFlowId == flowId }
    }

    override suspend fun upsert(record: PendingConfirmRecord): Unit = withContext(io) {
        val prefs = prefs()
        val payload = readPayloadSafely(prefs)

        val updated = payload.records
            .filterNot { it.purchaseFlowId == record.purchaseFlowId }
            .plus(record)
            .sortedBy { it.createdAt }

        writePayload(
            prefs = prefs,
            payload = StoragePayload(
                version = PendingConfirmRecord.CURRENT_STORAGE_VERSION,
                records = updated
            )
        )

        Log.d(
            TAG,
            "UPSERT flowId=${record.purchaseFlowId} state=${record.state} attempts=${record.attemptCount} terminal=${record.isTerminal}"
        )
    }

    override suspend fun removeByFlowId(flowId: String): Unit = withContext(io) {
        val prefs = prefs()
        val payload = readPayloadSafely(prefs)

        val updated = payload.records.filterNot { it.purchaseFlowId == flowId }

        writePayload(
            prefs = prefs,
            payload = StoragePayload(
                version = PendingConfirmRecord.CURRENT_STORAGE_VERSION,
                records = updated
            )
        )

        Log.d(TAG, "REMOVE flowId=$flowId")
    }

    override suspend fun removeByUserId(
        userId: String,
        includeUnknownUser: Boolean
    ): Unit = withContext(io) {
        val prefs = prefs()
        val payload = readPayloadSafely(prefs)

        val updated = payload.records.filterNot { record ->
            record.userId == userId ||
                    (includeUnknownUser && record.userId == FALLBACK_UNKNOWN_USER)
        }

        writePayload(
            prefs = prefs,
            payload = StoragePayload(
                version = PendingConfirmRecord.CURRENT_STORAGE_VERSION,
                records = updated
            )
        )

        Log.w(
            TAG,
            "REMOVE_BY_USER userId=$userId includeUnknownUser=$includeUnknownUser removed=${payload.records.size - updated.size}"
        )
    }

    override suspend fun clear(): Unit = withContext(io) {
        val prefs = prefs()
        prefs.edit().remove(KEY_PENDING_CONFIRM_PAYLOAD).commit()
        Log.w(TAG, "CLEAR_ALL")
    }

    private suspend fun prefs(): SharedPreferences = encryptedPrefsProvider.get()

    /**
     * Критично:
     * - unknown version -> ignore safely;
     * - corrupted payload -> log + terminal-invalid bucket;
     * - никогда не бросаем исключение наружу.
     */
    private fun readPayloadSafely(prefs: SharedPreferences): StoragePayload {
        val raw = prefs.getString(KEY_PENDING_CONFIRM_PAYLOAD, null)
            ?: return emptyPayload()

        return try {
            val parsed = gson.fromJson(raw, StoragePayload::class.java)
                ?: return emptyPayload()

            if (parsed.version != PendingConfirmRecord.CURRENT_STORAGE_VERSION) {
                Log.w(TAG, "UNKNOWN_VERSION version=${parsed.version}, ignoring payload safely")
                emptyPayload()
            } else {
                parsed.copy(records = parsed.records.orEmpty())
            }
        } catch (t: Throwable) {
            val errorCode = when (t) {
                is JsonSyntaxException -> "CORRUPTED_JSON"
                else -> "CORRUPTED_PAYLOAD"
            }

            Log.e(TAG, "READ_CORRUPTED_PAYLOAD errorCode=$errorCode", t)

            // 🔥 terminal-invalid bucket
            val invalidRecord = PendingConfirmRecord(
                purchaseFlowId = "invalid-${System.currentTimeMillis()}",
                userId = FALLBACK_UNKNOWN_USER,
                productId = FALLBACK_UNKNOWN_VALUE,
                orderId = FALLBACK_UNKNOWN_VALUE,
                purchaseToken = FALLBACK_UNKNOWN_VALUE,
                createdAt = System.currentTimeMillis(),
                lastAttemptAt = System.currentTimeMillis(),
                attemptCount = 0,
                state = PendingConfirmState.TERMINAL_INVALID,
                lastErrorCode = errorCode,
                appVersion = "unknown",
                isTerminal = true
            )

            val repaired = StoragePayload(
                version = PendingConfirmRecord.CURRENT_STORAGE_VERSION,
                records = listOf(invalidRecord)
            )

            runCatching { writePayload(prefs, repaired) }
                .onFailure { Log.e(TAG, "FAILED_TO_WRITE_INVALID_BUCKET", it) }

            repaired
        }
    }

    private fun writePayload(
        prefs: SharedPreferences,
        payload: StoragePayload
    ) {
        val json = gson.toJson(payload)
        prefs.edit()
            .putString(KEY_PENDING_CONFIRM_PAYLOAD, json)
            .commit()
    }

    private fun emptyPayload(): StoragePayload {
        return StoragePayload(
            version = PendingConfirmRecord.CURRENT_STORAGE_VERSION,
            records = emptyList()
        )
    }
}