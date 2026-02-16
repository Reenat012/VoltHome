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
            deviceDao.observeAllDevices(),          // вход: устройства
            joinDao.observeJoins(),                 // вход: membership
            preferencesRepository.phaseMode         // вход: режим фаз
        ) { devices, joins, mode ->
            // Сигнатуры для distinctUntilChanged.
            val devSig = devices.map { it.deviceId }.sorted()

            val joinSig = joins.map { it.groupId to it.deviceId }
                .sortedWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })

            Triple(devSig, joinSig, mode)
        }
            .distinctUntilChanged()
            .debounce(200)
            .onEach { (devSig, joinSig, mode) ->
                // Лог триггера (для расследований циклов).
                // Комментарий: пока без projectId (у flows глобальные), но уже можно видеть "что поменялось".
                android.util.Log.w(
                    "AUTO_RECALC_TRIGGER",
                    "trigger=DEVICE_OR_JOIN_OR_MODE " +
                            "devices=${devSig.size} joins=${joinSig.size} mode=$mode " +
                            "devIds(sample)=${devSig.take(12)} joins(sample)=${joinSig.take(12)}"
                )

                withContext(Dispatchers.IO) {
                    calculatorFactory.create().calculateGroups(mode)
                }
            }
            .launchIn(scope)
    }
}