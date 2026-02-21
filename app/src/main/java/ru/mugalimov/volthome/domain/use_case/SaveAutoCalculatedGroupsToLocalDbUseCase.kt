package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DistributionDecision

/**
 * Сохранение результата AUTO-пересчёта в локальную БД.
 *
 * ❗STRUCTURE writer:
 * - Это ЯВНЫЙ structural commit (rebuild) по кнопке/сценарию.
 * - Реактивный AUTO-контур НЕ имеет права менять структуру.
 *
 * Коммит v3.1:
 * - Single-flight (Mutex) per projectId
 * - Инварианты до записи
 *
 * ✅ FIX (2026-02-21):
 * - Калькулятор может вернуть группы с groupId=0 (плейсхолдер).
 * - Для БД это недопустимо: groupId используется как стабильная идентичность.
 * - Поэтому перед записью нормализуем id:
 *   - groupId > 0
 *   - уникален внутри набора
 *   - детерминированный (чтобы не плодить "свалку" случайных id)
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

        // -------------------------------------------------------------------
        // ✅ Нормализация id ДО инвариантов:
        // - лечим groupId=0 и любые дубли groupId
        // - делаем id детерминированным от projectId + полей группы
        // -------------------------------------------------------------------
        val groups = normalizeAutoGroupIds(
            projectId = projectId,
            groups = params.groups
        )

        // -------------------------
        // ✅ Быстрые инварианты (до БД)
        // -------------------------
        val dupGroupIds = groups.groupBy { it.groupId }.filter { it.value.size > 1 }.keys
        require(dupGroupIds.isEmpty()) {
            "AUTO_SAVE invariant failed: duplicate groupId(s)=$dupGroupIds projectId=$projectId"
        }

        val invalidIds = groups.filter { it.groupId <= 0L }.map { it.groupNumber to it.groupId }
        require(invalidIds.isEmpty()) {
            "AUTO_SAVE invariant failed: non-positive groupId(s)=$invalidIds projectId=$projectId"
        }

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
            .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }

        Log.w(
            "AUTO_SAVE",
            "AUTO_SAVE BEGIN source=AUTO_SAVE reason=EXPLICIT_REBUILD projectId=$projectId " +
                    "groups=${groups.size} summary=[$summary] caller=$caller"
        )

        // ✅ ЕДИНСТВЕННАЯ structural запись (внутри неё DB-sanity)
        Log.w("AUTO_SAVE", "AUTO_SAVE BEFORE repo replace projectId=$projectId")
        explicationRepository.replaceAllGroupsTransactionalAlreadyLocked(projectId, groups)
        Log.w("AUTO_SAVE", "AUTO_SAVE AFTER repo replace projectId=$projectId")

        explicationRepository.setLastDistributionDecisions(params.distributionDecisions)

        Log.w("AUTO_SAVE", "AUTO_SAVE END projectId=$projectId groups=${groups.size}")

        Log.w(
            "AUTO_SAVE",
            "AUTO_SAVE BEGIN source=AUTO_SAVE reason=EXPLICIT_REBUILD projectId=$projectId " +
                    "groups=${groups.size} summary=[$summary] caller=$caller"
        )

        try {
            explicationRepository.replaceAllGroupsTransactionalAlreadyLocked(
                projectId = projectId,
                groups = groups
            )

            explicationRepository.setLastDistributionDecisions(params.distributionDecisions)

            Log.w("AUTO_SAVE", "AUTO_SAVE END projectId=$projectId groups=${groups.size}")
        } catch (t: Throwable) {
            Log.e("AUTO_SAVE", "AUTO_SAVE FAILED projectId=$projectId groups=${groups.size}", t)
            throw t
        }
    }

    /**
     * Нормализует groupId для AUTO-результата.
     *
     * Правило:
     * - если ВСЁ ок (все id > 0 и нет дублей) — возвращаем как есть.
     * - иначе:
     *   - присваиваем детерминированные id по ключу группы
     *   - разрешаем коллизии (маловероятно) через suffix-соль
     *
     * Почему детерминированно:
     * - чтобы не плодить "рандомные" id и не превращать БД в свалку
     * - чтобы одинаковая структура в рамках проекта давала одинаковые id
     */
    private fun normalizeAutoGroupIds(
        projectId: String,
        groups: List<CircuitGroup>
    ): List<CircuitGroup> {
        if (groups.isEmpty()) return groups

        val hasNonPositive = groups.any { it.groupId <= 0L }
        val dup = groups.groupBy { it.groupId }.any { (_, v) -> v.size > 1 }
        if (!hasNonPositive && !dup) return groups

        Log.w(
            "AUTO_SAVE",
            "normalizeAutoGroupIds: detected invalid ids. " +
                    "hasNonPositive=$hasNonPositive dup=$dup sample=${groups.map { it.groupId }.take(12)}"
        )

        val used = HashSet<Long>(groups.size * 2)

        fun groupStableKey(g: CircuitGroup): String {
            return buildString {
                append("pid=").append(projectId)
                append("|num=").append(g.groupNumber)
                append("|roomId=").append(g.roomId)

                // roomName может быть String — trim не обязателен
                append("|roomName=").append(g.roomName)

                // groupType — скорее всего enum DeviceType
                append("|type=").append(g.groupType.name)

                append("|phase=").append(g.phase?.name ?: "null")
            }
        }
        return groups.map { g ->
            val idOk = g.groupId > 0L && used.add(g.groupId)
            if (idOk) return@map g

            val baseKey = groupStableKey(g)

            // Пытаемся несколько раз на случай коллизии (крайне маловероятно, но мы не играем в рулетку).
            var attempt = 0
            var newId: Long
            do {
                val salted = if (attempt == 0) baseKey else "$baseKey|salt=$attempt"
                newId = stablePositiveLong(salted)

                attempt++
                // защита от бесконечного цикла: в реальности не понадобится, но пусть будет.
                if (attempt > 1000) {
                    throw IllegalStateException("AUTO_SAVE failed to allocate unique groupId for key=$baseKey")
                }
            } while (!used.add(newId))

            // ВАЖНО: копируем только groupId, остальное не трогаем.
            g.copy(groupId = newId)
        }
    }

    /**
     * Детерминированный Long > 0 на основе строки.
     * Используем SHA-256 и берём первые 8 байт.
     */
    private fun stablePositiveLong(input: String): Long {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(input.toByteArray(Charsets.UTF_8))

        val value = ByteBuffer.wrap(hash, 0, 8).long
        // делаем строго положительным и не нулём
        val positive = value and Long.MAX_VALUE
        return if (positive == 0L) 1L else positive
    }
}