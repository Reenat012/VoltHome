package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DistributionDecision

/**
 * Сохранение результата AUTO-пересчёта в локальную БД.
 *
 * ❗STRUCTURE writer:
 * - Это ЯВНЫЙ structural commit (rebuild).
 * - Реактивный AUTO-контур НЕ имеет права менять структуру.
 *
 * Коммит v3.1:
 * - Single-flight (Mutex) per projectId
 * - Инварианты до записи
 *
 * ✅ FIX (2026-02-24):
 * - Больше НЕ "нормализуем" groupId и НЕ продолжаем сохранять при невалидных id.
 * - Любые groupId<=0 или дубли groupId => hard-fail ДО записи в БД.
 *
 * ВАЖНО:
 * - manual режим НЕ трогаем: здесь мы сохраняем только AUTO результат в БД.
 */
class SaveAutoCalculatedGroupsToLocalDbUseCase @Inject constructor(
    private val explicationRepository: ExplicationRepository,
    private val structuralWriteMutex: ProjectStructuralWriteMutex, // ✅ single-flight
) {

    data class Params(
        val projectId: String,
        val groups: List<CircuitGroup>,
        val distributionDecisions: List<DistributionDecision>
    )

    suspend fun execute(params: Params) {
        require(params.projectId.isNotBlank()) { "projectId must be non-blank" }

        // ✅ Обычный вход: берём lock здесь
        structuralWriteMutex.withLock(params.projectId) {
            executeAlreadyLocked(params)
        }
    }

    /**
     * ⚠️ Вызывать ТОЛЬКО если ВНЕ уже есть structuralWriteMutex.withLock(projectId).
     * Иначе получишь гонки/перезаписи.
     */
    suspend fun executeAlreadyLocked(params: Params) {
        require(params.projectId.isNotBlank()) { "projectId must be non-blank" }

        val projectId = params.projectId
        val groups = params.groups

        // -------------------------------------------------------------------
        // ✅ HARD FAIL ДО инвариантов и ДО БД:
        // - groupId must be > 0
        // - groupId must be unique
        // Никаких warning+save, никаких "лечений" тут.
        // -------------------------------------------------------------------
        failIfInvalidAutoGroupIds(projectId = projectId, groups = groups)

        // -------------------------
        // ✅ Остальные инварианты (до БД)
        // -------------------------
        val dupNumbers = groups.groupBy { it.groupNumber }.filter { it.value.size > 1 }.keys
        require(dupNumbers.isEmpty()) {
            "AUTO_SAVE invariant failed: duplicate groupNumber(s)=$dupNumbers projectId=$projectId"
        }

        val nullPhase = groups.filter { it.phase == null }.map { it.groupId }
        require(nullPhase.isEmpty()) {
            "AUTO_SAVE invariant failed: null phase for groupId(s)=$nullPhase projectId=$projectId"
        }

        val summary = groups
            .sortedBy { it.groupNumber }
            .joinToString { g ->
                val ph = g.phase?.name ?: "null"
                "${g.groupId}#${g.groupNumber}#$ph(devs=${g.devices.size})"
            }

        val caller = Throwable().stackTrace
            .drop(1)
            .take(8)
            .joinToString(" <- ") {
                "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
            }

        Log.w(
            "AUTO_SAVE",
            "AUTO_SAVE BEGIN source=AUTO_SAVE reason=EXPLICIT_REBUILD projectId=$projectId " +
                    "groups=${groups.size} summary=[$summary] caller=$caller"
        )

        try {
            // ✅ ЕДИНСТВЕННАЯ structural запись (внутри неё DB-sanity)
            Log.w("AUTO_SAVE", "AUTO_SAVE BEFORE repo replace projectId=$projectId")
            explicationRepository.replaceAllGroupsTransactionalAlreadyLocked(projectId, groups)
            Log.w("AUTO_SAVE", "AUTO_SAVE AFTER repo replace projectId=$projectId")

            // decisions держим в памяти (не БД)
            explicationRepository.setLastDistributionDecisions(params.distributionDecisions)

            Log.w("AUTO_SAVE", "AUTO_SAVE END projectId=$projectId groups=${groups.size}")
        } catch (t: Throwable) {
            Log.e("AUTO_SAVE", "AUTO_SAVE FAILED projectId=$projectId groups=${groups.size}", t)
            throw t
        }
    }

    private fun failIfInvalidAutoGroupIds(projectId: String, groups: List<CircuitGroup>) {
        if (groups.isEmpty()) return

        // 1) id must be > 0
        val invalid = groups
            .asSequence()
            .filter { it.groupId <= 0L }
            .map { g -> "num=${g.groupNumber}:id=${g.groupId}" }
            .take(30)
            .toList()

        if (invalid.isNotEmpty()) {
            val caller = Throwable().stackTrace
                .drop(1)
                .take(10)
                .joinToString(" <- ") {
                    "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                }

            val sampleIds = groups.asSequence().map { it.groupId }.take(20).toList()

            throw IllegalStateException(
                "AUTO_SAVE hard-fail: non-positive groupId(s) detected projectId=$projectId " +
                        "invalid(sample)=$invalid ids(sample)=$sampleIds caller=$caller"
            )
        }

        // 2) id must be unique
        val dupIds = groups.groupBy { it.groupId }.filter { it.value.size > 1 }.keys
        if (dupIds.isNotEmpty()) {
            val sample = dupIds.take(30)
            throw IllegalStateException(
                "AUTO_SAVE hard-fail: duplicate groupId(s)=$sample projectId=$projectId"
            )
        }
    }

    @Deprecated(
        message = "Forbidden: AUTO must provide valid stable unique groupId(s). " +
                "Normalization hides bugs and is not allowed. Use hard-fail.",
        level = DeprecationLevel.ERROR
    )
    private fun normalizeAutoGroupIds(
        projectId: String,
        groups: List<CircuitGroup>
    ): List<CircuitGroup> = groups
}