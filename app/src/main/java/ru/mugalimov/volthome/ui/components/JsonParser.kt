package ru.mugalimov.volthome.ui.components

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageTypeAdapter

object JsonParser {

    /** Читает default_devices.json из assets. Один эмит, чтение на IO. */
    fun parseDevices(context: Context): Flow<List<DefaultDevice>> = flow {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("default_devices.json").use { input ->
                    val json = input.bufferedReader().use { it.readText() }
                    val gson = GsonBuilder()
                        .registerTypeAdapter(Voltage::class.java, VoltageTypeAdapter())
                        .create()
                    val type = object : TypeToken<List<DefaultDevice>>() {}.type
                    gson.fromJson<List<DefaultDevice>>(json, type) ?: emptyList()
                }
            }.onFailure { e ->
                Log.e("JsonParser", "Error parsing devices", e)
            }.getOrElse { emptyList() }
        }
        emit(result)
    }

    /** Читает default_rooms.json из assets. Один эмит, чтение на IO. */
    fun parseRooms(context: Context): Flow<List<DefaultRoom>> = flow {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("default_rooms.json").use { input ->
                    val json = input.bufferedReader().use { it.readText() }
                    val gson = GsonBuilder()
                        .registerTypeAdapter(RoomType::class.java, RoomTypeAdapter())
                        .create()
                    val type = object : TypeToken<List<DefaultRoomJson>>() {}.type
                    val items = gson.fromJson<List<DefaultRoomJson>>(json, type).orEmpty()
                    items.map { j ->
                        DefaultRoom(
                            id = j.id,
                            name = j.name,
                            icon = j.icon,
                            description = j.description,
                            roomType = j.roomType
                        )
                    }
                }
            }.onFailure { e ->
                Log.e("JsonParser", "Error parsing rooms", e)
            }.getOrElse { emptyList() }
        }
        emit(result)
    }

    // Временный DTO для парсинга JSON
    private data class DefaultRoomJson(
        val id: Long,
        val name: String,
        val icon: String,
        val description: String,
        val roomType: RoomType
    )
}