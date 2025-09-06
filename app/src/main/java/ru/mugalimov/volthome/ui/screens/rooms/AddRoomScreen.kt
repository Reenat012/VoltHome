package ru.mugalimov.volthome.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest

/**
 * ВАЖНО: Это обновлённый AddRoomSheet, который повторяет функциональность выбора устройств
 * «1 в 1» как на экране «Комната» (DevicePickerSheet):
 *  - выбор пресетов
 *  - степпер (– N +)
 *  - возможность сразу отредактировать Название и Мощность (всегда в Вт) перед вставкой
 *  - остальные параметры — read-only с иконкой замка
 *
 * Изменение SIG: onConfirm теперь возвращает список DeviceCreateRequest, т.к. мы сохраняем
 * кастомные поля инстансов (title + ratedPowerW) уже на этапе создания комнаты.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRoomSheet(
    defaultDevices: List<DefaultDevice>,
    roomTypes: List<RoomType>,
    onConfirm: (name: String, roomType: RoomType, devices: List<DeviceCreateRequest>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(roomTypes.firstOrNull() ?: RoomType.STANDARD) }

    // Количество по id
    val qtyMap = remember { mutableStateMapOf<String, Int>() }
    // Раскрытие карточек
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }
    // Переименования по id
    val nameOverride = remember { mutableStateMapOf<String, String>() }
    // Кастомная мощность (в текстовом виде) по id
    val powerOverride = remember { mutableStateMapOf<String, String>() }

    // Пресет при открытии
    LaunchedEffect(Unit) { applyPresetFor(selectedType, defaultDevices, qtyMap) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Заголовок + кнопка-галочка (без текста)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Добавить комнату",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = {
                    val requests = buildRequests(
                        defaults = defaultDevices,
                        qtyMap = qtyMap,
                        nameOverride = nameOverride,
                        powerOverride = powerOverride
                    )
                    onConfirm(
                        name.ifBlank { roomTypeLabel(selectedType) },
                        selectedType,
                        requests
                    )
                }) {
                    Icon(Icons.Rounded.Check, contentDescription = "Создать")
                }
            }

            Spacer(Modifier.padding(top = 12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название комнаты") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.padding(top = 10.dp))

            RoomTypeRow(
                types = roomTypes,
                selected = selectedType,
                onSelect = {
                    selectedType = it
                    applyPresetFor(it, defaultDevices, qtyMap)
                }
            )

            Spacer(Modifier.padding(top = 12.dp))
            Text("Устройства", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.padding(top = 8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(defaultDevices, key = { it.id }) { device ->
                    val key = device.id.toString()
                    val expanded = expandedMap[key] == true
                    val qty = qtyMap[key] ?: 0

                    DeviceRowEditable(
                        device = device,
                        expanded = expanded,
                        qty = qty,
                        title = nameOverride[key] ?: device.name,
                        powerText = powerOverride[key] ?: device.power.toString(),
                        onToggle = { expandedMap[key] = !(expandedMap[key] ?: false) },
                        onInc = { qtyMap[key] = (qtyMap[key] ?: 0).plus(1).coerceAtMost(99) },
                        onDec = { qtyMap[key] = (qtyMap[key] ?: 0).minus(1).coerceAtLeast(0) },
                        onTitleChange = { nameOverride[key] = it },
                        onPowerChange = { powerOverride[key] = it.replace(',', '.') }
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
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { t ->
            val isSel = t == selected
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
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

/**
 * Карточка устройства (как в DevicePickerSheet):
 *  - верх: название пресета + текущая мощность + степпер (– N +) и стрелка
 *  - при раскрытии: редактируемые поля Название и Мощность (Вт),
 *    затем — read-only поля с замком: Тип устройства, cos φ, Коэфф. спроса, Напряжение
 */
@Composable
private fun DeviceRowEditable(
    device: DefaultDevice,
    expanded: Boolean,
    qty: Int,
    title: String,
    powerText: String,
    onToggle: () -> Unit,
    onInc: () -> Unit,
    onDec: () -> Unit,
    onTitleChange: (String) -> Unit,
    onPowerChange: (String) -> Unit
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
                            "${device.power} Вт",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Степпер (– N +) с выравниванием по центру
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onDec) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Уменьшить")
                    }
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                        Text(qty.toString())
                    }
                    IconButton(onClick = onInc) {
                        Icon(Icons.Rounded.Add, contentDescription = "Увеличить")
                    }
                }

                IconButton(onClick = { onToggle() }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {

                    // Редактируемые поля
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.padding(top = 8.dp))

                    OutlinedTextField(
                        value = powerText,
                        onValueChange = onPowerChange,
                        label = { Text("Мощность (Вт)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.padding(top = 12.dp))
                    Divider()
                    Spacer(Modifier.padding(top = 12.dp))

                    // Read-only поля с замком
                    ReadonlyField(label = "Тип устройства", value = deviceTypeLabel(device.deviceType))
                    Spacer(Modifier.padding(top = 8.dp))
                    ReadonlyField(label = "Cos φ", value = device.powerFactor.format(2))
                    Spacer(Modifier.padding(top = 8.dp))
                    ReadonlyField(label = "Коэфф. спроса", value = device.demandRatio.format(2))
                    Spacer(Modifier.padding(top = 8.dp))
                    ReadonlyField(label = "Напряжение", value = voltageHuman(device.voltage))
                }
            }
        }
    }
}

@Composable
private fun ReadonlyField(label: String, value: String) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        enabled = false,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/* ====== СБОРКА ЗАПРОСОВ СО ВСЕМИ КАСТОМАМИ (как в DevicePickerSheet) ====== */

private fun buildRequests(
    defaults: List<DefaultDevice>,
    qtyMap: Map<String, Int>,
    nameOverride: Map<String, String>,
    powerOverride: Map<String, String>
): List<DeviceCreateRequest> {
    val byId = defaults.associateBy { it.id.toString() }
    val out = mutableListOf<DeviceCreateRequest>()
    for ((key, count) in qtyMap) {
        if (count <= 0) continue
        val def = byId[key] ?: continue

        val title = (nameOverride[key] ?: def.name).trim().ifEmpty { def.name }
        val powerRaw = (powerOverride[key] ?: def.power.toString())
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?: def.power.toDouble()
        val watts = powerRaw.toInt().coerceAtLeast(1) // всегда Вт

        out += DeviceCreateRequest(
            title = title,
            type = def.deviceType,
            count = count,
            ratedPowerW = watts,
            powerFactor = def.powerFactor,
            demandRatio = def.demandRatio,
            voltage = def.voltage
        )
    }
    return out
}

/* ====== ХЕЛПЕРЫ UI ====== */

private fun roomTypeLabel(type: RoomType): String = when (type) {
    RoomType.STANDARD -> "Стандартная"
    RoomType.BATHROOM -> "Ванная (УЗО)"
    RoomType.KITCHEN -> "Кухня (УЗО)"
    RoomType.OUTDOOR -> "Улица (УЗО)"
}

private fun deviceTypeLabel(type: DeviceType): String = type.name

private fun Double.format(digits: Int) = "%.${digits}f".format(this).replace(',', '.')

private fun voltageHuman(v: Voltage): String = "${v.value} В"

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
            addFirstOf(DeviceType.HEAVY_DUTY, 1)
        }
        RoomType.KITCHEN -> {
            addFirstOf(DeviceType.LIGHTING, 1)
            addFirstOf(DeviceType.SOCKET, 2)
            addFirstOf(DeviceType.HEAVY_DUTY, 1)
        }
        RoomType.OUTDOOR -> {
            addFirstOf(DeviceType.SOCKET, 1)
        }
    }
}