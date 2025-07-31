// Добавьте необходимые импорты в начале файла
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.SafetyDivider
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import ru.mugalimov.volthome.ui.screens.explication.GroupParameterRow
import ru.mugalimov.volthome.ui.screens.explication.WarningItem

@Composable
fun GroupCard(group: CircuitGroup) {
    // Рассчитываем процент загрузки группы
    val loadPercent = if (group.circuitBreaker > 0) {
        (group.nominalCurrent / group.circuitBreaker) * 100
    } else {
        0.0
    }

    // Определяем цвет индикатора в зависимости от загрузки
    val indicatorColor = when {
        loadPercent > 80 -> MaterialTheme.colorScheme.error
        loadPercent > 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    // Проверяем, содержит ли группа устройства с пусковыми токами
    val hasReactiveLoad = group.devices.any {
        it.powerFactor < 0.7 || it.hasMotor
    }

    Card(
        elevation = CardDefaults.cardElevation(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок карточки с номером группы
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ElectricalServices,
                    contentDescription = "Группа"
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Группа ${group.groupNumber}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Информация о комнате
            Text(
                text = "Комната: ${group.roomName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Параметры группы
            GroupParameterRow("Тип группы", group.groupType.name)
            GroupParameterRow("Кол-во устройств", "${group.devices.size}")
            GroupParameterRow("Автомат", "${group.circuitBreaker}A тип ${group.breakerType}")
            GroupParameterRow("Сечение кабеля", "${group.cableSection} мм²")
            GroupParameterRow("Расчетный ток, А", "%.2f".format(group.nominalCurrent))
            GroupParameterRow("Фаза", "${group.phase}")

            // Индикатор загрузки
            Spacer(modifier = Modifier.height(12.dp))
            Text("Загрузка группы: ${"%.1f".format(loadPercent)}%")
            LinearProgressIndicator(
                progress = (loadPercent / 100).toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = indicatorColor
            )

            // Запас мощности
            val reserve = group.circuitBreaker - group.nominalCurrent
            Text(
                text = "Запас: ${"%.1f".format(reserve)}A",
                color = if (reserve < 3) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            // Блок предупреждений
            if (group.rcdRequired || hasReactiveLoad) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Требования безопасности:",
                    style = MaterialTheme.typography.labelLarge
                )

                if (group.rcdRequired) {
                    WarningItem(
                        text = "Требуется УЗО на ${group.rcdCurrent}мА",
                        icon = Icons.Outlined.SafetyDivider
                    )
                }

                if (hasReactiveLoad) {
                    WarningItem(
                        text = "Содержит устройства с пусковыми токами",
                        icon = Icons.Outlined.Warning
                    )
                }
            }

            // Список устройств в группе
            Spacer(modifier = Modifier.height(12.dp))
            Text("Устройства:")
            group.devices.forEach { device ->
                Text(
                    text = "- ${device.name} (${device.power} Вт)",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}