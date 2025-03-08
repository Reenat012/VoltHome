package ru.mugalimov.volthome.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.mugalimov.volthome.model.Room
import ru.mugalimov.volthome.ui.screens.RoomCard

//Список комнат
@Composable
fun RoomList(
    rooms: List<Room>,
    onClickRoom: (Int) -> Unit,
    onDelete: (Int) -> Unit,
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

