package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.LoadListItem
import ru.mugalimov.volthome.domain.model.RoomWithLoad
import ru.mugalimov.volthome.domain.model.TotalLoad


// Список устройств
@Composable
fun LoadList(
    items: List<LoadListItem>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            // Обрабатываем разные типы элементов
            when (item) {
                is LoadListItem.Total -> TotalLoadCard(totalLoad = item.totalLoad)
                is LoadListItem.Room -> LoadCard(roomWithLoad = item.roomWithLoad)
            }
        }
    }
}