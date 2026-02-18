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
 * - Это ЯВНЫЙ structural commit (rebuild) по кнопке/сценарию.
 * - Реактивный AUTO-контур НЕ имеет права менять структуру.
 *
 * Коммит v3.1:
 * - Single-flight (Mutex) per projectId
 * - Инварианты до записи
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

        val projectId = params.projectId
        val groups = params.groups

        structuralWriteMutex.withLock(projectId) {
            // -------------------------
            // ✅ Быстрые инварианты (до БД)
            // -------------------------
            val dupGroupIds = groups.groupBy { it.groupId }.filter { it.value.size > 1 }.keys
            require(dupGroupIds.isEmpty()) {
                "AUTO_SAVE invariant failed: duplicate groupId(s)=$dupGroupIds projectId=$projectId"
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

            // ✅ ЕДИНСТВЕННАЯ structural запись (внутри неё теперь будет DB-sanity)
            explicationRepository.replaceAllGroupsTransactional(
                projectId = projectId,
                groups = groups
            )

            explicationRepository.setLastDistributionDecisions(params.distributionDecisions)

            Log.w("AUTO_SAVE", "AUTO_SAVE END projectId=$projectId groups=${groups.size}")
        }
    }
}