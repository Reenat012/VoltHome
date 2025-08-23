package ru.mugalimov.volthome.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRoomSheet(
    defaultDevices: List<DefaultDevice>,
    roomTypes: List<RoomType>,
    onConfirm: (name: String, roomType: RoomType, selected: List<Pair<DefaultDevice, Int>>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(roomTypes.firstOrNull() ?: RoomType.STANDARD) }

    val qtyMap = remember { mutableStateMapOf<String, Int>() }
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

    // Пресет при открытии
    LaunchedEffect(Unit) { applyPresetFor(selectedType, defaultDevices, qtyMap) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Заголовок + кнопка-галочка
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Добавить комнату", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))

                IconButton(onClick = {
                    val selected = defaultDevices.mapNotNull { d ->
                        val q = qtyMap[d.id.toString()] ?: 0
                        if (q > 0) d to q else null
                    }
                    onConfirm(
                        name.ifBlank { roomTypeLabel(selectedType) },
                        selectedType,
                        selected
                    )
                }) {
                    Icon(Icons.Rounded.Check, contentDescription = "Создать")
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название комнаты") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            RoomTypeRow(
                types = roomTypes,
                selected = selectedType,
                onSelect = {
                    selectedType = it
                    applyPresetFor(it, defaultDevices, qtyMap)
                }
            )

            Spacer(Modifier.height(12.dp))
            Text("Устройства", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(defaultDevices, key = { it.id }) { device ->
                    val key = device.id.toString()
                    val expanded = expandedMap[key] == true
                    val qty = qtyMap[key] ?: 0

                    DeviceRowExpandable(
                        device = device,
                        expanded = expanded,
                        qty = qty,
                        onToggle = { expandedMap[key] = !(expandedMap[key] ?: false) },
                        onInc = { qtyMap[key] = (qtyMap[key] ?: 0) + 1 },
                        onDec = { if ((qtyMap[key] ?: 0) > 0) qtyMap[key] = (qtyMap[key] ?: 0) - 1 }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomTypeRow(
    types: List<RoomType>,
    selected: RoomType,
    onSelect: (RoomType) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { t ->
            val isSel = t == selected
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
            ) {
                Text(
                    roomTypeLabel(t),
                    modifier = Modifier
                        .clickable { onSelect(t) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/** Карточка устройства: клик по левой части раскрывает read‑only параметры, справа — счётчик */
@Composable
private fun DeviceRowExpandable(
    device: DefaultDevice,
    expanded: Boolean,
    qty: Int,
    onToggle: () -> Unit,
    onInc: () -> Unit,
    onDec: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggle() }
                ) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    if (!expanded) {
                        Text(
                            "${device.power} Вт · PF ${device.powerFactor.format(2)} · DR ${device.demandRatio.format(2)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Counter(qty = qty, onInc = onInc, onDec = onDec)

                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    ParamChips(device)
                }
            }
        }
    }
}

@Composable
private fun Counter(qty: Int, onInc: () -> Unit, onDec: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "-",
                    modifier = Modifier
                        .clickable { onDec() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(qty.toString(), modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    "+",
                    modifier = Modifier
                        .clickable { onInc() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun ParamChips(device: DefaultDevice) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("${device.power} Вт")
        Chip("PF ${device.powerFactor.format(2)}")
        Chip("DR ${device.demandRatio.format(2)}")
        Chip(device.voltage.asLabel())
        Chip("Тип: ${device.deviceType.name}")
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun roomTypeLabel(type: RoomType): String = when (type) {
    RoomType.STANDARD -> "Стандартная"
    RoomType.BATHROOM -> "Ванная (УЗО)"
    RoomType.KITCHEN  -> "Кухня (УЗО)"
    RoomType.OUTDOOR  -> "Улица (УЗО)"
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this).replace(',', '.')

private fun Voltage.asLabel(): String {
    val phase = when (this.type) {
        VoltageType.AC_1PHASE -> "1ф"
        VoltageType.AC_3PHASE -> "3ф"
        VoltageType.DC -> "DC"
    }
    return if (this.type == VoltageType.DC) "${this.value}V • DC" else "${this.value}V • $phase"
}

/* ====== ПРЕСЕТЫ ПОД ТИП КОМНАТЫ ====== */

private fun applyPresetFor(
    type: RoomType,
    all: List<DefaultDevice>,
    qtyMap: MutableMap<String, Int>
) {
    qtyMap.clear()

    fun addFirstOf(dt: DeviceType, count: Int = 1) {
        val item = all.firstOrNull { it.deviceType == dt } ?: return
        qtyMap[item.id.toString()] = count
    }

    when (type) {
        RoomType.STANDARD -> {
            addFirstOf(DeviceType.LIGHTING, 1)
            addFirstOf(DeviceType.SOCKET, 1)
        }
        RoomType.BATHROOM -> {
            addFirstOf(DeviceType.LIGHTING, 1)
            addFirstOf(DeviceType.SOCKET, 1)
            addFirstOf(DeviceType.HEAVY_DUTY, 1) // например, бойлер, если есть
        }
        RoomType.KITCHEN -> {
            addFirstOf(DeviceType.LIGHTING, 1)
            // розеточные чаще кратно двум
            addFirstOf(DeviceType.SOCKET, 2)
            addFirstOf(DeviceType.HEAVY_DUTY, 1) // духовой шкаф/варочная, если есть
        }
        RoomType.OUTDOOR -> {
            addFirstOf(DeviceType.SOCKET, 1)
            // подсветка по желанию
        }
    }
}