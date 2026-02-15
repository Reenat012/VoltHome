package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DistributionDecision

/**
 * Сохранение результата AUTO-пересчёта в локальную БД.
 *
 * ВАЖНО:
 * - Это AUTO-путь (исторический): replace-by-delete+insert.
 * - Для manual Save использовать ЗАПРЕЩЕНО (manual делает diff-commit).
 * - Вынесено в domain/use_case, чтобы UI/VM не держали прямых вызовов replaceAllGroupsTransactional().
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
        Log.e(
            "AUTO_SAVE",
            "AUTO_SAVE execute pid=${params.projectId} groups=${params.groups.size}",
            Throwable("STACK")
        )
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

        // stacktrace-маркер: покажет, кто вызвал AUTO-save (обрезаем, чтобы не шумело)
        val caller = Throwable().stackTrace
            .drop(1)
            .take(8)
            .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }

        Log.w(
            "AUTO_SAVE",
            "AUTO_SAVE BEGIN projectId=$projectId groups=${groups.size} summary=[$summary] caller=$caller"
        )

        // 1) Сохраняем группы AUTO-результата (replace допустим только тут)
        explicationRepository.replaceAllGroupsTransactional(
            projectId = projectId,
            groups = groups
        )

        // 2) Обновляем decision log (in-memory)
        explicationRepository.setLastDistributionDecisions(params.distributionDecisions)

        Log.w("AUTO_SAVE", "AUTO_SAVE END projectId=$projectId groups=${groups.size}")
    }
}