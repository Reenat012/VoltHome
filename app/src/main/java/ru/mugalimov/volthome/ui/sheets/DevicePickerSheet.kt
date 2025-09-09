package ru.mugalimov.volthome.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.core.validation.InputConstraints
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest as DomainDeviceCreateRequest
import ru.mugalimov.volthome.domain.use_case.AddDevicesToRoomUseCase
import javax.inject.Inject

/**
 * BottomSheet добавления устройств с немедленной кастомизацией.
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
    val powerErrors = remember { mutableStateMapOf<Long, String?>() }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

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
                BottomSheetDefaults.DragHandle()
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text(text = "Добавить устройства")
                    val totalTop = qty.values.sum()
                    val hasErrorsTop = powerErrors.values.any { it != null }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val reqs =
                                    buildRequests(defaultDevices, qty, nameOverride, powerOverride)
                                if (reqs.isEmpty()) {
                                    onDismiss(); return@launch
                                }
                                val ids = helperVm.add(roomId, reqs)
                                onAdded(ids); onDismiss()
                            }
                        },
                        enabled = totalTop > 0 && !hasErrorsTop
                    ) { Icon(Icons.Rounded.Check, contentDescription = "Добавить") }
                }
            }
        }
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .bringIntoViewRequester(bringIntoViewRequester)
                .imePadding()
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .onFocusChanged {
                        if (it.isFocused) scope.launch {
                            delay(150); bringIntoViewRequester.bringIntoView()
                        }
                    },
                singleLine = true,
                placeholder = { Text("Поиск устройства…") }
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(filtered, key = { it.id }) { def ->
                    var expanded by remember { mutableStateOf(false) }
                    val count = qty[def.id] ?: 0

                    Card {
                        Column(Modifier.padding(12.dp)) {
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

                                    // 1) «Название» — на всю ширину, единый визуал
                                    OutlinedTextField(
                                        value = nameOverride[def.id] ?: def.name,
                                        onValueChange = { nameOverride[def.id] = it },
                                        label = { Text("Название") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .onFocusChanged {
                                                if (it.isFocused) scope.launch {
                                                    delay(150); bringIntoViewRequester.bringIntoView()
                                                }
                                            }
                                    )

                                    Spacer(Modifier.height(10.dp))

                                    // 2) Остальные поля — сетка 2×3
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Ряд 1: Мощность | Тип устройства
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            val currentPowerText = powerOverride[def.id] ?: def.power.toString()
                                            val error = powerErrors[def.id]

                                            OutlinedTextField(
                                                value = currentPowerText,
                                                onValueChange = { new ->
                                                    val norm = new.replace(',', '.')
                                                    powerOverride[def.id] = norm
                                                    val v = norm.toDoubleOrNull()
                                                    powerErrors[def.id] = when {
                                                        v == null || v <= 0.0 -> "Введите число > 0"
                                                        v.toInt() > InputConstraints.MAX_POWER_W ->
                                                            "Максимум ${InputConstraints.MAX_POWER_W} Вт"
                                                        else -> null
                                                    }
                                                },
                                                label = { Text("Мощность (Вт)") },
                                                singleLine = true,
                                                isError = error != null,
                                                supportingText = { error?.let { Text(it) } },
                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                    keyboardType = KeyboardType.Decimal
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 56.dp)
                                                    .onFocusChanged {
                                                        if (it.isFocused) scope.launch {
                                                            delay(150); bringIntoViewRequester.bringIntoView()
                                                        }
                                                    }
                                            )

                                            // ReadonlyField без изменения сигнатуры: участвует в сетке через weight
                                            Box(Modifier.weight(1f)) {
                                                ReadonlyField("Тип устройства", deviceTypeLabel(def.deviceType))
                                            }
                                        }

                                        // Ряд 2: Cos φ | Коэфф. спроса
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(Modifier.weight(1f)) {
                                                ReadonlyField("Коэфф. мощности (PF)", def.powerFactor.toString())
                                            }
                                            Box(Modifier.weight(1f)) {
                                                ReadonlyField("Коэфф. спроса", def.demandRatio.toString())
                                            }
                                        }

                                        // Ряд 3: Напряжение | пустая ячейка (резерв)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(Modifier.weight(1f)) {
                                                ReadonlyField("Напряжение", voltageHuman(def.voltage))
                                            }
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            val total = qty.values.sum()
            val hasErrorsBottom = powerErrors.values.any { it != null }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = total > 0 && !hasErrorsBottom,
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
        readOnly = true,
        enabled = false,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
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
        val watts = raw.toInt().coerceIn(
            InputConstraints.MIN_POWER_W,
            InputConstraints.MAX_POWER_W
        )
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
private fun voltageHuman(v: Voltage): String = "${v.value} В"
