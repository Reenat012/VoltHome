package ru.mugalimov.volthome.domain.use_case

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.PhaseMode
import javax.inject.Inject

/**
 * Обновляет ИМЯ и МОЩНОСТЬ конкретного экземпляра устройства (только запись в инстанс).
 * Каталог дефолтов не меняем. После сохранения вызываем пересчёт.
 */
class UpdateDeviceFieldsUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val recalc: RecalculateAllUseCase,
    private val prefs: PreferencesRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /**
     * @param deviceId id инстанса (Device.id)
     * @param newName не пустое имя
     * @param newPowerW мощность в Вт (>0)
     */
    suspend operator fun invoke(
        deviceId: Long,
        newName: String,
        newPowerW: Int
    ) = withContext(io) {
        val name = newName.trim()
        require(name.isNotEmpty()) { "Имя не может быть пустым" }
        require(newPowerW > 0) { "Мощность должна быть > 0 Вт" }
        require(newPowerW <= 500_000) { "Слишком большая мощность" }

        val current: Device = deviceRepository.getDeviceById(deviceId.toInt())
            ?: error("Устройство не найдено: $deviceId")

        val updated = current.copy(
            name = name,
            power = newPowerW
        )
        deviceRepository.updateDevice(updated)

        // Явный пересчёт (учитываем текущий режим 1/3 фазы)
        val mode: PhaseMode = prefs.phaseMode.first()
        recalc(mode)
    }
}

/**
 * Унифицированная точка пересчёта всего проекта:
 * — перерасчёт групп и распределение по фазам
 * — подбор вводного
 * — обновление экспликации/индикаторов/диаграмм
 *
 * В реальном проекте это может оборачивать существующий GroupCalculator/Facade.
 */
class RecalculateAllUseCase @Inject constructor(
    private val calculatorFactory: GroupCalculatorFactory,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend operator fun invoke(mode: PhaseMode) = withContext(io) {
        calculatorFactory.create().calculateGroups(mode)
    }
}