package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DeviceSpecUi
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupCardCompact(
    group: CircuitGroup,
    onEdit: (() -> Unit)? = null
) {
    var expanded by rememberSaveable(group.groupNumber) { mutableStateOf(false) }
    var hint by remember { mutableStateOf<GroupHint?>(null) }

    // каталог дефолтных устройств
    val roomVm: RoomDetailViewModel = hiltViewModel()
    LaunchedEffect(Unit) { roomVm.loadDefaultDevices() }
    val defaultDevices by roomVm.defaultDevices.collectAsState(initial = emptyList())

    // единый BottomSheet для всего
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var sheetDevice by remember { mutableStateOf<DeviceSpecUi?>(null) }

    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            // Заголовок + «что это?»
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Группа ${group.groupNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = group.roomName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    hint = GroupHint.HEADER
                    scope.launch { sheetState.show() }
                }) {
                    Icon(Icons.Outlined.Info, contentDescription = "Что это?")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Параметр-бейджи: Автомат • Мощность • Ток — клики открывают bottom sheet
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParamBadge(
                    icon = Icons.Outlined.ElectricalServices,
                    text = "${group.breakerType}${group.circuitBreaker}",
                    onClick = {
                        hint = GroupHint.BREAKER
                        scope.launch { sheetState.show() }
                    }
                )
                ParamBadge(
                    icon = Icons.Outlined.Bolt,
                    text = "${kw(group.devicesPowerW())} кВт",
                    onClick = {
                        hint = GroupHint.POWER
                        scope.launch { sheetState.show() }
                    }
                )
                ParamBadge(
                    icon = Icons.Outlined.ElectricBolt,
                    text = "${amp(group.nominalCurrent)} А",
                    onClick = {
                        hint = GroupHint.CURRENT
                        scope.launch { sheetState.show() }
                    }
                )
                if (onEdit != null) {
                    AssistChip(onClick = onEdit, label = { Text("Редактировать") })
                }
            }

            // Чипы устройств — кликабельные
            if (group.devices.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                DeviceChips(
                    devices = group.devices.map { it.name },
                    onDeviceClick = { name ->
                        val found = defaultDevices.firstOrNull { it.name == name }
                        val real = group.devices.firstOrNull { it.name == name }
                        sheetDevice = DeviceSpecUi(
                            name = name,
                            power = found?.power ?: (real?.power ?: 0),
                            voltage = found?.voltage?.value ?: real?.voltage?.value,
                            demandRatio = found?.demandRatio,
                            powerFactor = found?.powerFactor ?: real?.powerFactor,
                            deviceType = found?.deviceType?.name ?: real?.deviceType?.name,
                            hasMotor = found?.hasMotor ?: (real?.hasMotor ?: false),
                            requiresDedicatedCircuit = found?.requiresDedicatedCircuit
                                ?: (real?.requiresDedicatedCircuit ?: false),
                            requiresSocketConnection = found?.requiresSocketConnection
                                ?: real?.requiresSocketConnection
                        )
                        scope.launch { sheetState.show() }
                    }
                )
            }

            // Загрузка
            Spacer(Modifier.height(12.dp))
            val load = (if (group.circuitBreaker > 0) group.nominalCurrent / group.circuitBreaker else 0.0)
                .coerceAtLeast(0.0)
            val barColor = when {
                load > 0.8 -> MaterialTheme.colorScheme.error
                load > 0.6 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Загрузка", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = load.coerceAtMost(1.0).toFloat(),
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = barColor
                )
                Spacer(Modifier.width(8.dp))
                Text("${"%.0f".format(load * 100)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Разворачиваемые детали
            AnimatedVisibility(visible = expanded) {
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

    // ЕДИНЫЙ bottom sheet: если выбран девайс — показываем его карточку, иначе — текст подсказки
    if (sheetDevice != null || hint != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    sheetDevice = null; hint = null
                }
            },
            sheetState = sheetState
        ) {
            if (sheetDevice != null) {
                DeviceSpecSheet(
                    device = sheetDevice!!,
                    onDismiss = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { sheetDevice = null }
                    },
                    sheetState = sheetState
                )
            } else {
                val (title, text) = groupHintContent(hint!!, group)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
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
private fun ParamBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
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
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// --- контент для bottom sheet подсказок ---
private fun groupHintContent(hint: GroupHint, group: CircuitGroup): Pair<String, String> = when (hint) {
    GroupHint.HEADER -> "Карточка группы" to
            "Здесь параметры группы: автомат, мощность, ток и состав устройств. Блок помогает быстро оценить загрузку, требования к защите и необходимость перераспределения."
    GroupHint.BREAKER -> {
        val curveChar = group.breakerType.firstOrNull()?.uppercaseChar() ?: 'C'
        breakerDialog(curveChar, group.circuitBreaker)
    }
    GroupHint.POWER -> "Мощность группы" to
            "Сумма мощностей устройств в группе. Показана в кВт: P = ΣP(устройств). Используется для проверки нагрузки и распределения по фазам."
    GroupHint.CURRENT -> "Расчётный ток" to
            "Ток из расчёта нагрузки. Сравнивай с номиналом автомата; рабочую загрузку держи ≤ 80% для стабильной работы."
}

// --- helpers ---
private fun kw(watts: Int): String = "%.1f".format(watts / 1000.0)
private fun amp(a: Double): String = "%.1f".format(a)
private fun CircuitGroup.devicesPowerW(): Int = devices.sumOf { it.power ?: 0 }

// контекстное пояснение по кривой автомата
private fun breakerDialog(curve: Char, nominalA: Int): Pair<String, String> {
    val title = "Тип автомата: $curve$nominalA"
    val common =
        "Формат «$curve$nominalA»: буква — кривая мгновенного отключения (чувствительность к пусковым токам), число — номинал в амперах."

    val body = when (curve) {
        'B' -> "Кривая B ≈ 3–5×In. Когда: активные нагрузки и длинные линии (освещение, электроника). Избегать для двигателей."
        'C' -> "Кривая C ≈ 5–10×In. Дефолт для розеточных/смешанных групп: баланс между пусками и защитой линии."
        'D' -> "Кривая D ≈ 10–20×In. Для больших пусков (двигатели, насосы, сварка). Требует достаточного тока КЗ; на длинных линиях часто недопустим."
        else -> "Обычно используют B, C или D."
    }
    val tip = "\n\nНоминал выбирают по расчётному току и сечению кабеля. Для кривой D обязательно проверь условие отключения по току КЗ."
    return title to "$common\n\n$body$tip"
}