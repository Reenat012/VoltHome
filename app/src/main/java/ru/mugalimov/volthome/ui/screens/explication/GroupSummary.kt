package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceType

@Composable
fun GroupSummary(groups: List<CircuitGroup>) {
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Сводка по электрическому щиту",
                style = MaterialTheme.typography.titleLarge
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Общее количество групп
            SummaryRow("Общее количество групп", "${groups.size}")

            // Суммарный ток
            val totalCurrent = groups.sumOf { it.nominalCurrent }
            SummaryRow("Суммарный ток", "%.2f А".format(totalCurrent))

            // Количество групп с УЗО
            val rcdGroups = groups.count { it.rcdRequired }
            SummaryRow("Групп с УЗО", "$rcdGroups")

            // Количество мощных групп
            val heavyGroups = groups.count { it.groupType == DeviceType.HEAVY_DUTY }
            SummaryRow("Выделенных линий", "$heavyGroups")
        }
    }
}