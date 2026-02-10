package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState

/**
 * Commit manual draft -> локальная БД.
 *
 * ВАЖНО:
 * - НЕ трогаем сервер/outbox.
 * - Пишем группы + joins транзакционно через ExplicationRepository.replaceAllGroupsTransactional(projectId,...)
 * - Для joins нужны реальные Device (хотя бы по id). Берём из DeviceRepository.
 *
 * КРИТИЧНО ДЛЯ "Save откатывает":
 * - В AUTO режиме фазы могут форситься через group_phase_overrides.
 * - При replaceAllGroupsTransactional() group_id может переиспользоваться (SQLite без AUTOINCREMENT),
 *   поэтому старые overrides могут снова "попасть" на новые группы.
 * => после Save мы обязаны очистить overrides по проекту.
 */
class CommitManualDraftToLocalDbUseCase @Inject constructor(
    private val explicationRepository: ExplicationRepository,
    private val deviceRepository: DeviceRepository,
    private val groupPhaseOverrideDao: GroupPhaseOverrideDao,
) {

    data class Params(
        val projectId: String,
        val draft: ProjectEditState
    )

    /**
     * @return доменные CircuitGroup, которые были закоммичены (удобно для обновления UI).
     */
    suspend fun execute(params: Params): List<CircuitGroup> {
        val projectId = params.projectId
        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        val draft = params.draft

        // Собираем реальные devicesById из БД.
        // Да, это N запросов, но на коммит ок: групп/устройств немного.
        val devicesById: Map<Long, Device> = buildMap {
            val allIds = draft.groups.flatMap { it.deviceIds }.distinct()
            for (id in allIds) {
                val d = deviceRepository.getDeviceById(id.toInt())
                if (d != null) put(id, d)
            }
        }

        // Маппим draft-группы -> доменные CircuitGroup.
        // IMPORTANT:
        // - installedPowerW обязателен в CircuitGroup => считаем по устройствам группы.
        // - nullable поля в draft нормализуем.
        val groups: List<CircuitGroup> = draft.groups
            .sortedBy { it.groupNumber }
            .map { g ->
                val devices = g.deviceIds.mapNotNull { id -> devicesById[id] }

                // Установленная мощность: суммарная мощность устройств.
                // Важно: в доменной модели Device.power = Int (non-null).
                val installedPowerW = devices.sumOf { it.power }

                CircuitGroup(
                    groupId = g.groupId,               // репо всё равно переинсертит с groupId=0
                    groupNumber = g.groupNumber,
                    roomName = g.roomName,
                    roomId = g.roomId,
                    groupType = g.groupType,
                    devices = devices,

                    // Нормализация nullable полей в draft:
                    nominalCurrent = g.nominalCurrent ?: 0.0,
                    installedPowerW = installedPowerW,
                    circuitBreaker = g.circuitBreaker ?: 16,
                    cableSection = g.cableSection ?: 2.5,
                    breakerType = g.breakerType ?: "C",

                    rcdRequired = g.rcdRequired ?: false,
                    rcdCurrent = g.rcdCurrent ?: 30,

                    // phase в draft non-null
                    phase = g.phase
                )
            }

        // 1) Транзакционно заменяем группы ТОЛЬКО этого проекта.
        explicationRepository.replaceAllGroupsTransactional(
            projectId = projectId,
            groups = groups
        )

        // 2) КРИТИЧНО: чистим overrides, иначе AUTO-ветка может "накрыть" сохранённые фазы.
        // Даже если у Entity стоит FK CASCADE, это не гарантирует отсутствие переиспользования group_id,
        // а также не гарантирует, что у пользователя не останутся overrides в таблице по проекту.
        groupPhaseOverrideDao.deleteByProject(projectId)

        return groups
    }
}