package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.incomer.IncomerKind
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.incomer.RcdSelectivity
import ru.mugalimov.volthome.domain.model.incomer.RcdType
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceInventoryItem
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.model.protection.RcdKind

/** Единый источник состава защиты для сметы и визуализации щита. */
object ProtectionDeviceInventory {

    fun build(
        incomer: IncomerSpec?,
        groups: List<CircuitGroup>
    ): List<ProtectionDeviceInventoryItem> = buildList {
        incomer?.let { addAll(forIncomer(it)) }
        groups.sortedBy(CircuitGroup::groupNumber).forEach { addAll(forGroup(it)) }
    }

    fun forIncomer(incomer: IncomerSpec): List<ProtectionDeviceInventoryItem> =
        when (incomer.kind) {
            IncomerKind.MCB_ONLY -> listOf(
                ProtectionDeviceInventoryItem("incomer-breaker", incomer.breakerSpec())
            )
            IncomerKind.MCB_PLUS_RCD -> listOf(
                ProtectionDeviceInventoryItem("incomer-breaker", incomer.breakerSpec()),
                ProtectionDeviceInventoryItem("incomer-rcd", incomer.rcdSpec())
            )
            IncomerKind.RCBO -> listOf(
                ProtectionDeviceInventoryItem(
                    "incomer-rcbo",
                    ProtectionDeviceSpec(
                        kind = ProtectionDeviceKind.RCBO,
                        poles = incomer.poles,
                        ratedCurrentA = incomer.mcbRating,
                        breakerCurve = incomer.mcbCurve,
                        breakingCapacityA = incomer.icn,
                        leakageCurrentMa = incomer.rcdSensitivityMa,
                        rcdType = incomer.rcdType,
                        selectivity = incomer.rcdSelectivity
                    )
                )
            )
        }

    fun forGroup(group: CircuitGroup): List<ProtectionDeviceInventoryItem> {
        val rcd = group.rcdSpec
        val prefix = groupSlotPrefix(group)
        val breakerPoles = if (group.phase == Phase.THREE_PHASE) 3 else 1

        if (rcd?.kind == RcdKind.RCBO) {
            return listOf(
                ProtectionDeviceInventoryItem(
                    "$prefix-rcbo",
                    ProtectionDeviceSpec(
                        kind = ProtectionDeviceKind.RCBO,
                        poles = rcd.poles,
                        ratedCurrentA = rcd.ratedCurrentA ?: group.circuitBreaker,
                        breakerCurve = group.breakerType,
                        leakageCurrentMa = rcd.leakageCurrentMa,
                        rcdType = rcd.type,
                        selectivity = rcd.selectivity
                    )
                )
            )
        }

        return buildList {
            if (group.rcdRequired) {
                add(
                    ProtectionDeviceInventoryItem(
                        "$prefix-rcd",
                        ProtectionDeviceSpec(
                            kind = ProtectionDeviceKind.RCD,
                            poles = rcd?.poles ?: if (group.phase == Phase.THREE_PHASE) 4 else 2,
                            ratedCurrentA = rcd?.ratedCurrentA ?: group.circuitBreaker,
                            leakageCurrentMa = rcd?.leakageCurrentMa ?: group.rcdCurrent,
                            rcdType = rcd?.type ?: RcdType.A,
                            selectivity = rcd?.selectivity ?: RcdSelectivity.NONE
                        )
                    )
                )
            }
            add(
                ProtectionDeviceInventoryItem(
                    "$prefix-breaker",
                    ProtectionDeviceSpec(
                        kind = ProtectionDeviceKind.MCB,
                        poles = breakerPoles,
                        ratedCurrentA = group.circuitBreaker,
                        breakerCurve = group.breakerType
                    )
                )
            )
        }
    }

    /**
     * Идентификатор товарного слота не зависит от номера группы и Room PK:
     * AUTO-перестройка пересоздаёт строки groups. Для реальной группы используем
     * состав линии (помещение + тип + ids устройств). Если состав изменился,
     * старый товарный выбор намеренно не переносится на новую электрическую
     * функцию автоматически.
     */
    fun groupSlotPrefix(group: CircuitGroup): String {
        val deviceIds = group.devices.map { it.id }.filter { it > 0L }.sorted()
        if (deviceIds.isNotEmpty()) {
            val identity = buildString {
                append(group.roomId)
                append('|')
                append(group.groupType.name)
                append('|')
                append(deviceIds.joinToString(","))
            }
            return "circuit-${fnv1a64(identity).toString(16)}"
        }
        return if (group.groupId > 0L) {
            "group-id-${group.groupId}"
        } else {
            "group-${group.groupNumber}"
        }
    }

    private fun fnv1a64(value: String): ULong {
        var hash = 0xcbf29ce484222325uL
        value.forEach { char ->
            hash = hash xor char.code.toULong()
            hash *= 0x100000001b3uL
        }
        return hash
    }

    private fun IncomerSpec.breakerSpec() = ProtectionDeviceSpec(
        kind = ProtectionDeviceKind.MCB,
        poles = poles,
        ratedCurrentA = mcbRating,
        breakerCurve = mcbCurve,
        breakingCapacityA = icn
    )

    private fun IncomerSpec.rcdSpec(): ProtectionDeviceSpec =
        ProtectionDeviceSpec(
            kind = ProtectionDeviceKind.RCD,
            poles = poles,
            ratedCurrentA = rcdRatedCurrentA ?: mcbRating,
            leakageCurrentMa = rcdSensitivityMa,
            rcdType = rcdType,
            selectivity = rcdSelectivity
        )
}
