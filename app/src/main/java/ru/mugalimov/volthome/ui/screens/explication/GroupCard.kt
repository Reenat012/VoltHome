// Добавьте необходимые импорты в начале файла
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.CircuitGroup

@Composable
fun GroupCard(
    group: CircuitGroup,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Заголовок
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Numbers,
                    contentDescription = "Номер группы",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) // Добавлена закрывающая скобка

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Группа ${group.groupNumber}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = group.roomName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Параметры группы
            GroupParameterRow(
                icon = Icons.Outlined.Category,
                label = "Тип группы",
                value = group.groupType.name
            )

            GroupParameterRow(
                icon = Icons.Outlined.Devices,
                label = "Устройства",
                value = group.devices.joinToString(", ") { it.name } // Добавлен разделитель
            )

            GroupParameterRow(
                icon = Icons.Outlined.Cable,
                label = "Сечение кабеля",
                value = "${group.cableSection} кв.мм"
            )

            GroupParameterRow(
                icon = Icons.Outlined.ElectricBolt,
                label = "Автоматический выключатель",
                value = "${group.circuitBreaker} А"
            )

            GroupParameterRow(
                icon = Icons.Outlined.Calculate,
                label = "Расчетный ток",
                value = "%.2f А".format(group.nominalCurrent)
            )
        }
    }
}

@Composable
private fun GroupParameterRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "$label иконка",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}