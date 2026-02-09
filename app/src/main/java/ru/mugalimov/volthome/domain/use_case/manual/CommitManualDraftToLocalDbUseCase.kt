package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
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
 * - Пишем группы + joins транзакционно через ExplicationRepository.replaceAllGroupsTransactional(...)
 * - Для joins нужны реальные Device (хотя бы по id). Берём из DeviceRepository.
 *
 * Проблема nullability:
 * - Draft-модель может содержать nullable поля (после ручных операций / новых групп).
 * - CircuitGroup в домене требует non-null.
 * Поэтому здесь делаем нормализацию null -> безопасные дефолты.
 */
class CommitManualDraftToLocalDbUseCase @Inject constructor(
    private val explicationRepository: ExplicationRepository,
    private val deviceRepository: DeviceRepository,
) {

    data class Params(
        val draft: ProjectEditState
    )

    /**
     * @return доменные CircuitGroup, которые были закоммичены (удобно для обновления UI).
     */
    suspend fun execute(params: Params): List<CircuitGroup> {
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
                // Если где-то power == null -> считаем как 0.
                val installedPowerW = devices.sumOf { it.power ?: 0 }

                // Нормализация nullable полей в draft:
                // - nominalCurrent: если null -> 0.0 (чтобы не падало)
                // - circuitBreaker: если null -> 16 (дефолт, как у тебя в newGroup)
                // - cableSection: если null -> 2.5 (дефолт)
                // - breakerType: если null -> "C" (самый частый, но можешь поменять)
                // - rcdRequired: если null -> false
                // - rcdCurrent: если null -> 30 (у тебя в CircuitGroup дефолт 30)
                CircuitGroup(
                    groupId = g.groupId,               // репо всё равно переинсертит с groupId=0
                    groupNumber = g.groupNumber,
                    roomName = g.roomName,             // в CircuitGroup non-null String
                    roomId = g.roomId,
                    groupType = g.groupType,
                    devices = devices,

                    nominalCurrent = g.nominalCurrent ?: 0.0,
                    installedPowerW = installedPowerW,
                    circuitBreaker = g.circuitBreaker ?: 16,
                    cableSection = g.cableSection ?: 2.5,
                    breakerType = g.breakerType ?: "C",

                    rcdRequired = g.rcdRequired ?: false,
                    rcdCurrent = g.rcdCurrent ?: 30,

                    phase = g.phase                    // phase в CircuitGroup non-null, в draft тоже должен быть задан
                )
            }

        // Транзакционно заменяем группы проекта.
        explicationRepository.replaceAllGroupsTransactional(groups)

        return groups
    }
}