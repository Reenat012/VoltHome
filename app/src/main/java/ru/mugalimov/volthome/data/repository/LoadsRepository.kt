package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.Load

interface LoadsRepository {
    //получаем список нагрузок
    suspend fun observeLoads() : Flow<List<Load>>

    suspend fun addLoads(name: String, current: Double, sumPower: Int, countDevice: Int, roomId: Long)
}