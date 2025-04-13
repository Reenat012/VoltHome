package ru.netology.nework.converters

import androidx.room.TypeConverter
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
}