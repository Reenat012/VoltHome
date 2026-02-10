package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupComposition
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState

/**
 * Экран-независимая сборка baseState для ManualEditSession.
 *
 * Требования ТЗ v1.1:
 * - baseState строится НЕ из uiState конкретного экрана,
 * - источник истины: локальная БД (через репозитории),
 * - порядок должен быть детерминированным,
 * - nextGroupNumber должен быть вычислен стабильно.
 */
class ProjectBaseStateBuilder @Inject constructor(
    private val explicationRepository: ExplicationRepository
) {

    /**
     * Собрать baseState по projectId.
     * Никаких UI-данных, только снимок из репозитория.
     */
    suspend fun build(projectId: String): ProjectEditState {
        // Важно: берём группы с устройствами по конкретному projectId,
        // чтобы вход в manual был строго проектным, а не "что где сейчас отображается".
        val groupsWithDevices = explicationRepository.getGroupsWithDevicesByProject(projectId)

        // Детерминированный порядок групп:
        // 1) groupNumber
        // 2) groupId (на всякий случай для стабильности при одинаковых номерах)
        val sortedGroups = groupsWithDevices
            .sortedWith(compareBy({ it.group.groupNumber }, { it.group.groupId }))

        // Собираем уникальные устройства из всех групп.
        // В baseState это нужно для доменных операций (Move/AutoAssign и т.п.).
        val allDevices = sortedGroups
            .flatMap { it.devices }
            .distinctBy { it.id }
            .sortedBy { it.id } // детерминированно

        val manualDevices = allDevices.map { d ->
            ManualDeviceDraft(
                deviceId = d.id,
                roomId = d.roomId ?: 0L,
                deviceType = d.deviceType,
                powerW = d.power,
                voltageType = d.voltage.type,
                demandRatio = d.demandRatio,
                powerFactor = d.powerFactor,
                hasMotor = d.hasMotor,
                requiresDedicatedCircuit = d.requiresDedicatedCircuit
            )
        }

        val manualGroups = sortedGroups.map { gw ->
            val g = gw.group

            ManualGroupDraft(
                groupId = g.groupId,
                groupNumber = g.groupNumber,
                roomId = g.roomId,
                roomName = g.roomName,
                groupType = g.groupType,

                // На входе в manual считаем состав "нормальным".
                // Фактическая пометка MIXED_MANUAL выставляется доменными операциями.
                composition = ManualGroupComposition.NORMAL,

                // В доменной группе phase non-null, но если модель где-то допускает null —
                // защищаемся, чтобы manual не падал при входе.
                phase = g.phase ?: Phase.A,

                deviceIds = gw.devices.map { it.id },

                // Линия (может быть заполнена или нет — draft допускает nullable)
                nominalCurrent = g.nominalCurrent,
                circuitBreaker = g.circuitBreaker,
                cableSection = g.cableSection,
                breakerType = g.breakerType,
                rcdRequired = g.rcdRequired,
                rcdCurrent = g.rcdCurrent
            )
        }

        val nextGroupNumber = (sortedGroups.maxOfOrNull { it.group.groupNumber } ?: 0) + 1

        return ProjectEditState(
            projectId = projectId,
            groups = manualGroups,
            devices = manualDevices,
            // На входе в manual — пусто. "Нераспределённые" формируются только ручными операциями.
            unassignedDeviceIds = emptySet(),
            nextGroupNumber = nextGroupNumber
        )
    }
}