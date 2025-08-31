package ru.mugalimov.volthome.domain.model.provider

import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaticDeviceDefaultsProvider @Inject constructor() : DeviceDefaultsProvider {
    private val map = mapOf(

        // ⚡ Стандартные группы
        DeviceType.LIGHTING to DeviceDefaults(
            power = 150,
            powerFactor = 0.90,
            demandRatio = 0.80,
            voltage = Voltage(220, VoltageType.AC_1PHASE),
            hasMotor = false,
            requiresDedicatedCircuit = false,
            requiresSocketConnection = false
        ),

        DeviceType.SOCKET to DeviceDefaults(
            power = 1000,
            powerFactor = 0.95,
            demandRatio = 0.60,
            voltage = Voltage(220, VoltageType.AC_1PHASE),
            hasMotor = false,
            requiresDedicatedCircuit = false,
            requiresSocketConnection = true
        ),

        DeviceType.HEAVY_DUTY to DeviceDefaults(
            power = 2500,
            powerFactor = 0.90,
            demandRatio = 1.00,
            voltage = Voltage(220, VoltageType.AC_1PHASE),
            hasMotor = true,
            requiresDedicatedCircuit = true,
            requiresSocketConnection = false
        ),

        // ❄️ Специализированные устройства
        DeviceType.AIR_CONDITIONER to DeviceDefaults(
            power = 2000,
            powerFactor = 0.85,
            demandRatio = 0.90,
            voltage = Voltage(220, VoltageType.AC_1PHASE),
            hasMotor = true,
            requiresDedicatedCircuit = true,
            requiresSocketConnection = true
        ),

        DeviceType.ELECTRIC_STOVE to DeviceDefaults(
            power = 7000,
            powerFactor = 1.00,
            demandRatio = 1.00,
            voltage = Voltage(380, VoltageType.AC_3PHASE),
            hasMotor = false,
            requiresDedicatedCircuit = true,
            requiresSocketConnection = false
        ),

        DeviceType.OVEN to DeviceDefaults(
            power = 3000,
            powerFactor = 0.95,
            demandRatio = 1.00,
            voltage = Voltage(220, VoltageType.AC_1PHASE),
            hasMotor = false,
            requiresDedicatedCircuit = true,
            requiresSocketConnection = false
        ),

        DeviceType.WASHING_MACHINE to DeviceDefaults(
            power = 2200,
            powerFactor = 0.90,
            demandRatio = 1.00,
            voltage = Voltage(220, VoltageType.AC_1PHASE),
            hasMotor = true,
            requiresDedicatedCircuit = true,
            requiresSocketConnection = true
        ),

        DeviceType.DISHWASHER to DeviceDefaults(
            power = 1800,
            powerFactor = 0.95,
            demandRatio = 1.00,
            voltage = Voltage(220, VoltageType.AC_1PHASE),
            hasMotor = false,
            requiresDedicatedCircuit = true,
            requiresSocketConnection = true
        ),

        DeviceType.WATER_HEATER to DeviceDefaults(
            power = 3000,
            powerFactor = 0.98,
            demandRatio = 1.00,
            voltage = Voltage(220, VoltageType.AC_1PHASE),
            hasMotor = false,
            requiresDedicatedCircuit = true,
            requiresSocketConnection = false
        ),

        // 🌀 Прочие
        DeviceType.OTHER to DeviceDefaults(
            power = 1000,
            powerFactor = 0.90,
            demandRatio = 0.80,
            voltage = Voltage(220, VoltageType.AC_1PHASE),
            hasMotor = false,
            requiresDedicatedCircuit = false,
            requiresSocketConnection = true
        )
    )

    override fun get(type: DeviceType): DeviceDefaults = map.getValue(type)
}