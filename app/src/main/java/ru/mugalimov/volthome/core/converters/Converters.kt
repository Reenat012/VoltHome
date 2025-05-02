package ru.netology.nework.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import java.util.Date

class Converters {
    //учим БД читать класс Date
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromVoltage(voltage: Voltage): Int = voltage.value

    @TypeConverter
    fun toVoltage(value: Int): Voltage {
        return when (value) {
            220 -> Voltage.V220
            380 -> Voltage.V380
            else -> throw IllegalArgumentException("Unknown voltage value")
        }
    }

    @TypeConverter
    fun fromDeviceType(value: DeviceType): String = value.name

    @TypeConverter
    fun toDeviceType(value: String): DeviceType = DeviceType.valueOf(value)

    @TypeConverter
    fun fromDevicesList(devices: List<Device>): String {
        return Gson().toJson(devices)
    }

    @TypeConverter
    fun toDevicesList(value: String): List<Device> {
        val type = object : TypeToken<List<Device>>() {}.type
        return Gson().fromJson(value, type)
    }
}