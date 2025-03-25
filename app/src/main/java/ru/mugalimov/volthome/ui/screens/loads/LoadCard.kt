package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.model.RoomWithLoad

@Composable
fun LoadCard(
    roomWithLoad: RoomWithLoad,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Комната: ${roomWithLoad.room.name}",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

//fun CardLoad(load: Load) {
//    Card(
//        modifier = Modifier
//            .padding(8.dp)
//            .fillMaxWidth()
////            .clickable { onClickDevice() } // Обработчик клика на карточку
//    ) {
//        Row(
//            modifier = Modifier.fillMaxSize(),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Column(
//                modifier = Modifier
//                    .weight(1f) // Занимает всё свободное пространство
//                    .padding(16.dp),
////            verticalAlignment = Alignment.CenterVertically // Выравнивание по вертикали
//            ) {
//                Text(
//                    text = load.name,
////                modifier = Modifier.weight(1f),
//                    style = MaterialTheme.typography.titleMedium
//                )
//
//            }
//        }
//    }
//}