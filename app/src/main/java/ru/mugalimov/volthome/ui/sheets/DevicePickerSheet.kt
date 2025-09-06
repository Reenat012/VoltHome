package ru.mugalimov.volthome.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest as DomainDeviceCreateRequest
import ru.mugalimov.volthome.domain.use_case.AddDevicesToRoomUseCase

/**
 * BottomSheet добавления устройств с немедленной кастомизацией.
 * Требования:
 *  - Степпер (– N +) с ровным выравниванием
 *  - Редактируемые поля: Название, Мощность (всегда Вт)
 *  - Остальные параметры — read-only (с иконкой замка)
 *  - Кнопка добавления в шапке и нижняя — только иконка галочки
 *  - Заголовок "Добавить устройства" всегда виден
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(
    roomId: Long,
    defaultDevices: List<DefaultDevice>,
    onDismiss: () -> Unit,
    onAdded: (List<Long>) -> Unit,
    helperVm: DevicePickerHelperVm = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var search by remember { mutableStateOf("") }
    val qty = remember { mutableStateMapOf<Long, Int>() }
    val nameOverride = remember { mutableStateMapOf<Long, String>() }
    val powerOverride = remember { mutableStateMapOf<Long, String>() }

    val filtered = remember(search, defaultDevices) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) defaultDevices else defaultDevices.filter { it.name.lowercase().contains(q) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp)
            ) {
                // стандартный хэндл
                BottomSheetDefaults.DragHandle()
                Spacer(Modifier.height(6.dp))
                // Заголовок + кнопка-галочка (без текста)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text(text = "Добавить устройства")
                    val totalTop = qty.values.sum()
                    IconButton(
                        onClick = {
                            scope.launch {
                                val reqs = buildRequests(defaultDevices, qty, nameOverride, powerOverride)
                                if (reqs.isEmpty()) { onDismiss(); return@launch }
                                val ids = helperVm.add(roomId, reqs)
                                onAdded(ids); onDismiss()
                            }
                        },
                        enabled = totalTop > 0
                    ) { Icon(Icons.Rounded.Check, contentDescription = "Добавить") }
                }
            }
        }
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Поиск устройства…") }
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp) // запас под нижнюю панель
            ) {
                items(filtered, key = { it.id }) { def ->
                    var expanded by remember { mutableStateOf(false) }
                    val count = qty[def.id] ?: 0

                    Card {
                        Column(Modifier.padding(12.dp)) {
                            // Заголовок + степпер (ровное выравнивание)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(def.name)
                                    Text("${def.power} Вт")
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = { qty[def.id] = (count - 1).coerceAtLeast(0) }
                                    ) { Icon(Icons.Rounded.Remove, contentDescription = "Уменьшить") }

                                    // фиксированная ширина для стабильного центрирования цифры
                                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                                        Text(text = count.toString(), textAlign = TextAlign.Center)
                                    }

                                    IconButton(
                                        onClick = { qty[def.id] = (count + 1).coerceAtMost(99) }
                                    ) { Icon(Icons.Rounded.Add, contentDescription = "Увеличить") }

                                    IconButton(onClick = { expanded = !expanded }) {
                                        Icon(
                                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(expanded) {
                                Column {
                                    Spacer(Modifier.height(10.dp))

                                    // Название — редактируемое
                                    OutlinedTextField(
                                        value = nameOverride[def.id] ?: def.name,
                                        onValueChange = { nameOverride[def.id] = it },
                                        label = { Text("Название") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    // Мощность — только Вт
                                    OutlinedTextField(
                                        value = powerOverride[def.id] ?: def.power.toString(),
                                        onValueChange = { powerOverride[def.id] = it.replace(',', '.') },
                                        label = { Text("Мощность (Вт)") },
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(Modifier.height(12.dp))
                                    Divider()
                                    Spacer(Modifier.height(12.dp))

                                    // Остальные параметры — read-only (с замком)
                                    ReadonlyField(label = "Тип устройства", value = deviceTypeLabel(def.deviceType))
                                    Spacer(Modifier.height(8.dp))
                                    ReadonlyField(label = "Коэфф. мощности (PF)", value = def.powerFactor.toString())
                                    Spacer(Modifier.height(8.dp))
                                    ReadonlyField(label = "Коэфф. спроса", value = def.demandRatio.toString())
                                    Spacer(Modifier.height(8.dp))
                                    ReadonlyField(label = "Напряжение", value = voltageHuman(def.voltage))
                                }
                            }
                        }
                    }
                }
            }

            // Нижняя панель: галочка (без текста) и Отмена
            Spacer(Modifier.height(8.dp))
            val total = qty.values.sum()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = total > 0,
                    onClick = {
                        scope.launch {
                            val reqs = buildRequests(defaultDevices, qty, nameOverride, powerOverride)
                            if (reqs.isEmpty()) { onDismiss(); return@launch }
                            val ids = helperVm.add(roomId, reqs)
                            onAdded(ids)
                            onDismiss()
                        }
                    }
                ) { Icon(Icons.Rounded.Check, contentDescription = "Добавить") }

                Button(onClick = onDismiss) { Text("Отмена") }
            }

            Spacer(Modifier.height(12.dp))
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

@HiltViewModel
class DevicePickerHelperVm @Inject constructor(
    private val addDevicesToRoomUseCase: AddDevicesToRoomUseCase
) : androidx.lifecycle.ViewModel() {
    suspend fun add(roomId: Long, reqs: List<DomainDeviceCreateRequest>): List<Long> {
        return addDevicesToRoomUseCase(roomId, reqs)
    }
}

private fun buildRequests(
    defaults: List<DefaultDevice>,
    qtyMap: Map<Long, Int>,
    nameOverride: Map<Long, String>,
    powerOverride: Map<Long, String>
): List<DomainDeviceCreateRequest> {
    val byId = defaults.associateBy { it.id }
    val out = mutableListOf<DomainDeviceCreateRequest>()
    for ((id, count) in qtyMap) {
        if (count <= 0) continue
        val def = byId[id] ?: continue
        val title = (nameOverride[id] ?: def.name).trim().ifEmpty { def.name }
        val rawText = (powerOverride[id] ?: def.power.toString()).replace(',', '.')
        val raw = rawText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: def.power.toDouble()
        val watts = raw.toInt().coerceAtLeast(1) // всегда Вт

        out += DomainDeviceCreateRequest(
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

private fun deviceTypeLabel(type: DeviceType): String = type.toString()

/** Требование: "написать человеческим языком и выводить только value" */
private fun voltageHuman(v: Voltage): String = "${v.value} В"