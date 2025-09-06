package ru.mugalimov.volthome.domain.use_case

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import javax.inject.Inject

/**
 * Авто-пересчёт групп при изменении устройств/привязок И/ИЛИ смене режима фаз.
 * Запускается один раз на всё приложение и живёт в App-scoped ViewModel.
 */
class RecalculateGroupsOnDeviceChangeUseCase @Inject constructor(
    private val deviceDao: DeviceDao,
    private val joinDao: GroupDeviceJoinDao,
    private val calculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository
) {
    @OptIn(FlowPreview::class)
    fun launch(scope: CoroutineScope): Job {
        return combine(
            deviceDao.observeDevices(),          // Flow<List<DeviceEntity>>
            joinDao.observeJoins(),              // Flow<List<GroupDeviceJoin>>
            preferencesRepository.phaseMode      // Flow<PhaseMode>
        ) { devices, joins, mode ->
            // компактные «сигнатуры» для отсечения повторов + текущий mode
            val devSig = devices.map { it.deviceId }.sorted()
            val joinSig = joins.map { it.groupId to it.deviceId }
                .sortedWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
            Triple(devSig to joinSig, mode, Unit)
        }
            .map { (pair, mode) -> Triple(pair.first.hashCode(), pair.second.hashCode(), mode) }
            .distinctUntilChanged()              // срежет одинаковые (dev, join, mode)
            .debounce(200)
            .onEach { (_, _, mode) ->
                withContext(Dispatchers.IO) {
                    // ВАЖНО: передаём режим!
                    calculatorFactory.create().calculateGroups(mode)
                    // Если метод у тебя называется иначе:
                    // calculatorFactory.create().calculate(mode)
                }
            }
            .launchIn(scope)
    }
}