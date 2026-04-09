package ru.mugalimov.volthome.domain.model.manual

import ru.mugalimov.volthome.domain.model.CableSelectionReason
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.LineSelectionReason
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
 * Состав группы с точки зрения правил смешения.
 * NORMAL — состав однородный.
 * MIXED_MANUAL — пользователь вручную смешал типы.
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
    val composition: ManualGroupComposition = ManualGroupComposition.NORMAL,
    val phase: Phase,
    val deviceIds: List<Long>,

    // Линия/защита
    val nominalCurrent: Double? = null,
    val circuitBreaker: Int? = null,
    val cableSection: Double? = null,
    val breakerType: String? = null,

    // Объяснение выбора линии единым policy-слоем
    val whyBreakerSelected: LineSelectionReason? = null,
    val whyCableSelected: CableSelectionReason? = null,

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
    val roomName: String,
    val deviceType: DeviceType,
    val powerW: Int?,
    val voltageType: VoltageType,

    // Manual path обязан хранить реальное напряжение устройства.
    val voltageValue: Int?,

    val demandRatio: Double?,
    val powerFactor: Double?,
    val hasMotor: Boolean,
    val requiresDedicatedCircuit: Boolean,
) {
    fun deepCopy(): ManualDeviceDraft = copy()
}