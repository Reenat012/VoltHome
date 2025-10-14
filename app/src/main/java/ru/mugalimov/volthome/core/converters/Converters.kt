package ru.netology.nework.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
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
    fun fromVoltage(voltage: Voltage): String {
        return "${voltage.value}|${voltage.type.name}"
    }

    @TypeConverter
    fun toVoltage(value: String): Voltage {
        val parts = value.split("|")
        return Voltage(
            value = parts[0].toInt(),
            type = VoltageType.valueOf(parts[1])
        )
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

    @TypeConverter
    fun fromOutboxState(value: String?): ru.mugalimov.volthome.data.local.entity.OutboxState? {
        return value?.let { runCatching { ru.mugalimov.volthome.data.local.entity.OutboxState.valueOf(it) }.getOrNull() }
    }

    @TypeConverter
    fun toOutboxState(value: ru.mugalimov.volthome.data.local.entity.OutboxState?): String? {
        return value?.name
    }

    @TypeConverter
    fun fromTombstoneEntityType(value: String?): ru.mugalimov.volthome.data.local.entity.TombstoneEntityType? {
        return value?.let { runCatching { ru.mugalimov.volthome.data.local.entity.TombstoneEntityType.valueOf(it) }.getOrNull() }
    }

    @TypeConverter
    fun toTombstoneEntityType(value: ru.mugalimov.volthome.data.local.entity.TombstoneEntityType?): String? {
        return value?.name
    }
}