package ru.mugalimov.volthome.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "uuid_map_rooms",
    indices = [Index(value = ["local_id"], unique = true)]
)
data class UuidMapRoom(
    @PrimaryKey @ColumnInfo(name = "room_uuid") val roomUuid: String,
    @ColumnInfo(name = "local_id") val localId: Long
)

@Entity(
    tableName = "uuid_map_groups",
    indices = [Index(value = ["local_id"], unique = true)]
)
data class UuidMapGroup(
    @PrimaryKey @ColumnInfo(name = "group_uuid") val groupUuid: String,
    @ColumnInfo(name = "local_id") val localId: Long
)

@Entity(
    tableName = "uuid_map_devices",
    indices = [Index(value = ["local_id"], unique = true)]
)
data class UuidMapDevice(
    @PrimaryKey @ColumnInfo(name = "device_uuid") val deviceUuid: String,
    @ColumnInfo(name = "local_id") val localId: Long
)