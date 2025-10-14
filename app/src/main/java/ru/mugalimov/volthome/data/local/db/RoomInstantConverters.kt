package ru.mugalimov.volthome.data.local.db

import androidx.room.TypeConverter
import java.time.Instant

class RoomInstantConverters {
    @TypeConverter fun fromInstant(value: Instant?): String? = value?.toString()
    @TypeConverter fun toInstant(value: String?): Instant? = value?.let { Instant.parse(it) }
}