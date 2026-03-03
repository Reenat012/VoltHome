package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.local.dao.DeviceDao
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
    private val deviceDao: DeviceDao, // ✅ NEW: инвариант "не сохранять пустые devices при наличии устройств"
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

        // -------------------------------------------------------------------
        // ✅ NEW INVARIANT:
        // AUTO_SAVE запрещён, если groups содержат пустые devices, но в проекте есть "живые" устройства.
        //
        // Почему это критично:
        // replaceAllGroupsTransactionalAlreadyLocked() сначала удаляет старые joins,
        // затем вставляет новые joins на основе g.devices. Если devices пустые — joins не вставятся,
        // и ты получишь "groups>0 & devices=0" (классическая поломка).
        // -------------------------------------------------------------------
        val activeDeviceCount = deviceDao.countActiveByProjectId(projectId)
        val emptyDeviceGroups = groups.count { it.devices.isEmpty() }

        if (activeDeviceCount > 0 && groups.isNotEmpty() && emptyDeviceGroups > 0) {
            val sample = groups.asSequence()
                .filter { it.devices.isEmpty() }
                .take(5)
                .joinToString { g ->
                    "gid=${g.groupId} num=${g.groupNumber} roomId=${g.roomId} type=${g.groupType} phase=${g.phase.name}"
                }

            val msg =
                "AUTO_SAVE invariant failed: groups contain empty devices while project has active devices. " +
                        "pid=$projectId activeDevices=$activeDeviceCount groups=${groups.size} emptyGroups=$emptyDeviceGroups " +
                        "source=$source opId=$opId sample=[$sample]"

            Log.e("AUTO_SAVE", msg, Throwable("STACK"))
            throw IllegalStateException(msg)
        }

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

        val summary = groups
            .sortedBy { it.groupNumber }
            .joinToString { g ->
                val ph = g.phase.name
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

    // --- остальной код без изменений (ensureValidAutoIds / failIfInvalidAutoGroupIds / normalizeAutoGroupIds) ---

    private fun ensureValidAutoIds(projectId: String, groups: List<CircuitGroup>): List<CircuitGroup> {
        if (groups.isEmpty()) return groups

        val hasInvalid = groups.any { it.groupId <= 0L }
        if (!hasInvalid) return groups // ✅ no-op

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

        val used = HashSet<Long>(groups.size * 2)
        groups.asSequence().map { it.groupId }.filter { it > 0L }.forEach { used.add(it) }

        fun stableKey(g: CircuitGroup): String {
            val deviceIds = g.devices
                .asSequence()
                .map { it.id }
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

        fun fnv1a64(input: String): ULong {
            var hash = 0xcbf29ce484222325uL
            val prime = 0x100000001b3uL
            for (ch in input) {
                hash = hash xor ch.code.toULong()
                hash *= prime
            }
            return hash
        }

        fun toPositiveNonZero(x: ULong): Long {
            val p = (x and Long.MAX_VALUE.toULong()).toLong()
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
                if (salt > 10_000) {
                    throw IllegalStateException(
                        "AUTO_SAVE hard-fail: cannot resolve groupId collision during normalization " +
                                "projectId=$projectId groupNumber=${g.groupNumber} roomId=${g.roomId} groupType=${g.groupType}"
                    )
                }
            }

            out.add(g.copy(groupId = newId))
        }

        return out
    }

    private fun failIfInvalidAutoGroupIds(projectId: String, groups: List<CircuitGroup>) {
        if (groups.isEmpty()) return

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