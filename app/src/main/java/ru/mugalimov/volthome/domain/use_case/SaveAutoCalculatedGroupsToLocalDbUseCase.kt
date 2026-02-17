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
 */
class SaveAutoCalculatedGroupsToLocalDbUseCase @Inject constructor(
    private val explicationRepository: ExplicationRepository
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

        // Сводка: groupId#num#phase#devCount
        val summary = groups
            .sortedBy { it.groupNumber }
            .joinToString { g ->
                val ph = g.phase?.name ?: "null"
                "${g.groupId}#${g.groupNumber}#$ph(devs=${g.devices.size})"
            }

        // stacktrace-маркер: кто инициирует STRUCTURE write
        val caller = Throwable().stackTrace
            .drop(1)
            .take(8)
            .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }

        Log.w(
            "AUTO_SAVE",
            "AUTO_SAVE BEGIN source=AUTO_SAVE reason=EXPLICIT_REBUILD projectId=$projectId " +
                    "groups=${groups.size} summary=[$summary] caller=$caller"
        )

        // ✅ ЕДИНСТВЕННАЯ структурная запись
        explicationRepository.replaceAllGroupsTransactional(
            projectId = projectId,
            groups = groups
        )

        // Decisions (in-memory)
        explicationRepository.setLastDistributionDecisions(params.distributionDecisions)

        Log.w("AUTO_SAVE", "AUTO_SAVE END projectId=$projectId groups=${groups.size}")
    }
}