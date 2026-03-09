package ru.mugalimov.volthome.domain.use_case.manual

import android.content.ContentValues.TAG
import android.util.Log
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
 * - nextGroupNumber должен быть вычислен стабильно,
 * - ✅ после MANUAL_SAVE устройства без join обязаны появляться в "Нераспределённые".
 */
class ProjectBaseStateBuilder @Inject constructor(
    private val explicationRepository: ExplicationRepository
) {

    /**
     * Собрать baseState по projectId.
     * Никаких UI-данных, только снимок из репозитория.
     */
    suspend fun build(projectId: String): ProjectEditState {
        Log.d(TAG, "build() projectId=$projectId")

        // 1) Группы + устройства по membership (join)
        val groupsWithDevices = explicationRepository.getGroupsWithDevicesByProject(projectId)
        Log.d(TAG, "db snapshot: groups=${groupsWithDevices.size}")

        groupsWithDevices.take(10).forEach { gw ->
            Log.d(
                TAG,
                "g id=${gw.group.groupId} num=${gw.group.groupNumber} roomId=${gw.group.roomId} devs=${gw.devices.size}"
            )
        }

        // 2) ВСЕ устройства проекта (не через join!)
        val allDevicesInProject = explicationRepository.getAllDevicesByProject(projectId)
        Log.d(TAG, "db snapshot: allDevices=${allDevicesInProject.size}")

        // 3) Детерминированный порядок групп:
        val sortedGroups = groupsWithDevices
            .sortedWith(compareBy({ it.group.groupNumber }, { it.group.groupId }))

        // 4) assigned/unassigned
        val assignedDeviceIds = sortedGroups
            .asSequence()
            .flatMap { it.devices.asSequence() }
            .map { it.id }
            .toSet()

        val allDeviceIds = allDevicesInProject
            .asSequence()
            .map { it.id }
            .toSet()

        val unassignedDeviceIds = (allDeviceIds - assignedDeviceIds)

        Log.d(
            TAG,
            "baseState devices: all=${allDeviceIds.size} assigned=${assignedDeviceIds.size} unassigned=${unassignedDeviceIds.size}"
        )

        // ✅ НОВОЕ:
        // Делаем карту roomId -> roomName из уже известных групп.
        // Это основной способ нормализовать устройства в manual draft.
        val roomNamesById: Map<Long, String> = sortedGroups
            .map { it.group }
            .filter { it.roomId > 0L && it.roomName.isNotBlank() }
            .associate { it.roomId to it.roomName }

        // 5) devices в manual state — из ВСЕХ устройств проекта
        // (иначе unassigned не сможет материализоваться в UI)
        val manualDevices = allDevicesInProject
            .distinctBy { it.id }
            .sortedBy { it.id }
            .map { d ->
                val roomId = d.roomId ?: 0L

                require(roomId > 0L) {
                    "MANUAL_BASE_STATE: deviceId=${d.id} has invalid roomId=$roomId"
                }

                val roomName = roomNamesById[roomId]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Помещение #$roomId"

                ManualDeviceDraft(
                    deviceId = d.id,
                    roomId = roomId,
                    roomName = roomName,
                    deviceType = d.deviceType,
                    powerW = d.power,
                    voltageType = d.voltage.type,
                    demandRatio = d.demandRatio,
                    powerFactor = d.powerFactor,
                    hasMotor = d.hasMotor,
                    requiresDedicatedCircuit = d.requiresDedicatedCircuit
                )
            }

        // 6) groups draft — как раньше (membership в deviceIds внутри групп)
        val manualGroups = sortedGroups.map { gw ->
            val g = gw.group

            ManualGroupDraft(
                groupId = g.groupId,
                groupNumber = g.groupNumber,
                roomId = g.roomId,
                roomName = g.roomName,
                groupType = g.groupType,

                composition = ManualGroupComposition.NORMAL,

                phase = g.phase ?: Phase.A,

                deviceIds = gw.devices.map { it.id },

                nominalCurrent = g.nominalCurrent,
                circuitBreaker = g.circuitBreaker,
                cableSection = g.cableSection,
                breakerType = g.breakerType,
                rcdRequired = g.rcdRequired,
                rcdCurrent = g.rcdCurrent
            )
        }

        val nextGroupNumber = (sortedGroups.maxOfOrNull { it.group.groupNumber } ?: 0) + 1

        val state = ProjectEditState(
            projectId = projectId,
            groups = manualGroups,
            devices = manualDevices,
            // ✅ ключевой фикс: на входе в manual unassigned вычисляются из БД
            unassignedDeviceIds = unassignedDeviceIds,
            nextGroupNumber = nextGroupNumber
        )

        Log.d(
            TAG,
            "built: groups=${state.groups.size} devices=${state.devices.size} unassigned=${state.unassignedDeviceIds.size} nextGroupNumber=${state.nextGroupNumber}"
        )

        return state
    }
}