package ru.mugalimov.volthome.domain.policy.compatibility

import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.VoltageType

/**
 * Единая политика структурной совместимости линии.
 *
 * Она намеренно не выбирает автомат, кабель или УЗО. Задача policy — не дать
 * AUTO, MANUAL и commit-слою по-разному трактовать допустимый состав группы.
 */
object CircuitCompatibilityPolicy {

    enum class Status {
        ALLOWED,
        ALLOWED_WITH_WARNING,
        REJECTED
    }

    enum class Reason {
        CROSS_ROOM,
        MIXED_VOLTAGE_TYPE,
        MIXED_CONNECTION_TYPE,
        DEDICATED_LOAD_IN_SHARED_GROUP,
        DEDICATED_GROUP_ALREADY_OCCUPIED,
        THREE_PHASE_LOAD_IN_SINGLE_PHASE_PROJECT,
        THREE_PHASE_LOAD_ON_SINGLE_PHASE_BRANCH,
        SINGLE_PHASE_LOAD_ON_THREE_PHASE_BRANCH,
        MIXED_DEVICE_TYPES
    }

    data class DeviceInput(
        val id: Long,
        val roomId: Long,
        val deviceType: DeviceType,
        val voltageType: VoltageType,
        val requiresDedicatedCircuit: Boolean,
        val requiresSocketConnection: Boolean
    )

    data class GroupInput(
        val phaseMode: PhaseMode,
        val phase: Phase,
        val roomId: Long,
        val devices: List<DeviceInput>
    )

    data class Decision(
        val status: Status,
        val reasons: Set<Reason>
    ) {
        val allowed: Boolean get() = status != Status.REJECTED

        fun requireAllowed(context: String) {
            require(allowed) {
                "$context: ${reasons.joinToString { it.userMessage }}"
            }
        }
    }

    fun evaluateGroup(input: GroupInput): Decision {
        if (input.devices.isEmpty()) return Decision(Status.ALLOWED, emptySet())

        val reasons = linkedSetOf<Reason>()
        val first = input.devices.first()

        if (input.devices.any { it.roomId != input.roomId }) {
            reasons += Reason.CROSS_ROOM
        }
        if (input.devices.map { it.voltageType }.distinct().size > 1) {
            reasons += Reason.MIXED_VOLTAGE_TYPE
        }
        if (input.devices.map { it.requiresSocketConnection }.distinct().size > 1) {
            reasons += Reason.MIXED_CONNECTION_TYPE
        }
        if (input.devices.size > 1 && input.devices.any { it.requiresDedicatedCircuit }) {
            reasons += Reason.DEDICATED_LOAD_IN_SHARED_GROUP
        }
        if (input.devices.map { it.deviceType }.distinct().size > 1) {
            reasons += Reason.MIXED_DEVICE_TYPES
        }

        val hasThreePhaseLoad = input.devices.any { it.voltageType == VoltageType.AC_3PHASE }
        if (input.phaseMode == PhaseMode.SINGLE && hasThreePhaseLoad) {
            reasons += Reason.THREE_PHASE_LOAD_IN_SINGLE_PHASE_PROJECT
        }
        if (hasThreePhaseLoad && input.phase != Phase.THREE_PHASE) {
            reasons += Reason.THREE_PHASE_LOAD_ON_SINGLE_PHASE_BRANCH
        }
        if (!hasThreePhaseLoad && input.phase == Phase.THREE_PHASE) {
            reasons += Reason.SINGLE_PHASE_LOAD_ON_THREE_PHASE_BRANCH
        }

        val rejected = reasons.any {
            it != Reason.MIXED_DEVICE_TYPES
        }
        return when {
            rejected -> Decision(Status.REJECTED, reasons)
            first.deviceType.let { input.devices.any { d -> d.deviceType != it } } ->
                Decision(Status.ALLOWED_WITH_WARNING, reasons)
            else -> Decision(Status.ALLOWED, reasons)
        }
    }

    fun evaluatePlacement(
        device: DeviceInput,
        target: GroupInput
    ): Decision {
        val placementReasons = linkedSetOf<Reason>()
        if (device.requiresDedicatedCircuit && target.devices.isNotEmpty()) {
            placementReasons += Reason.DEDICATED_LOAD_IN_SHARED_GROUP
        }
        if (target.devices.any { it.requiresDedicatedCircuit }) {
            placementReasons += Reason.DEDICATED_GROUP_ALREADY_OCCUPIED
        }

        val combined = evaluateGroup(
            target.copy(devices = target.devices + device)
        )
        val allReasons = combined.reasons + placementReasons
        val rejected = placementReasons.isNotEmpty() || combined.status == Status.REJECTED
        return when {
            rejected -> Decision(Status.REJECTED, allReasons)
            allReasons.isNotEmpty() -> Decision(Status.ALLOWED_WITH_WARNING, allReasons)
            else -> Decision(Status.ALLOWED, emptySet())
        }
    }
}

val CircuitCompatibilityPolicy.Reason.userMessage: String
    get() = when (this) {
        CircuitCompatibilityPolicy.Reason.CROSS_ROOM ->
            "устройство и группа относятся к разным помещениям"
        CircuitCompatibilityPolicy.Reason.MIXED_VOLTAGE_TYPE ->
            "нельзя смешивать однофазные и трёхфазные устройства"
        CircuitCompatibilityPolicy.Reason.MIXED_CONNECTION_TYPE ->
            "нельзя смешивать розеточное и стационарное подключение"
        CircuitCompatibilityPolicy.Reason.DEDICATED_LOAD_IN_SHARED_GROUP ->
            "устройство требует выделенной линии"
        CircuitCompatibilityPolicy.Reason.DEDICATED_GROUP_ALREADY_OCCUPIED ->
            "целевая группа уже является выделенной линией"
        CircuitCompatibilityPolicy.Reason.THREE_PHASE_LOAD_IN_SINGLE_PHASE_PROJECT ->
            "трёхфазная нагрузка недоступна в однофазном проекте"
        CircuitCompatibilityPolicy.Reason.THREE_PHASE_LOAD_ON_SINGLE_PHASE_BRANCH ->
            "трёхфазная нагрузка должна находиться в трёхфазной группе"
        CircuitCompatibilityPolicy.Reason.SINGLE_PHASE_LOAD_ON_THREE_PHASE_BRANCH ->
            "однофазная нагрузка должна быть назначена на фазу A, B или C"
        CircuitCompatibilityPolicy.Reason.MIXED_DEVICE_TYPES ->
            "группа содержит устройства разных назначений"
    }
