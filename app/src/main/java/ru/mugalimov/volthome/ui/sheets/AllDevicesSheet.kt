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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllDevicesSheet(
    defaultDevices: List<DefaultDevice>,
    onConfirm: (selected: List<DefaultDevice>) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, defaultDevices) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) defaultDevices else defaultDevices.filter { it.name.lowercase().contains(q) }
    }

    val qtyMap = remember { mutableStateMapOf<String, Int>() }
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

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
            // Заголовок + галочка
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Типовые устройства", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))

                IconButton(onClick = {
                    val result = buildList {
                        defaultDevices.forEach { d ->
                            val q = qtyMap[d.id.toString()] ?: 0
                            repeat(q) { add(d) }
                        }
                    }
                    onConfirm(result)
                }) {
                    Icon(Icons.Rounded.Check, contentDescription = "Добавить выбранные")
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск устройства…") },
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(filtered, key = { it.id }) { device ->
                    val key = device.id.toString()
                    val expanded = expandedMap[key] == true
                    val qty = qtyMap[key] ?: 0

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
                                        .clickable { expandedMap[key] = !expanded }
                                ) {
                                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                                    if (!expanded) {
                                        Text(
                                            "${device.power} Вт · Cos ф = ${device.powerFactor.format(2)} · К-т спроса = ${device.demandRatio.format(2)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                CounterPill(
                                    qty = qty,
                                    onDec = { if (qty > 0) qtyMap[key] = qty - 1 else qtyMap.remove(key) },
                                    onInc = { qtyMap[key] = qty + 1 }
                                )

                                Icon(
                                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .clickable { expandedMap[key] = !expanded }
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
            }

            // Нижние кнопки можно убрать, если достаточно галочки
            /*
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Отмена") }

                Button(
                    onClick = {
                        val result = buildList {
                            defaultDevices.forEach { d ->
                                val q = qtyMap[d.id.toString()] ?: 0
                                repeat(q) { add(d) }
                            }
                        }
                        onConfirm(result)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Добавить") }
            }
            */
        }
    }
}

@Composable
private fun CounterPill(qty: Int, onDec: () -> Unit, onInc: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("-", modifier = Modifier
                .clickable { onDec() }
                .padding(end = 10.dp),
                style = MaterialTheme.typography.titleSmall
            )
            Text(qty.toString(), style = MaterialTheme.typography.titleSmall)
            Text("+", modifier = Modifier
                .clickable { onInc() }
                .padding(start = 10.dp),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun ParamChips(device: DefaultDevice) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("${device.power} Вт")
        Chip("Cos ф ${device.powerFactor.format(2)}")
        Chip("К-т мощ-ти ${device.demandRatio.format(2)}")
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

private fun Double.format(digits: Int) = "%.${digits}f".format(this).replace(',', '.')

private fun Voltage.asLabel(): String {
    val phase = when (this.type) {
        VoltageType.AC_1PHASE -> "1ф"
        VoltageType.AC_3PHASE -> "3ф"
        VoltageType.DC -> "DC"
    }
    return if (this.type == VoltageType.DC) "${this.value}V • DC" else "${this.value}V • $phase"
}