package ru.mugalimov.volthome.domain.use_case

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.core.validation.PowerValidator
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.VoltageType
import javax.inject.Inject

/**
 * Обновляет поля устройства.
 *
 * ВАЖНО:
 * - После изменения параметров устройства пересчёт групп запускаем ЯВНО.
 * - Иначе device breakdown уже новый, а persisted group.nominalCurrent остаётся старым.
 */
class UpdateDeviceFieldsUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val activeProjectDataStore: ActiveProjectDataStore,
    private val preferencesRepository: PreferencesRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val saveAutoCalculatedGroupsToLocalDbUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,
    private val explicationRepository: ExplicationRepository,
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

        // 1. Сохраняем устройство
        deviceRepository.updateDevice(updated)

        // 2. Сразу пересчитываем и пересохраняем группы активного проекта
        val projectId = activeProjectDataStore.activeProjectId.first().orEmpty().trim()
        if (projectId.isBlank()) {
            // Не валим сохранение устройства, но честно сигналим вверх
            error("Устройство сохранено, но активный projectId пуст — группы не пересчитаны")
        }

        val phaseMode = preferencesRepository.phaseMode.first()
        val calculator = groupCalculatorFactory.create(projectId)

        when (val result = calculator.calculateGroups(phaseMode)) {
            is GroupingResult.Error -> {
                error("Устройство сохранено, но пересчёт групп не выполнен: ${result.message}")
            }

            is GroupingResult.Success -> {
                saveAutoCalculatedGroupsToLocalDbUseCase.execute(
                    SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                        projectId = projectId,
                        groups = result.system.groups,
                        distributionDecisions = result.distributionDecisions,
                        source = "UpdateDeviceFieldsUseCase.invoke",
                        opId = null
                    )
                )

                explicationRepository.setLastDistributionDecisions(result.distributionDecisions)
            }
        }
    }
}