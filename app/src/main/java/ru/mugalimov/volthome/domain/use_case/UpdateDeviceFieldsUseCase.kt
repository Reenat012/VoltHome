package ru.mugalimov.volthome.domain.use_case

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.core.validation.PowerValidator
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.VoltageType
import javax.inject.Inject

/**
 * Обновляет поля устройства.
 *
 * ВАЖНО (Коммит №2):
 * - Здесь НЕТ пересчёта.
 * - Пересчёт запускается только реактивным контуром RecalculateGroupsOnDeviceChangeUseCase
 *   по факту изменения устройства (DEVICE_CHANGED).
 *
 * Иначе получаешь:
 * 1) прямой запуск recalc отсюда
 * 2) второй запуск от observeAllDevices()
 * => "реактивная бензопила"
 */
class UpdateDeviceFieldsUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    suspend operator fun invoke(
        deviceId: Long,
        newName: String,
        newPowerW: Int,

        // ✅ PRO extras (в FREE передаём null)
        newDeviceType: DeviceType? = null,
        newPowerFactor: Double? = null,
        newDemandRatio: Double? = null,
        newVoltageValue: Int? = null,
        newVoltageType: VoltageType? = null,
        newHasMotor: Boolean? = null,
        newRequiresDedicatedCircuit: Boolean? = null,
        newRequiresSocketConnection: Boolean? = null
    ) = withContext(io) {

        val name = newName.trim()
        require(name.isNotEmpty()) { "Имя не может быть пустым" }

        val rangeError = PowerValidator.errorMessage(newPowerW)
        require(rangeError == null) { rangeError!! }

        newPowerFactor?.let {
            require(it in 0.1..1.0) { "PF должен быть в диапазоне 0.1 — 1.0" }
        }
        newDemandRatio?.let {
            require(it in 0.1..1.0) { "Коэфф. спроса должен быть в диапазоне 0.1 — 1.0" }
        }

        // Напряжение: DC не поддерживается
        newVoltageValue?.let {
            require(it in 1..1000) { "Напряжение должно быть в диапазоне 1 — 1000 В" }
        }
        newVoltageType?.let {
            require(it != VoltageType.DC) { "DC пока не поддерживается" }
        }

        val current: Device = deviceRepository.getDeviceById(deviceId.toInt())
            ?: error("Устройство не найдено: $deviceId")

        val updatedVoltage = if (newVoltageValue != null || newVoltageType != null) {
            current.voltage.copy(
                value = newVoltageValue ?: current.voltage.value,
                type = newVoltageType ?: current.voltage.type
            )
        } else {
            current.voltage
        }

        val updated = current.copy(
            name = name,
            power = newPowerW,

            deviceType = newDeviceType ?: current.deviceType,
            powerFactor = newPowerFactor ?: current.powerFactor,
            demandRatio = newDemandRatio ?: current.demandRatio,
            voltage = updatedVoltage,

            hasMotor = newHasMotor ?: current.hasMotor,
            requiresDedicatedCircuit = newRequiresDedicatedCircuit ?: current.requiresDedicatedCircuit,
            requiresSocketConnection = newRequiresSocketConnection ?: current.requiresSocketConnection
        )

        deviceRepository.updateDevice(updated)

        // ❌ Никакого recalc() тут.
        // ✅ Пересчёт поднимется реактивно: DEVICE_CHANGED.
    }
}