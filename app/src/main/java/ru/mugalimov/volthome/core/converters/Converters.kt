package ru.netology.nework.converters

import androidx.room.TypeConverter
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
}