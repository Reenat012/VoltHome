package ru.mugalimov.volthome.data.local.model

import androidx.room.ColumnInfo
import ru.mugalimov.volthome.domain.model.RoomType

data class RoomWithDevicesPreviewDb(
    @ColumnInfo(name = "roomId")
    val roomId: Long,

    @ColumnInfo(name = "roomName")
    val roomName: String,

    @ColumnInfo(name = "roomType")
    val roomType: RoomType,

    @ColumnInfo(name = "devicesCount")
    val devicesCount: Int,

    @ColumnInfo(name = "previewName1")
    val previewName1: String?,

    @ColumnInfo(name = "previewName2")
    val previewName2: String?,

    @ColumnInfo(name = "previewName3")
    val previewName3: String?
)