package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType

/**
 * Каноническое представление тока щита по фазам.
 *
 * Для сбалансированной 3ф нагрузки CurrentCalculator возвращает уже
 * линейный ток. Поэтому он добавляется в A, B и C без дополнительного
 * деления на три.
 */
data class PhaseLoadVector(
    val a: Double = 0.0,
    val b: Double = 0.0,
    val c: Double = 0.0
) {
    val max: Double get() = maxOf(a, b, c)

    fun asMap(): Map<Phase, Double> = mapOf(
        Phase.A to a,
        Phase.B to b,
        Phase.C to c
    )
}

fun phaseLoadVector(groups: List<CircuitGroup>): PhaseLoadVector {
    var a = 0.0
    var b = 0.0
    var c = 0.0

    groups.forEach { group ->
        val current = group.nominalCurrent
        require(current.isFinite() && current >= 0.0) {
            "Некорректный ток группы №${group.groupNumber}: $current A"
        }
        require(group.circuitBreaker > 0) {
            "Группа №${group.groupNumber}: не задан номинал автомата"
        }
        require(current <= group.circuitBreaker + 1e-6) {
            "Группа №${group.groupNumber} перегружена: " +
                    "${"%.2f".format(current)} A > ${group.circuitBreaker} A"
        }
        if (group.devices.isNotEmpty()) {
            val installedCurrent = CircuitLoadCalculator.calculate(group.devices).installedCurrentA
            require(installedCurrent <= group.circuitBreaker + 1e-6) {
                "Группа №${group.groupNumber} перегружена: " +
                        "${"%.2f".format(installedCurrent)} A > ${group.circuitBreaker} A. Нужен пересчёт групп."
            }
        }

        val hasThreePhaseDevices = group.devices.any {
            it.voltage.type == VoltageType.AC_3PHASE
        }
        val hasOtherDevices = group.devices.any {
            it.voltage.type != VoltageType.AC_3PHASE
        }
        require(!(hasThreePhaseDevices && hasOtherDevices)) {
            "Группа №${group.groupNumber} смешивает 1ф и 3ф устройства"
        }
        require(!(group.phase == Phase.THREE_PHASE && hasOtherDevices && !hasThreePhaseDevices)) {
            "Группа №${group.groupNumber} помечена как 3ф, но содержит только 1ф устройства"
        }
        require(!hasThreePhaseDevices || group.phase == Phase.THREE_PHASE) {
            "Группа №${group.groupNumber} с 3ф устройством не помечена как трёхфазная"
        }

        if (group.phase == Phase.THREE_PHASE || hasThreePhaseDevices) {
            a += current
            b += current
            c += current
        } else {
            when (group.phase) {
                Phase.A -> a += current
                Phase.B -> b += current
                Phase.C -> c += current
                Phase.THREE_PHASE -> Unit
            }
        }
    }

    return PhaseLoadVector(a = a, b = b, c = c)
}

/** Считает суммарный линейный ток A/B/C. */
fun phaseCurrents(groups: List<CircuitGroup>): Map<Phase, Double> =
    phaseLoadVector(groups).asMap().withDefault { 0.0 }

/** Определяет тип сети на основании фактического распределения фаз */
fun inferVoltageType(groups: List<CircuitGroup>): VoltageType {
    val hasThreePhaseEvidence = groups.any { group ->
        group.phase == Phase.THREE_PHASE ||
                group.phase == Phase.B ||
                group.phase == Phase.C ||
                group.devices.any { it.voltage.type == VoltageType.AC_3PHASE }
    }
    return if (hasThreePhaseEvidence) VoltageType.AC_3PHASE else VoltageType.AC_1PHASE
}

/** Безопасный доступ с 0.0 по умолчанию */
fun Map<Phase, Double>.getOrZero(phase: Phase): Double = this.getOrDefault(phase, 0.0)
