package ru.mugalimov.volthome.domain.model

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

data class Room(
    val id: Long = 0, // До генерации в entity будет 0
    val name: String,
    val createdAt: Date,
    val devices: List<Device> = emptyList(),
    val roomType: RoomType = RoomType.STANDARD  // Тип помещения
)

@RequiresApi(Build.VERSION_CODES.O)
fun LocalDateTime.format(): String {
    return DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm")
        .format(this)
}
