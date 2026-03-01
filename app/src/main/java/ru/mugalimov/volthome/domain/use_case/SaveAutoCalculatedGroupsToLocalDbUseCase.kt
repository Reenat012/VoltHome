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
 * ✅ Commit B2 (2026-02-24):
 * - hard-fail остаётся, но становится "умным":
 *   1) сначала ensureValidAutoIds(): если groupId<=0 — детерминированно нормализуем и логируем ERROR
 *   2) затем failIfInvalidAutoGroupIds(): теперь ловит "невозможные" кейсы (дубли/неконсистентность после нормализации)
 *
 * ВАЖНО:
 * - Никаких silent warning+save
 * - Но и никаких падений ТОЛЬКО из-за groupId=0 (мы их поднимаем детерминированно)
 * - manual режим НЕ трогаем: здесь мы сохраняем только AUTO результат в БД.
 */
class SaveAutoCalculatedGroupsToLocalDbUseCase @Inject constructor(
    private val explicationRepository: ExplicationRepository,
    private val structuralWriteMutex: ProjectStructuralWriteMutex, // ✅ single-flight
    private val projectOwnershipRepository: ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository, // ✅ bastion lock SoT
) {

    data class Params(
        val projectId: String,
        val groups: List<CircuitGroup>,
        val distributionDecisions: List<DistributionDecision>,
        // ✅ Commit 4: корреляция источника и операции
        val source: String,
        val opId: String?
    )

    suspend fun execute(params: Params) {
        require(params.projectId.isNotBlank()) { "projectId must be non-blank" }
        require(params.source.isNotBlank()) { "source must be non-blank" }

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
        require(params.source.isNotBlank()) { "source must be non-blank" } // ✅ обязателен и тут

        val projectId = params.projectId.trim()
        val opId = params.opId
        val source = params.source.trim()
        val wanted = params.groups.size

        // -------------------------------------------------------------------
        // ✅ Commit 4 BASTION:
        // Самый первый шаг — жёсткое подавление AUTO_SAVE, если проект под manual lock.
        // Важно: после SUPPRESS здесь не должно быть вообще никаких "writer" логов.
        // -------------------------------------------------------------------
        val manualLock = projectOwnershipRepository.isManualLock(projectId)
        if (manualLock) {
            Log.w(
                "AUTO_SAVE",
                "AUTO_SAVE SUPPRESS manualLock=true pid=$projectId opId=$opId source=$source groupsWanted=$wanted"
            )
            return
        }

        // ↓↓↓ ниже — старый код, но теперь всегда гарантировано: lock=false
        val groups = ensureValidAutoIds(projectId = projectId, groups = params.groups)
        failIfInvalidAutoGroupIds(projectId = projectId, groups = groups)

        // ... остальные инварианты ...

        // -------------------------
        // ✅ Остальные инварианты (до БД)
        // -------------------------
        val dupNumbers = groups.groupBy { it.groupNumber }.filter { it.value.size > 1 }.keys
        require(dupNumbers.isEmpty()) {
            "AUTO_SAVE invariant failed: duplicate groupNumber(s)=$dupNumbers projectId=$projectId"
        }

        // -------------------------------------------------------------------
        // ✅ FIX: summary/caller обязаны быть объявлены ДО логов
        // -------------------------------------------------------------------

        // Короткая сводка: стабильный порядок по groupNumber.
        // Формат: "id#num#phase(devs=n)"
        val summary = groups
            .sortedBy { it.groupNumber }
            .joinToString { g ->
                val ph = g.phase.name
                "${g.groupId}#${g.groupNumber}#$ph(devs=${g.devices.size})"
            }

        // Короткий caller trace: чтобы видеть источник без 500 строк стектрейса
        val caller = Throwable().stackTrace
            .drop(1)
            .take(8)
            .joinToString(" <- ") {
                "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
            }

        Log.w(
            "AUTO_SAVE",
            "AUTO_SAVE BEGIN source=$source reason=EXPLICIT_REBUILD projectId=$projectId opId=$opId " +
                    "groups=${groups.size} summary=[$summary] caller=$caller"
        )

        try {
            Log.w("AUTO_SAVE", "AUTO_SAVE BEFORE repo replace projectId=$projectId opId=$opId source=$source")
            explicationRepository.replaceAllGroupsTransactionalAlreadyLocked(projectId, groups)
            Log.w("AUTO_SAVE", "AUTO_SAVE AFTER repo replace projectId=$projectId opId=$opId source=$source")

            explicationRepository.setLastDistributionDecisions(params.distributionDecisions)

            Log.w("AUTO_SAVE", "AUTO_SAVE END projectId=$projectId opId=$opId source=$source groups=${groups.size}")
        } catch (t: Throwable) {
            Log.e("AUTO_SAVE", "AUTO_SAVE FAILED projectId=$projectId opId=$opId source=$source groups=${groups.size}", t)
            throw t
        }
    }

    /**
     * ✅ B2: Поднимаем groupId<=0 детерминированно.
     *
     * Правила:
     * - если все groupId > 0 => возвращаем исходный список без изменений (no-op)
     * - иначе:
     *   - выдаём новые id строго > 0
     *   - детерминированно: зависит только от (projectId + stableKey группы)
     *   - при коллизиях: salt++ детерминированно
     *   - логируем ERROR, потому что это не "нормально", но мы не валим приложение
     *
     * Почему без падения:
     * - groupId=0 тут может прилететь при регрессии калькулятора/интеграции.
     * - падать на проде из-за этого — больно, лучше пережить и логом подсветить.
     */
    private fun ensureValidAutoIds(projectId: String, groups: List<CircuitGroup>): List<CircuitGroup> {
        if (groups.isEmpty()) return groups

        val hasInvalid = groups.any { it.groupId <= 0L }
        if (!hasInvalid) return groups // ✅ no-op

        // Логируем ERROR (не warning): это сигнал регрессии/дырки в расчёте.
        val invalidSample = groups.asSequence()
            .filter { it.groupId <= 0L }
            .map { "num=${it.groupNumber}:id=${it.groupId}" }
            .take(20)
            .toList()

        Log.e(
            "AUTO_SAVE",
            "AUTO_SAVE ERROR: invalid groupId(s) detected BEFORE save, will normalize deterministically " +
                    "projectId=$projectId invalid(sample)=$invalidSample totalInvalid=${groups.count { it.groupId <= 0L }}"
        )

        // Чтобы не получить дубли:
        // 1) сохраняем все уже валидные id
        // 2) генерим новые только для невалидных
        val used = HashSet<Long>(groups.size * 2)
        groups.asSequence().map { it.groupId }.filter { it > 0L }.forEach { used.add(it) }

        // Детерминированный ключ группы:
        // ⚠️ здесь мы НЕ используем groupNumber (он плавает).
        // Используем то, что максимально стабильно и уже есть в модели.
        fun stableKey(g: CircuitGroup): String {
            val deviceIds = g.devices
                .asSequence()
                .map { it.id } // ⚠️ предполагаем, что в доменной Device есть id
                .sorted()
                .joinToString(",")

            return buildString(256) {
                append("pid=").append(projectId)
                append("|roomId=").append(g.roomId)
                append("|groupType=").append(g.groupType.name)
                append("|devices=").append(deviceIds)
                append("|cb=").append(g.circuitBreaker)
                append("|cable=").append(g.cableSection)
                append("|rcd=").append(if (g.rcdRequired) 1 else 0)
                append("|rcdI=").append(g.rcdCurrent)
                append("|bt=").append(g.breakerType)
                append("|pW=").append(g.installedPowerW)
            }
        }

        // Генератор стабильного id на базе FNV-1a 64-bit.
// ⚠️ Важно: offset basis не влезает в signed Long literal, поэтому считаем в ULong.
        fun fnv1a64(input: String): ULong {
            var hash = 0xcbf29ce484222325uL // offset basis (ULong)
            val prime = 0x100000001b3uL     // FNV prime (ULong)

            for (ch in input) {
                hash = hash xor ch.code.toULong()
                hash *= prime
            }
            return hash
        }

        /**
         * Переводим ULong-хеш в Long > 0 детерминированно:
         * - режем до Long.MAX_VALUE (чтобы не получить отрицательное)
         * - 0 запрещён, заменяем на 1
         */
        fun toPositiveNonZero(x: ULong): Long {
            val p = (x and Long.MAX_VALUE.toULong()).toLong()
            return if (p == 0L) 1L else p
        }

        fun toPositiveNonZero(x: Long): Long {
            // Убираем отрицательность и 0. Не используем abs(Long.MIN_VALUE).
            val p = x and Long.MAX_VALUE
            return if (p == 0L) 1L else p
        }

        val out = ArrayList<CircuitGroup>(groups.size)

        for (g in groups) {
            if (g.groupId > 0L) {
                out.add(g)
                continue
            }

            val base = stableKey(g)
            var salt = 0
            var newId: Long

            while (true) {
                val key = if (salt == 0) base else "$base|salt=$salt"
                newId = toPositiveNonZero(fnv1a64(key))
                if (!used.contains(newId)) {
                    used.add(newId)
                    break
                }
                salt++
                // Защита от теоретического зацикливания (на практике не должно случиться).
                if (salt > 10_000) {
                    throw IllegalStateException(
                        "AUTO_SAVE hard-fail: cannot resolve groupId collision during normalization " +
                                "projectId=$projectId groupNumber=${g.groupNumber} roomId=${g.roomId} groupType=${g.groupType}"
                    )
                }
            }

            // Важно: меняем только groupId, остальное не трогаем.
            out.add(g.copy(groupId = newId))
        }

        return out
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
                "Normalization hides bugs and is not allowed. Use ensureValidAutoIds()+hard-fail.",
        level = DeprecationLevel.ERROR
    )
    private fun normalizeAutoGroupIds(
        projectId: String,
        groups: List<CircuitGroup>
    ): List<CircuitGroup> = groups
}