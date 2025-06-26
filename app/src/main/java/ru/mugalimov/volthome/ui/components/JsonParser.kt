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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DefaultRoom
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageTypeAdapter

object JsonParser {
    fun parseDevices(context: Context): Flow<List<DefaultDevice>> = flow {
        try {
            Log.d("JsonParser", "Trying to open default_devices.json")
            val fileNames = context.assets.list("")?.joinToString()
            Log.d("JsonParser", "Available files: $fileNames")

            val inputStream = context.assets.open("default_devices.json")
            val size = inputStream.available()
            Log.d("JsonParser", "File size: $size bytes")

            val json = inputStream.bufferedReader().use { it.readText() }
            Log.d("JsonParser", "File content: ${json.take(500)}...") // Первые 500 символов

            val gson = GsonBuilder()
                .registerTypeAdapter(Voltage::class.java, VoltageTypeAdapter())
                .create()

            val typeToken = object : TypeToken<List<DefaultDevice>>() {}.type
            val result = gson.fromJson<List<DefaultDevice>>(json, typeToken)

            Log.d("JsonParser", "Parsed ${result?.size ?: 0} devices")
            emit(result ?: emptyList())
        } catch (e: Exception) {
            Log.e("JsonParser", "Error parsing devices", e)
            emit(emptyList())
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

