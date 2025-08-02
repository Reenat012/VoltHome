package ru.mugalimov.volthome.ui.screens.loads



import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

@Composable
fun PhaseGroupTableItem(item: PhaseLoadItem) {
    Column {
        Text(
            text = "Фаза ${item.phase.name}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        item.groups.forEach { group ->
            Text(
                text = "— Группа №${group.groupNumber} (${group.roomName}): " +
                        "${group.devices.joinToString()} " +
                        "Мощность = ${group.totalPower.toInt()} Вт, " +
                        "Ток = %.2f А".format(group.totalCurrent),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
