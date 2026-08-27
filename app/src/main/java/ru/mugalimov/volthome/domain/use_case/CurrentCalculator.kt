package ru.mugalimov.volthome.domain.use_case

import ru.mugalimov.volthome.domain.model.VoltageType
import kotlin.math.sqrt

/**
 * Единое доменное ядро расчёта нагрузок.
 *
 * Коммит 2:
 * - здесь живёт canonical способ получить:
 *   1) installed power
 *   2) calculated power
 *   3) calculated current
 *
 * ВАЖНО:
 * - все остальные use-case должны использовать именно этот контракт;
 * - ручной режим может отличаться входными данными,
 *   но не должен отличаться самой формулой.
 */
data class LoadInput(
    val powerW: Double,
    val voltage: Double,
    val powerFactor: Double?,
    val demandRatio: Double,
    val voltageType: VoltageType,
    val label: String? = null
)

data class DeviceLoad(
    val installedPowerW: Double,
    val installedCurrentA: Double,
    val calculatedPowerW: Double,
    val calculatedCurrentA: Double
)

data class GroupLoad(
    val installedPowerW: Double,
    val installedCurrentA: Double,
    val calculatedPowerW: Double,
    val calculatedCurrentA: Double
)

object CurrentCalculator {

    private const val MIN_POWER_FACTOR = 0.1
    private const val DEFAULT_1PH_VOLTAGE = 230.0
    private const val DEFAULT_3PH_LINE_VOLTAGE = 400.0

    /** Напряжение по умолчанию для случая, когда входное значение отсутствует/битое. */
    fun defaultVoltageFor(voltageType: VoltageType): Double = when (voltageType) {
        VoltageType.AC_1PHASE -> DEFAULT_1PH_VOLTAGE
        VoltageType.AC_3PHASE -> DEFAULT_3PH_LINE_VOLTAGE
        VoltageType.DC -> DEFAULT_1PH_VOLTAGE
    }

    /** Нормализация напряжения. */
    fun normalizeVoltage(voltage: Double, voltageType: VoltageType): Double {
        require(voltage.isFinite()) { "Напряжение должно быть конечным числом" }
        return if (voltage > 0.0) voltage else defaultVoltageFor(voltageType)
    }

    /** Нормализация cos φ. */
    fun normalizePowerFactor(powerFactor: Double?): Double {
        val value = powerFactor ?: 1.0
        require(value.isFinite() && value in MIN_POWER_FACTOR..1.0) {
            "Коэффициент мощности должен быть в диапазоне $MIN_POWER_FACTOR..1.0"
        }
        return value
    }

    /** Canonical calculated power. */
    fun calculateCalculatedPower(
        power: Double,
        demandRatio: Double
    ): Double {
        require(power.isFinite() && power >= 0.0) {
            "Мощность должна быть конечным неотрицательным числом"
        }
        require(demandRatio.isFinite() && demandRatio in 0.0..1.0) {
            "Коэффициент спроса должен быть в диапазоне 0.0..1.0"
        }
        return power * demandRatio
    }

    /** Installed current без учета коэффициента спроса. */
    fun calculateInstalledCurrent(
        power: Double,
        voltage: Double,
        powerFactor: Double?,
        voltageType: VoltageType
    ): Double {
        require(power.isFinite() && power >= 0.0) {
            "Мощность должна быть конечным неотрицательным числом"
        }
        val normalizedVoltage = normalizeVoltage(voltage, voltageType)
        val pf = normalizePowerFactor(powerFactor)

        return when (voltageType) {
            VoltageType.AC_1PHASE -> power / (normalizedVoltage * pf)
            VoltageType.AC_3PHASE -> power / (sqrt(3.0) * normalizedVoltage * pf)
            VoltageType.DC -> power / normalizedVoltage
        }
    }

    /** Installed power, reconstructed from line current. */
    fun calculateInstalledPower(
        current: Double,
        voltage: Double,
        powerFactor: Double?,
        voltageType: VoltageType
    ): Double {
        require(current.isFinite() && current >= 0.0) {
            "Ток должен быть конечным неотрицательным числом"
        }
        val normalizedVoltage = normalizeVoltage(voltage, voltageType)
        val pf = normalizePowerFactor(powerFactor)
        return when (voltageType) {
            VoltageType.AC_1PHASE -> current * normalizedVoltage * pf
            VoltageType.AC_3PHASE -> current * sqrt(3.0) * normalizedVoltage * pf
            VoltageType.DC -> current * normalizedVoltage
        }
    }

    /**
     * Canonical calculated current.
     *
     * ВАЖНО:
     * - именно этот способ считается единым для calculated current;
     * - старое имя calculateNominalCurrent оставляем как alias, чтобы не ломать проект.
     */
    fun calculateCalculatedCurrent(
        power: Double,
        voltage: Double,
        powerFactor: Double?,
        demandRatio: Double,
        voltageType: VoltageType
    ): Double {
        val calculatedPower = calculateCalculatedPower(
            power = power,
            demandRatio = demandRatio
        )

        val normalizedVoltage = normalizeVoltage(voltage, voltageType)
        val pf = normalizePowerFactor(powerFactor)

        return when (voltageType) {
            VoltageType.AC_1PHASE -> calculatedPower / (normalizedVoltage * pf)
            VoltageType.AC_3PHASE -> calculatedPower / (sqrt(3.0) * normalizedVoltage * pf)
            VoltageType.DC -> calculatedPower / normalizedVoltage
        }
    }

    /** Legacy alias — оставляем для совместимости вызовов. */
    fun calculateNominalCurrent(
        power: Double,
        voltage: Double,
        powerFactor: Double?,
        demandRatio: Double,
        voltageType: VoltageType
    ): Double {
        return calculateCalculatedCurrent(
            power = power,
            voltage = voltage,
            powerFactor = powerFactor,
            demandRatio = demandRatio,
            voltageType = voltageType
        )
    }

    /** Canonical device load. */
    fun calculateDeviceLoad(input: LoadInput): DeviceLoad {
        val installedPower = input.powerW
        val installedCurrent = calculateInstalledCurrent(
            power = input.powerW,
            voltage = input.voltage,
            powerFactor = input.powerFactor,
            voltageType = input.voltageType
        )
        val calculatedPower = calculateCalculatedPower(
            power = input.powerW,
            demandRatio = input.demandRatio
        )
        val calculatedCurrent = calculateCalculatedCurrent(
            power = input.powerW,
            voltage = input.voltage,
            powerFactor = input.powerFactor,
            demandRatio = input.demandRatio,
            voltageType = input.voltageType
        )

        return DeviceLoad(
            installedPowerW = installedPower,
            installedCurrentA = installedCurrent,
            calculatedPowerW = calculatedPower,
            calculatedCurrentA = calculatedCurrent
        )
    }

    /** Canonical group load. */
    fun calculateGroupLoad(inputs: Iterable<LoadInput>): GroupLoad {
        var installedPower = 0.0
        var installedCurrent = 0.0
        var calculatedPower = 0.0
        var calculatedCurrent = 0.0

        for (input in inputs) {
            val deviceLoad = calculateDeviceLoad(input)
            installedPower += deviceLoad.installedPowerW
            installedCurrent += deviceLoad.installedCurrentA
            calculatedPower += deviceLoad.calculatedPowerW
            calculatedCurrent += deviceLoad.calculatedCurrentA
        }

        return GroupLoad(
            installedPowerW = installedPower,
            installedCurrentA = installedCurrent,
            calculatedPowerW = calculatedPower,
            calculatedCurrentA = calculatedCurrent
        )
    }
}
