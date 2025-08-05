import android.content.ContentValues.TAG
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
import ru.mugalimov.volthome.data.local.entity.DeviceEntity
import ru.mugalimov.volthome.data.local.entity.RoomEntity
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.ElectricalSystem
import ru.mugalimov.volthome.domain.model.GroupProfile
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.SafetyProfile
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator
import ru.mugalimov.volthome.domain.usecase.distributeGroupsBalanced
import javax.inject.Inject
import kotlin.math.ceil


class GroupCalculator(
    private val roomRepository: RoomRepository,
    private val groupRepository: ExplicationRepository
) {

    private val roomSafetyProfiles = mapOf(
        RoomType.BATHROOM to SafetyProfile(rcdRequired = true),
        RoomType.KITCHEN to SafetyProfile(rcdRequired = true),
        RoomType.OUTDOOR to SafetyProfile(rcdRequired = true),
        RoomType.STANDARD to SafetyProfile(rcdRequired = false)
    )

    suspend fun calculateGroups(): GroupingResult {
        return try {
            val rooms = roomRepository.getRoomsWithDevices()
            var totalGroupNumber = 1
            val allGroups = mutableListOf<CircuitGroup>()

            rooms.forEach { roomWithDevices ->
                val room = roomWithDevices.room
                val safetyProfile = roomSafetyProfiles[room.roomType] ?: SafetyProfile()

                roomWithDevices.devices
                    .filter { it.requiresDedicatedCircuit || it.power > 2000 }
                    .forEach { device ->
                        val nominalCurrent = device.nominalCurrent()
                        val profile = selectBreaker(nominalCurrent, device.deviceType, device.hasMotor)
                        allGroups.add(
                            createDedicatedGroup(
                                device = device,
                                profile = profile,
                                safetyProfile = safetyProfile,
                                groupNumber = totalGroupNumber++,
                                room = room
                            )
                        )
                    }
            }

            rooms.forEach { roomWithDevices ->
                val room = roomWithDevices.room
                val safetyProfile = roomSafetyProfiles[room.roomType] ?: SafetyProfile()
                val devices = roomWithDevices.devices.filterNot { it.requiresDedicatedCircuit || it.power > 2000 }
                val groupedDevices = devices.groupBy { it.deviceType }

                groupedDevices.forEach { (deviceType, typeDevices) ->
                    val maxCurrent = typeDevices.maxOfOrNull { it.nominalCurrent() } ?: 0.0
                    val hasMotor = typeDevices.any { it.hasMotor }
                    val profile = selectBreaker(maxCurrent, deviceType, hasMotor)

                    val groups = createCircuitGroups(
                        devices = typeDevices,
                        profile = profile,
                        safetyProfile = safetyProfile,
                        startGroupNumber = totalGroupNumber,
                        room = room
                    )
                    allGroups.addAll(groups)
                    totalGroupNumber += groups.size
                }
            }

            val distributedGroups = distributeGroupsBalanced(allGroups)
            saveGroupsWithDevices(distributedGroups)

            distributedGroups.forEach {
                if (it.phase == null) throw IllegalStateException("Фаза у группы №${it.groupNumber} не установлена!")
            }
            GroupingResult.Success(ElectricalSystem(distributedGroups))
        } catch (e: Exception) {
            GroupingResult.Error("Ошибка расчета: ${e.message}")
        }
    }

    private suspend fun saveGroupsWithDevices(groups: List<CircuitGroup>) {
        groupRepository.deleteAllGroups()
        groupRepository.addGroup(groups)
    }

    private fun createCircuitGroups(
        devices: List<DeviceEntity>,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        startGroupNumber: Int,
        room: RoomEntity
    ): List<CircuitGroup> {
        val sortedDevices = devices.sortedByDescending { it.nominalCurrent() }
        val groups = mutableListOf<CircuitGroup>()
        var currentGroup = mutableListOf<DeviceEntity>()
        var currentSum = 0.0
        var groupNumber = startGroupNumber

        for (device in sortedDevices) {
            val current = device.nominalCurrent()
            if (currentSum + current > profile.maxCurrent * 1.2) {
                if (currentGroup.isNotEmpty()) {
                    groups.add(
                        createGroup(
                            devices = currentGroup,
                            profile = profile,
                            safetyProfile = safetyProfile,
                            groupNumber = groupNumber++,
                            room = room
                        )
                    )
                }
                currentGroup = mutableListOf()
                currentSum = 0.0
            }
            currentGroup.add(device)
            currentSum += current
        }

        if (currentGroup.isNotEmpty()) {
            groups.add(
                createGroup(
                    devices = currentGroup,
                    profile = profile,
                    safetyProfile = safetyProfile,
                    groupNumber = groupNumber,
                    room = room
                )
            )
        }

        return groups
    }

    private fun selectBreaker(nominalCurrent: Double, deviceType: DeviceType, hasMotor: Boolean): GroupProfile {
        val current = ceil(nominalCurrent).toInt()

        val minRatingByType = mapOf(
            DeviceType.LIGHTING to 10,
            DeviceType.SOCKET to 16,
            DeviceType.HEAVY_DUTY to 16,
            DeviceType.OVEN to 20,
            DeviceType.AIR_CONDITIONER to 20,
            DeviceType.ELECTRIC_STOVE to 25
        )

        val requiredMin = minRatingByType[deviceType] ?: 10
        val finalRequired = maxOf(current, requiredMin)

        val breakerOptions = listOf(
            Triple(10, 1.5, "B"),
            Triple(16, 2.5, "C"),
            Triple(20, 2.5, "C"),
            Triple(25, 4.0, "C"),
            Triple(32, 6.0, "C"),
            Triple(40, 10.0, "C"),
            Triple(50, 10.0, "D"),
            Triple(63, 16.0, "D")
        )

        val (rating, cable, type) = breakerOptions.firstOrNull { it.first >= finalRequired }
            ?: throw IllegalArgumentException("Нет подходящего автомата для ${finalRequired}А")

        val finalType = if (hasMotor) "D" else type

        return GroupProfile(
            maxCurrent = rating.toDouble(),
            breakerRating = rating,
            cableSection = cable,
            breakerType = finalType
        )
    }

    private fun createGroup(
        devices: List<DeviceEntity>,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        groupNumber: Int,
        room: RoomEntity
    ): CircuitGroup {
        val nominalCurrent = devices.sumOf { it.nominalCurrent() }

        return CircuitGroup(
            roomName = room.name,
            groupType = devices.first().deviceType,
            devices = devices.map { it.toDomainModel() },
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = profile.cableSection,
            breakerType = profile.breakerType,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            groupNumber = groupNumber,
            roomId = room.id
        )
    }

    private fun createDedicatedGroup(
        device: DeviceEntity,
        profile: GroupProfile,
        safetyProfile: SafetyProfile,
        groupNumber: Int,
        room: RoomEntity
    ): CircuitGroup {
        val nominalCurrent = device.nominalCurrent()

        return CircuitGroup(
            roomName = room.name,
            groupType = DeviceType.HEAVY_DUTY,
            devices = listOf(device.toDomainModel()),
            nominalCurrent = nominalCurrent,
            circuitBreaker = profile.breakerRating,
            cableSection = profile.cableSection,
            breakerType = profile.breakerType,
            rcdRequired = safetyProfile.rcdRequired,
            rcdCurrent = safetyProfile.rcdCurrent,
            groupNumber = groupNumber,
            roomId = room.id
        )
    }
}

fun DeviceEntity.nominalCurrent(): Double {
    return CurrentCalculator.calculateNominalCurrent(
        power = power.toDouble(),
        voltage = voltage.value.toDouble(),
        powerFactor = powerFactor,
        demandRatio = demandRatio,
        voltageType = voltage.type
    )
}

fun DeviceEntity.toDomainModel() = Device(
    id = deviceId,
    name = name,
    power = power,
    voltage = voltage,
    demandRatio = demandRatio,
    powerFactor = powerFactor,
    hasMotor = hasMotor,
    requiresDedicatedCircuit = requiresDedicatedCircuit,
    deviceType = deviceType,
    roomId = roomId
)

fun List<DeviceEntity>.toDomainModels() = map { it.toDomainModel() }

fun CircuitGroupEntity.toDomainModel(devices: List<Device>) = CircuitGroup(
    groupId = 0L,
    groupNumber = groupNumber,
    roomName = roomName,
    roomId = roomId,
    groupType = DeviceType.valueOf(groupType),
    devices = devices,
    nominalCurrent = nominalCurrent,
    circuitBreaker = circuitBreaker,
    cableSection = cableSection,
    breakerType = breakerType,
    rcdRequired = rcdRequired,
    rcdCurrent = rcdCurrent
)


