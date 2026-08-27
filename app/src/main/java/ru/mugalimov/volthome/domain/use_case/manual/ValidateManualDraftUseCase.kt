package ru.mugalimov.volthome.domain.use_case.manual

import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import ru.mugalimov.volthome.domain.model.manual.toCompatibilityInput
import ru.mugalimov.volthome.domain.policy.compatibility.CircuitCompatibilityPolicy
import ru.mugalimov.volthome.domain.policy.compatibility.userMessage

class ValidateManualDraftUseCase @Inject constructor() {

    data class Violation(
        val code: String,
        val message: String,
        val groupId: Long? = null,
        val deviceId: Long? = null
    )

    data class Result(
        val violations: List<Violation>,
        val warnings: List<Violation> = emptyList()
    ) {
        val isValid: Boolean get() = violations.isEmpty()

        fun requireValid(context: String) {
            require(isValid) {
                "$context: " + violations.joinToString("; ") { it.message }
            }
        }
    }

    fun execute(draft: ProjectEditState, requireCalculatedLines: Boolean): Result {
        val violations = mutableListOf<Violation>()
        val warnings = mutableListOf<Violation>()
        val devicesById = draft.devices.associateBy { it.deviceId }
        val assignedOccurrences = draft.groups
            .flatMap { group -> group.deviceIds.map { group.groupId to it } }
        val assignedIds = assignedOccurrences.map { it.second }

        assignedOccurrences
            .filter { (_, deviceId) -> deviceId !in devicesById }
            .forEach { (groupId, deviceId) ->
                violations += Violation(
                    code = "UNKNOWN_DEVICE",
                    message = "Группа $groupId ссылается на отсутствующее устройство $deviceId",
                    groupId = groupId,
                    deviceId = deviceId
                )
            }

        assignedIds.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .forEach { (deviceId, count) ->
                violations += Violation(
                    code = "DUPLICATE_MEMBERSHIP",
                    message = "Устройство $deviceId находится в $count группах",
                    deviceId = deviceId
                )
            }

        (assignedIds.toSet() intersect draft.unassignedDeviceIds).forEach { deviceId ->
            violations += Violation(
                code = "ASSIGNED_AND_UNASSIGNED",
                message = "Устройство $deviceId одновременно распределено и находится в нераспределённых",
                deviceId = deviceId
            )
        }

        val referenced = assignedIds.toSet() + draft.unassignedDeviceIds
        (devicesById.keys - referenced).forEach { deviceId ->
            violations += Violation(
                code = "DANGLING_DEVICE",
                message = "Устройство $deviceId потеряно из состава проекта",
                deviceId = deviceId
            )
        }

        draft.groups.forEach { group ->
            if (group.deviceIds.isEmpty()) {
                violations += Violation(
                    code = "EMPTY_GROUP",
                    message = "Группа №${group.groupNumber} не содержит устройств",
                    groupId = group.groupId
                )
                return@forEach
            }

            val groupDevices = group.deviceIds.mapNotNull(devicesById::get)
            val decision = CircuitCompatibilityPolicy.evaluateGroup(
                CircuitCompatibilityPolicy.GroupInput(
                    phaseMode = draft.phaseMode,
                    phase = group.phase,
                    roomId = group.roomId,
                    devices = groupDevices.map { it.toCompatibilityInput() }
                )
            )
            // В MANUAL совместимость — осознанное отклонение, а не запрет.
            // Жёсткими остаются только структурные инварианты draft: ссылки,
            // уникальность размещения, непустые группы и рассчитанные линии.
            decision.reasons.forEach { reason ->
                    warnings += Violation(
                        code = reason.name,
                        message = "Группа №${group.groupNumber}: ${reason.userMessage}",
                        groupId = group.groupId
                    )
            }

            if (requireCalculatedLines &&
                (group.nominalCurrent == null ||
                    group.circuitBreaker == null ||
                    group.cableSection == null ||
                    group.breakerType.isNullOrBlank())
            ) {
                violations += Violation(
                    code = "LINE_NOT_CALCULATED",
                    message = "Для группы №${group.groupNumber} не рассчитаны параметры линии",
                    groupId = group.groupId
                )
            }
        }

        if (draft.groups.map { it.groupNumber }.distinct().size != draft.groups.size) {
            violations += Violation(
                code = "DUPLICATE_GROUP_NUMBER",
                message = "Номера групп должны быть уникальны"
            )
        }

        return Result(
            violations = violations.distinct(),
            warnings = warnings.distinct()
        )
    }
}
