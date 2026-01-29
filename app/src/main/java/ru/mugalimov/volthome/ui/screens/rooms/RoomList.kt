package ru.mugalimov.volthome.ui.screens.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Список комнат (реактивная витрина RoomsList).
 * Единственный источник истины для карточки — RoomWithDevicesPreviewUi.
 */
@Composable
fun RoomList(
    rooms: List<RoomWithDevicesPreviewUi>,
    onClickRoom: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        items(
            items = rooms,
            key = { it.roomId },
            contentType = { "room_card" } // ✅ стабилизируем реюз/измерения айтемов
        ) { room ->
            RoomCard(
                room = room,
                onClick = { onClickRoom(room.roomId) },
                onDelete = { onDelete(room.roomId) }
            )
        }
    }
}