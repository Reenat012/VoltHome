package ru.mugalimov.volthome.domain.use_case.manual

import android.content.ContentValues.TAG
import android.util.Log
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.resolve
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualGroupComposition
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import ru.mugalimov.volthome.domain.policy.protection.RcdSelectionReason

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
    private val explicationRepository: ExplicationRepository,
    private val roomRepository: RoomRepository,
    private val preferencesRepository: PreferencesRepository,
    private val projectSetupRepository: ProjectSetupRepository
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
        val rooms = roomRepository.getRoomsWithDevicesByProject(projectId)
        val roomTypesById = rooms.associate { it.room.id to it.room.roomType }
        val roomNamesFromRooms = rooms.associate { it.room.id to it.room.name }
        val phaseMode = projectSetupRepository.resolve(
            projectId = projectId,
            legacyFallbackPhaseMode = preferencesRepository.phaseMode.first()
        ).phaseMode

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
        val roomNamesById: Map<Long, String> = roomNamesFromRooms + sortedGroups
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
                    roomType = roomTypesById[roomId] ?: ru.mugalimov.volthome.domain.model.RoomType.STANDARD,
                    deviceType = d.deviceType,
                    powerW = d.power,
                    voltageType = d.voltage.type,
                    voltageValue = d.voltage.value,
                    demandRatio = d.demandRatio,
                    powerFactor = d.powerFactor,
                    hasMotor = d.hasMotor,
                    requiresDedicatedCircuit = d.requiresDedicatedCircuit,
                    requiresSocketConnection = d.requiresSocketConnection
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
                roomType = roomTypesById[g.roomId] ?: ru.mugalimov.volthome.domain.model.RoomType.STANDARD,
                groupType = g.groupType,

                composition = ManualGroupComposition.NORMAL,

                phase = g.phase ?: Phase.A,

                deviceIds = gw.devices.map { it.id },

                nominalCurrent = g.nominalCurrent,
                circuitBreaker = g.circuitBreaker,
                cableSection = g.cableSection,
                breakerType = g.breakerType,
                rcdRequired = g.rcdRequired,
                rcdCurrent = g.rcdCurrent,
                rcdSpec = g.rcdSpec,
                deviationCodes = g.manualDeviationCodes,
                rcdReasons = g.rcdReasonCodes.mapNotNull { code ->
                    runCatching { RcdSelectionReason.valueOf(code) }.getOrNull()
                }
            )
        }

        val nextGroupNumber = (sortedGroups.maxOfOrNull { it.group.groupNumber } ?: 0) + 1

        val state = ProjectEditState(
            projectId = projectId,
            groups = manualGroups,
            devices = manualDevices,
            // ✅ ключевой фикс: на входе в manual unassigned вычисляются из БД
            unassignedDeviceIds = unassignedDeviceIds,
            nextGroupNumber = nextGroupNumber,
            phaseMode = phaseMode,
            roomTypesById = roomTypesById
        )

        Log.d(
            TAG,
            "built: groups=${state.groups.size} devices=${state.devices.size} unassigned=${state.unassignedDeviceIds.size} nextGroupNumber=${state.nextGroupNumber}"
        )

        return state
    }
}
