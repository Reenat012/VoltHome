package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.mugalimov.volthome.model.Device
import ru.mugalimov.volthome.model.Load
import ru.mugalimov.volthome.ui.screens.room.CardDevice

//Список устройств
@Composable
fun CardList(
    loads: List<Load>,
//    onClickDevice: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(loads, key = { it.id }) { load ->
            CardLoad (
                load = load
            )
        }
    }
}