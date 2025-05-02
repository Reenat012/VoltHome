package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.domain.model.RoomWithLoad
import ru.mugalimov.volthome.ui.viewmodel.LoadsScreenViewModel

@Composable
fun LoadCard(
    roomWithLoad: RoomWithLoad,
    modifier: Modifier = Modifier,
    viewModel: LoadsScreenViewModel = hiltViewModel(),
    roomId: Long, // Получаем roomId из аргументов навигации
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
                text = "${roomWithLoad.room.name}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Суммарная мощность устройств = ${roomWithLoad.load?.powerRoom} Вт "
            )
            Text(
                text = "Суммарный ток устройств = ${
                    roomWithLoad.load?.currentRoom?.let {
                        "%.1f".format(it)
                    } ?: "N/A"
                } А"
            )
            Text(
                text = "Количество устройств = ${roomWithLoad.load?.countDevices}"
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