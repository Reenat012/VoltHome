package ru.mugalimov.volthome.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Если у вас уже есть @Entity(tableName="rooms"), оставьте tableName тем же,
 * а поля добавятся миграциями. Здесь я показываю полный вид требуемых полей.
 */
@Entity(
    tableName = "rooms",
    indices = [
        Index("project_id"),
        Index(value = ["project_id","updated_at"]),
        Index(value = ["project_id","is_deleted"])
    ]
)
data class RoomEntityExt(
    @PrimaryKey val id: String,
    val name: String,
    val project_id: String,
    val updated_at: Instant,
    val is_deleted: Boolean
    // + любые ваши поля meta...
)