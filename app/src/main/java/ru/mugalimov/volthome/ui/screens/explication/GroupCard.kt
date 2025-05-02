package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.CircuitGroup

@Composable
fun GroupCard(
    group: CircuitGroup,
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
                text = "Номер группы ${group.groupNumber}"
            )
            Text(
                text = "Имя комнаты ${group.roomName}"
            )
            Text(
                text = "Тип группы ${group.groupType.name}"
            )
            Text(
                text = "Устройства в группе: ${group.devices.map { it.name }}"
            )
            Text(
                text = "Рекомендуемое сечение кабельной линии: ${group.cableSection} кв.мм"
            )
            Text(
                text = "Рекомендуемое значение номинального тока автоматического выключателя: ${group.circuitBreaker} А"
            )
            Text(
                text = "Расчетный ток группы: ${group.nominalCurrent.let { "%.2f".format(it) }} А"
            )

        }
    }
}