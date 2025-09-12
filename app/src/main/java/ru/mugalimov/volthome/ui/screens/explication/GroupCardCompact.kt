package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceSpecUi

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupCardCompact(
    group: CircuitGroup,
    onEdit: (() -> Unit)? = null,
    onDeviceClick: (Long) -> Unit // наружу — id инстанса
) {
    val expanded = rememberSaveable(group.groupNumber) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // единый BottomSheet по карточке
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetDevice = remember { mutableStateOf<DeviceSpecUi?>(null) }
    val hint = remember { mutableStateOf<GroupHint?>(null) }

    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .clickable { expanded.value = !expanded.value }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Группа ${group.groupNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp)) // ← горизонтальный отступ
                Text(
                    text = group.roomName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    hint.value = GroupHint.HEADER
                    scope.launch { sheetState.show() }
                }) { Icon(Icons.Outlined.Info, contentDescription = null) }
            }

            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParamBadge(
                    icon = Icons.Outlined.ElectricalServices,
                    text = "${group.breakerType}${group.circuitBreaker}"
                ) {
                    hint.value = GroupHint.BREAKER
                    scope.launch { sheetState.show() }
                }
                ParamBadge(
                    icon = Icons.Outlined.Bolt,
                    text = "${kw(group.devices.sumOf { it.power })} кВт"
                ) {
                    hint.value = GroupHint.POWER
                    scope.launch { sheetState.show() }
                }
                ParamBadge(
                    icon = Icons.Outlined.ElectricBolt,
                    text = "${amp(group.nominalCurrent)} А"
                ) {
                    hint.value = GroupHint.CURRENT
                    scope.launch { sheetState.show() }
                }
                if (onEdit != null) {
                    AssistChip(onClick = onEdit, label = { Text("Редактировать") })
                }
            }

            if (group.devices.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                DeviceChips(
                    devices = group.devices,
                    onDeviceClick = { id ->
                        val real = group.devices.firstOrNull { it.id == id }
                        if (real != null) {
                            sheetDevice.value = DeviceSpecUi(
                                name = real.name,
                                power = real.power,
                                voltage = real.voltage.value,
                                demandRatio = real.demandRatio,
                                powerFactor = real.powerFactor,
                                deviceType = real.deviceType.name,
                                hasMotor = real.hasMotor,
                                requiresDedicatedCircuit = real.requiresDedicatedCircuit,
                                requiresSocketConnection = real.requiresSocketConnection
                            )
                            scope.launch { sheetState.show() }
                        }
                        onDeviceClick(id)
                    },
                    maxVisible = 3,
                    groupKey = group.groupNumber
                )
                Spacer(Modifier.height(8.dp))
//                Divider()
            }

            // Прогресс загрузки группы — вернули «капсулу» и нормальные горизонтальные отступы
            val load = (if (group.circuitBreaker > 0) group.nominalCurrent / group.circuitBreaker else 0.0)
                .coerceAtLeast(0.0)
            val barColor = when {
                load > 0.8 -> MaterialTheme.colorScheme.error
                load > 0.6 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Загрузка",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp)) // ← горизонтальный отступ
                LinearProgressIndicator(
                    progress = load.coerceAtMost(1.0).toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)                         // чуть толще
                        .clip(RoundedCornerShape(50)),         // ← скругляем, «капсула»
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.width(8.dp)) // ← горизонтальный отступ
                Text(
                    "${"%.0f".format(load * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded.value) {
                Column {
                    Spacer(Modifier.height(12.dp)); Divider(); Spacer(Modifier.height(12.dp))
                    GroupParameterRow("Тип группы", group.groupType.name)
                    GroupParameterRow("Сечение кабеля", "${group.cableSection} мм²")
                    GroupParameterRow("Фаза", "${group.phase}")
                    if (group.rcdRequired) {
                        GroupParameterRow("УЗО", "${group.rcdCurrent} мА")
                    }
                }
            }
        }
    }

    if (sheetDevice.value != null || hint.value != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    sheetDevice.value = null
                    hint.value = null
                }
            },
            sheetState = sheetState
        ) {
            if (sheetDevice.value != null) {
                DeviceSpecSheet(
                    device = sheetDevice.value!!,
                    onDismiss = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            sheetDevice.value = null
                        }
                    },
                    sheetState = sheetState
                )
            } else {
                val (title, text) = groupHintContent(hint.value!!, group)
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private enum class GroupHint { HEADER, BREAKER, POWER, CURRENT }

@Composable
private fun ParamBadge(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp)) // ← внутри бейджа тоже ширина
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun kw(watts: Int): String = "%.2f".format(watts / 1000.0)
private fun amp(a: Double): String = "%.2f".format(a)

private fun groupHintContent(hint: GroupHint, group: CircuitGroup): Pair<String, String> = when (hint) {
    GroupHint.HEADER -> "Карточка группы" to
            "Здесь параметры группы: автомат, мощность, ток и состав устройств. Блок помогает быстро оценить загрузку, требования к защите и необходимость перераспределения."
    GroupHint.BREAKER -> {
        val curveChar = group.breakerType.firstOrNull()?.uppercaseChar() ?: 'C'
        val title = "Тип автомата: $curveChar${group.circuitBreaker}"
        val common = "Формат «$curveChar${group.circuitBreaker}»: буква — кривая мгновенного отключения, число — номинал, А."
        val body = when (curveChar) {
            'B' -> "Кривая B ≈ 3–5×In. Для активных нагрузок и длинных линий."
            'C' -> "Кривая C ≈ 5–10×In. Дефолт для розеточных/смешанных групп."
            'D' -> "Кривая D ≈ 10–20×In. Для больших пусков (двигатели, насосы, сварка)."
            else -> "Обычно используют B, C или D."
        }
        title to "$common\n\n$body"
    }
    GroupHint.POWER -> "Мощность группы" to
            "Сумма мощностей устройств в группе, используется для проверки нагрузки и распределения по фазам."
    GroupHint.CURRENT -> "Расчётный ток" to
            "Сравните с номиналом автомата; рабочую загрузку держите ≤ 80%."
}