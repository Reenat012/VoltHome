package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Load
import ru.mugalimov.volthome.domain.model.RoomWithLoad
import ru.mugalimov.volthome.ui.navigation.BottomNavItem


//Список устройств
@Composable
fun LoadList(
    roomsWithLoads: List<RoomWithLoad>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(roomsWithLoads) { roomWithLoad ->
            LoadCard(
                roomWithLoad = roomWithLoad,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}