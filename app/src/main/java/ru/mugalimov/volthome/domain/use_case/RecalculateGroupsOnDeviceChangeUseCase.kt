package ru.mugalimov.volthome.domain.use_case

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import javax.inject.Inject

/**
 * Авто‑пересчёт групп при изменении устройств/привязок.
 * Запускается один раз на всё приложение и живёт в App‑scoped ViewModel.
 */
class RecalculateGroupsOnDeviceChangeUseCase @Inject constructor(
    private val deviceDao: DeviceDao,
    private val joinDao: GroupDeviceJoinDao,
    private val calculatorFactory: GroupCalculatorFactory
) {
    fun launch(scope: CoroutineScope): Job {
        return combine(
            deviceDao.observeDevices(),   // Flow<List<DeviceEntity>>
            joinDao.observeJoins()        // Flow<List<GroupDeviceJoin>>
        ) { devices, joins ->
            // компактная "сигнатура" состояния для отсечения повторов
            val devSig = devices.map { it.deviceId }.sorted()
            val joinSig = joins.map { it.groupId to it.deviceId }
                .sortedWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
            devSig to joinSig
        }
            .map { (d, j) -> d.hashCode() to j.hashCode() }
            .distinctUntilChanged()
            .debounce(200) // сгладим дребезг при пакетных операциях
            .onEach {
                // считаем на IO, чтобы не блокировать Main
                withContext(Dispatchers.IO) {
                    calculatorFactory.create().calculateGroups()
                }
            }
            .launchIn(scope)
    }
}