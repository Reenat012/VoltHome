package ru.mugalimov.volthome.ui.components

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.Voltage

object JsonParser {
    fun parseDevices(context: Context): Flow<List<DefaultDevice>> {
        return try {
            val gson = GsonBuilder()
                .registerTypeAdapter(Voltage::class.java, VoltageTypeAdapter())
                .create()

            val json = context.assets.open("default_devices.json")
                .bufferedReader().use { it.readText() }

            val devices = gson.fromJson<List<DefaultDevice>>(
                json,
                object : TypeToken<List<DefaultDevice>>() {}.type
            )

            flowOf(devices ?: emptyList())
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

class VoltageTypeAdapter : TypeAdapter<Voltage>() {
    override fun write(out: JsonWriter, value: Voltage) {
        out.value(value.value)
    }

    override fun read(`in`: JsonReader): Voltage {
        return when (`in`.nextInt()) {
            220 -> Voltage.V220
            380 -> Voltage.V380
            else -> throw IllegalArgumentException("Unknown voltage value")
        }
    }
}
