package ru.mugalimov.volthome.core.logging

import android.util.Log
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/**
 * Единый логгер для записей групп/joins.
 *
 * Зачем:
 * - ловим "кто писал структуру" (GROUP_STRUCTURE_WRITE)
 * - ловим "кто писал параметры" (GROUP_PARAMETER_WRITE)
 * - фиксируем "последняя запись" в памяти (для быстрого дебага)
 *
 * ВАЖНО:
 * - Fingerprint — НЕ доказательство, а детектор изменений.
 * - В fingerprint мы включаем структуру joins + структурные поля groups.
 */
object GroupWriteLogger {

    // ===== Каналы логов (строки для grep) =====
    const val TAG_STRUCTURE = "GROUP_STRUCTURE_WRITE"
    const val TAG_PARAMS = "GROUP_PARAMETER_WRITE"

    // ===== Источник записи (source) =====
    enum class Source {
        AUTO_SAVE,
        MANUAL_SAVE,
        MANUAL_DIFF,
        DEVICE_DELETE,
        ROOM_DELETE,
        UPDATE_GROUP,
        UNKNOWN
    }

    // ===== Причина записи (reason) =====
    enum class Reason {
        EXPLICIT_REBUILD,
        DIFF_COMMIT,
        DEVICE_CHANGED,
        JOIN_CHANGED,
        USER_ACTION,
        CLEANUP,
        UNKNOWN
    }

    data class Fingerprint(
        val joinsFp: String,
        val groupsFp: String
    )

    data class LastWrite(
        val tag: String,
        val source: Source,
        val reason: Reason,
        val projectId: String,
        val groupsCount: Int,
        val joinsCount: Int,
        val fpBefore: Fingerprint?,
        val fpAfter: Fingerprint?,
        val sample: String,
        val thread: String,
        val atMs: Long
    )

    private val lastWriteRef = AtomicReference<LastWrite?>(null)

    fun getLastWrite(): LastWrite? = lastWriteRef.get()

    // -------------------- PUBLIC API --------------------

    fun logStructureWrite(
        source: Source,
        reason: Reason,
        projectId: String,
        groupsCount: Int,
        joinsCount: Int,
        before: Fingerprint?,
        after: Fingerprint?,
        sample: String,
        throwable: Throwable? = null
    ) {
        val msg = buildString {
            append("source=").append(source.name)
            append(" reason=").append(reason.name)
            append(" pid=").append(projectId)
            append(" groups=").append(groupsCount)
            append(" joins=").append(joinsCount)
            if (before != null) append(" before=").append(before.asShort())
            if (after != null) append(" after=").append(after.asShort())
            if (sample.isNotBlank()) append(" sample=").append(sample)
        }

        if (throwable != null) {
            Log.e(TAG_STRUCTURE, msg, throwable)
        } else {
            Log.w(TAG_STRUCTURE, msg)
        }

        lastWriteRef.set(
            LastWrite(
                tag = TAG_STRUCTURE,
                source = source,
                reason = reason,
                projectId = projectId,
                groupsCount = groupsCount,
                joinsCount = joinsCount,
                fpBefore = before,
                fpAfter = after,
                sample = sample,
                thread = Thread.currentThread().name,
                atMs = System.currentTimeMillis()
            )
        )
    }

    fun logParameterWrite(
        source: Source,
        reason: Reason,
        projectId: String,
        groupsCount: Int,
        sample: String,
        throwable: Throwable? = null
    ) {
        val msg = buildString {
            append("source=").append(source.name)
            append(" reason=").append(reason.name)
            append(" pid=").append(projectId)
            append(" groups=").append(groupsCount)
            if (sample.isNotBlank()) append(" sample=").append(sample)
        }

        if (throwable != null) {
            Log.e(TAG_PARAMS, msg, throwable)
        } else {
            Log.w(TAG_PARAMS, msg)
        }

        lastWriteRef.set(
            LastWrite(
                tag = TAG_PARAMS,
                source = source,
                reason = reason,
                projectId = projectId,
                groupsCount = groupsCount,
                joinsCount = 0,
                fpBefore = null,
                fpAfter = null,
                sample = sample,
                thread = Thread.currentThread().name,
                atMs = System.currentTimeMillis()
            )
        )
    }

    /**
     * Fingerprint joins: список (groupId, deviceId) отсортированный.
     */
    fun fingerprintJoins(pairs: List<Pair<Long, Long>>): String {
        val normalized = pairs
            .sortedWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
            .joinToString(separator = "|") { "${it.first}:${it.second}" }

        return sha256Short(normalized)
    }

    /**
     * Fingerprint групп по структурным полям.
     * Передавать сюда только реально существующие поля (см. caller).
     */
    fun fingerprintGroups(structRows: List<String>): String {
        val normalized = structRows.sorted().joinToString(separator = "|")
        return sha256Short(normalized)
    }

    fun fingerprint(joinsPairs: List<Pair<Long, Long>>, groupStructRows: List<String>): Fingerprint {
        return Fingerprint(
            joinsFp = fingerprintJoins(joinsPairs),
            groupsFp = fingerprintGroups(groupStructRows)
        )
    }

    // -------------------- HELPERS --------------------

    private fun Fingerprint.asShort(): String = "{j=${joinsFp}, g=${groupsFp}}"

    private fun sha256Short(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        // Берём первые 8 байт -> 16 hex символов. Этого достаточно для логов.
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Очень практичная эвристика для source, если явно не передали.
     * Не идеальна, но уже лучше "UNKNOWN" в вакууме.
     */
    fun detectSourceFromCaller(caller: String): Source {
        return when {
            caller.contains("SaveAutoCalculatedGroupsToLocalDbUseCase") -> Source.AUTO_SAVE
            caller.contains("commitManualDraftTransactional") -> Source.MANUAL_SAVE
            caller.contains("handleDeviceDeletion") -> Source.DEVICE_DELETE
            caller.contains("handleRoomDeletion") -> Source.ROOM_DELETE
            caller.contains("updateGroup(") || caller.contains("updateGroup:") -> Source.UPDATE_GROUP
            else -> Source.UNKNOWN
        }
    }

    fun shortCallerTrace(skip: Int = 1, take: Int = 6): String {
        return Throwable().stackTrace
            .drop(skip)
            .take(take)
            .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
    }
}