package ru.mugalimov.volthome.domain.model.manual

import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType

/**
 * Проектное состояние для ручного редактирования.
 * Важно: "фаза устройства" не хранится — фаза устройства = фаза группы.
 */
data class ProjectEditState(
    val projectId: String,
    val groups: List<ManualGroupDraft>,
    val devices: List<ManualDeviceDraft>,
    val unassignedDeviceIds: Set<Long>,
    val nextGroupNumber: Int,
) {

    fun deepCopy(): ProjectEditState {
        return copy(
            groups = groups.map { it.deepCopy() },
            devices = devices.map { it.deepCopy() },
            unassignedDeviceIds = unassignedDeviceIds.toSet()
        )
    }
}

/**
 * Состав группы с точки зрения "правил смешения".
 * NORMAL — состав однородный (по типу группы/ожидаемому назначению).
 * MIXED_MANUAL — пользователь вручную смешал типы (допустимо, но помечаем и предупреждаем).
 */
enum class ManualGroupComposition {
    NORMAL,
    MIXED_MANUAL
}

data class ManualGroupDraft(
    val groupId: Long,
    val groupNumber: Int,
    val roomId: Long,
    val roomName: String,
    val groupType: DeviceType,

    // Пометка смешанного состава (не меняет groupType)
    val composition: ManualGroupComposition = ManualGroupComposition.NORMAL,

    val phase: Phase,
    val deviceIds: List<Long>,

    // Линия/защита (как часть группового состояния в draft; фактическая пересборка будет в следующих коммитах)
    val nominalCurrent: Double? = null,
    val circuitBreaker: Int? = null,
    val cableSection: Double? = null,
    val breakerType: String? = null,
    val rcdRequired: Boolean? = null,
    val rcdCurrent: Int? = null,
) {
    fun deepCopy(): ManualGroupDraft {
        return copy(deviceIds = deviceIds.toList())
    }
}

data class ManualDeviceDraft(
    val deviceId: Long,
    val roomId: Long,

    // ✅ НОВОЕ:
    // Имя комнаты должно жить прямо в устройстве manual draft.
    // Иначе устройство из unassigned не сможет создать новую группу,
    // если в draft.groups сейчас нет ни одной группы этой комнаты.
    val roomName: String,

    val deviceType: DeviceType,

    val powerW: Int?,
    val voltageType: VoltageType,
    val demandRatio: Double?,
    val powerFactor: Double?,
    val hasMotor: Boolean,

    val requiresDedicatedCircuit: Boolean,
) {
    fun deepCopy(): ManualDeviceDraft = copy()
}