package ru.mugalimov.volthome.ui.screens.rooms

import RoomCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Room

/**
 * Список комнат.
 * deviceCounts позволяет передать реальное количество устройств по id комнаты,
 * если Room.devices не загружены (ленивая загрузка).
 */
@Composable
fun RoomList(
    rooms: List<Room>,
    onClickRoom: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAddDevice: (Long) -> Unit = {},              // на будущее, сейчас карточка его не использует
    deviceCounts: Map<Long, Int> = emptyMap(),     // id комнаты -> число устройств
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        items(rooms, key = { it.id }) { room ->
            val count = deviceCounts[room.id] ?: room.devices.size

            RoomCard(
                room = room,
                devicesCount = count,
                onClick = { onClickRoom(room.id) },
                onDelete = { onDelete(room.id) }
            )
        }
    }
}