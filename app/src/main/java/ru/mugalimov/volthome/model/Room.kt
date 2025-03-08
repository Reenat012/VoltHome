package ru.mugalimov.volthome.model

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

data class Room(
    val id: Int,
    val name: String,
    val createdAt: Date
)

@RequiresApi(Build.VERSION_CODES.O)
fun LocalDateTime.format(): String {
    return DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm")
        .format(this)
}
