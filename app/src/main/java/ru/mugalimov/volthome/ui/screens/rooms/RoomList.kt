package ru.mugalimov.volthome.ui.screens.rooms

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.mugalimov.volthome.domain.model.Room

//Список комнат
@Composable
fun RoomList(
    rooms: List<Room>,
    onClickRoom: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(rooms, key = { it.id }) { room ->
            RoomCard (
                room = room,
                onDelete = { onDelete(room.id) },
                onClickRoom = { onClickRoom(room.id) } // Передаем ID комнат
            )
        }
    }
}

