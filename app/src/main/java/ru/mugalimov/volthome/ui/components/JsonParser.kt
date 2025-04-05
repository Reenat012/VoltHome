package ru.mugalimov.volthome.ui.components

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DefaultRoom

object JsonParser {
    fun parseDevices(context: Context): Flow<List<DefaultDevice>> {
        return try {
            val inputStream = context.assets.open("default_devices.json")
            val json = inputStream.bufferedReader().use { it.readText() }
            val typeToken = object : TypeToken<List<DefaultDevice>>() {}.type
            val result = Gson().fromJson<List<DefaultDevice>>(json, typeToken)
            flowOf(result ?: emptyList())
        } catch (e: Exception) {
            flowOf(emptyList())
        }
    }

    fun parseRooms(context: Context): Flow<List<DefaultRoom>> {
        return try {
            val inputStream = context.assets.open("default_rooms.json")
            val json = inputStream.bufferedReader().use { it.readText() }
            val typeToken = object : TypeToken<List<DefaultRoom>>() {}.type
            val result = Gson().fromJson<List<DefaultRoom>>(json, typeToken)
            flowOf(result ?: emptyList())
        } catch (e: Exception) {
            flowOf(emptyList())
        }
    }
}
