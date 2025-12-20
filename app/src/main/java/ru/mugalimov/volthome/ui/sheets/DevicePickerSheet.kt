package ru.mugalimov.volthome.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest as DomainDeviceCreateRequest
import ru.mugalimov.volthome.domain.use_case.AddDevicesToRoomUseCase
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(
    roomId: Long,
    defaultDevices: List<DefaultDevice>,
    onDismiss: () -> Unit,
    onAdded: (List<Long>) -> Unit,
    helperVm: DevicePickerHelperVm = hiltViewModel(),
    paywallBus: PaywallBus = hiltViewModel<DevicePickerSheetPaywallHolder>().paywallBus
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val isPro = LocalUserPlan.current.isPro

    var search by remember { mutableStateOf("") }

    val qty = remember { mutableStateMapOf<Long, Int>() }
    val nameOverride = remember { mutableStateMapOf<Long, String>() }
    val powerOverride = remember { mutableStateMapOf<Long, String>() }
    val powerErrors = remember { mutableStateMapOf<Long, String?>() }

    // ✅ Advanced overrides (только PRO применяет)
    val pfOverride = remember { mutableStateMapOf<Long, String>() }
    val drOverride = remember { mutableStateMapOf<Long, String>() }
    val voltageOverride = remember { mutableStateMapOf<Long, Voltage>() }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // dialogs state
    var pfDialogFor by remember { mutableStateOf<Long?>(null) }
    var drDialogFor by remember { mutableStateOf<Long?>(null) }
    var voltageDialogFor by remember { mutableStateOf<Long?>(null) }

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
                                val reqs = buildRequestsForPicker(
                                    defaults = defaultDevices,
                                    qtyMap = qty,
                                    nameOverride = nameOverride,
                                    powerOverride = powerOverride,
                                    isPro = isPro,
                                    pfOverride = pfOverride,
                                    drOverride = drOverride,
                                    voltageOverride = voltageOverride
                                )
                                if (reqs.isEmpty()) { onDismiss(); return@launch }
                                val ids = helperVm.add(roomId, reqs)
                                onAdded(ids)
                                onDismiss()
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
                        if (it.isFocused) scope.launch { delay(150); bringIntoViewRequester.bringIntoView() }
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
                                    IconButton(onClick = { qty[def.id] = (count - 1).coerceAtLeast(0) }) {
                                        Icon(Icons.Rounded.Remove, contentDescription = "Уменьшить")
                                    }

                                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                                        Text(text = count.toString(), textAlign = TextAlign.Center)
                                    }

                                    IconButton(onClick = { qty[def.id] = (count + 1).coerceAtMost(99) }) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Увеличить")
                                    }

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

                                    // FREE: имя редактируется
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
                                                if (it.isFocused) scope.launch { delay(150); bringIntoViewRequester.bringIntoView() }
                                            }
                                    )

                                    Spacer(Modifier.height(10.dp))

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
                                                        v == null -> "Введите число > 0"
                                                        v.toInt() < InputConstraints.MIN_POWER_W ->
                                                            "Минимум ${InputConstraints.MIN_POWER_W} Вт"
                                                        v.toInt() > InputConstraints.MAX_POWER_W ->
                                                            "Максимум ${InputConstraints.MAX_POWER_W} Вт"
                                                        else -> null
                                                    }
                                                },
                                                label = { Text("Мощность (Вт)") },
                                                singleLine = true,
                                                isError = error != null,
                                                supportingText = {
                                                    Text(error ?: "Допустимо от ${InputConstraints.MIN_POWER_W} до ${InputConstraints.MAX_POWER_W} Вт")
                                                },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 56.dp)
                                                    .onFocusChanged {
                                                        if (it.isFocused) scope.launch { delay(150); bringIntoViewRequester.bringIntoView() }
                                                    }
                                            )

                                            Box(Modifier.weight(1f)) {
                                                ReadonlyFieldPro(
                                                    label = "Тип устройства",
                                                    value = deviceTypeLabel(def.deviceType),
                                                    locked = false,
                                                    hint = null,
                                                    onClick = null
                                                )
                                            }
                                        }

                                        // Ряд 2: PF | Кс (Advanced, продаём PRO)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            val pfText = pfOverride[def.id] ?: def.powerFactor.toString()
                                            val drText = drOverride[def.id] ?: def.demandRatio.toString()

                                            Box(Modifier.weight(1f)) {
                                                ReadonlyFieldPro(
                                                    label = "Коэфф. мощности (PF)",
                                                    value = pfText,
                                                    locked = !isPro,
                                                    hint = "Влияет на расчёт тока",
                                                    onClick = {
                                                        if (!isPro) paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR)
                                                        else pfDialogFor = def.id
                                                    }
                                                )
                                            }
                                            Box(Modifier.weight(1f)) {
                                                ReadonlyFieldPro(
                                                    label = "Коэфф. спроса",
                                                    value = drText,
                                                    locked = !isPro,
                                                    hint = "Учитывает реальную нагрузку",
                                                    onClick = {
                                                        if (!isPro) paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR)
                                                        else drDialogFor = def.id
                                                    }
                                                )
                                            }
                                        }

                                        // Ряд 3: Напряжение (220/380)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            val v = voltageOverride[def.id] ?: def.voltage
                                            Box(Modifier.weight(1f)) {
                                                ReadonlyFieldPro(
                                                    label = "Напряжение",
                                                    value = voltageHuman(v),
                                                    locked = !isPro,
                                                    hint = "Влияет на ток и фазность",
                                                    onClick = {
                                                        if (!isPro) paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR)
                                                        else voltageDialogFor = def.id
                                                    }
                                                )
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
                            val reqs = buildRequestsForPicker(
                                defaults = defaultDevices,
                                qtyMap = qty,
                                nameOverride = nameOverride,
                                powerOverride = powerOverride,
                                isPro = isPro,
                                pfOverride = pfOverride,
                                drOverride = drOverride,
                                voltageOverride = voltageOverride
                            )
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

    // ---------------- Dialogs ----------------

    // PF
    pfDialogFor?.let { deviceKey ->
        val def = defaultDevices.firstOrNull { it.id == deviceKey } ?: run { pfDialogFor = null; return@let }
        var text by remember { mutableStateOf(pfOverride[deviceKey] ?: def.powerFactor.toString()) }
        val err = validateRatio(text)

        AlertDialog(
            onDismissRequest = { pfDialogFor = null },
            title = { Text("Коэффициент мощности (PF)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Используется в проектировании и влияет на расчёт тока.")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("PF (0.1 — 1.0)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = err != null
                    )
                    if (err != null) Text(err)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = err == null,
                    onClick = {
                        pfOverride[deviceKey] = text.trim().replace(',', '.')
                        pfDialogFor = null
                    }
                ) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { pfDialogFor = null }) { Text("Отмена") } }
        )
    }

    // Demand ratio
    drDialogFor?.let { deviceKey ->
        val def = defaultDevices.firstOrNull { it.id == deviceKey } ?: run { drDialogFor = null; return@let }
        var text by remember { mutableStateOf(drOverride[deviceKey] ?: def.demandRatio.toString()) }
        val err = validateRatio(text)

        AlertDialog(
            onDismissRequest = { drDialogFor = null },
            title = { Text("Коэффициент спроса") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Позволяет учитывать реальные условия эксплуатации.")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Кс (0.1 — 1.0)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = err != null
                    )
                    if (err != null) Text(err)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = err == null,
                    onClick = {
                        drOverride[deviceKey] = text.trim().replace(',', '.')
                        drDialogFor = null
                    }
                ) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { drDialogFor = null }) { Text("Отмена") } }
        )
    }

    // Voltage presets
    voltageDialogFor?.let { deviceKey ->
        AlertDialog(
            onDismissRequest = { voltageDialogFor = null },
            title = { Text("Напряжение") },
            text = { Text("Выбери тип сети для устройства:") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = {
                        voltageOverride[deviceKey] = Voltage(value = 220, type = VoltageType.AC_1PHASE)
                        voltageDialogFor = null
                    }) { Text("220 В") }

                    TextButton(onClick = {
                        voltageOverride[deviceKey] = Voltage(value = 380, type = VoltageType.AC_3PHASE)
                        voltageDialogFor = null
                    }) { Text("380 В") }
                }
            },
            dismissButton = { TextButton(onClick = { voltageDialogFor = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun ReadonlyFieldPro(
    label: String,
    value: String,
    locked: Boolean,
    hint: String? = null,
    onClick: (() -> Unit)?
) {
    val clickable = onClick != null
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .let { m -> if (clickable) m.clickable { onClick?.invoke() } else m }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = true, // ✅ важно: иначе не ловим намерение
        singleLine = true,
        supportingText = { if (hint != null) Text(hint) },
        trailingIcon = { if (locked) Icon(Icons.Rounded.Lock, contentDescription = null) },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}

@HiltViewModel
class DevicePickerSheetPaywallHolder @Inject constructor(
    val paywallBus: PaywallBus
) : androidx.lifecycle.ViewModel()

@HiltViewModel
class DevicePickerHelperVm @Inject constructor(
    private val addDevicesToRoomUseCase: AddDevicesToRoomUseCase
) : androidx.lifecycle.ViewModel() {
    suspend fun add(roomId: Long, reqs: List<DomainDeviceCreateRequest>): List<Long> {
        return addDevicesToRoomUseCase(roomId, reqs)
    }
}

private fun buildRequestsForPicker(
    defaults: List<DefaultDevice>,
    qtyMap: Map<Long, Int>,
    nameOverride: Map<Long, String>,
    powerOverride: Map<Long, String>,
    isPro: Boolean,
    pfOverride: Map<Long, String>,
    drOverride: Map<Long, String>,
    voltageOverride: Map<Long, Voltage>
): List<DomainDeviceCreateRequest> {
    val byId = defaults.associateBy { it.id }
    val out = mutableListOf<DomainDeviceCreateRequest>()

    for ((id, count) in qtyMap) {
        if (count <= 0) continue
        val def = byId[id] ?: continue

        val title = (nameOverride[id] ?: def.name).trim().ifEmpty { def.name }
        val rawText = (powerOverride[id] ?: def.power.toString()).replace(',', '.')
        val raw = rawText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: def.power.toDouble()
        val watts = raw.toInt().coerceIn(InputConstraints.MIN_POWER_W, InputConstraints.MAX_POWER_W)

        val pf = if (isPro) pfOverride[id]?.trim()?.replace(',', '.')?.toDoubleOrNull() else null
        val dr = if (isPro) drOverride[id]?.trim()?.replace(',', '.')?.toDoubleOrNull() else null
        val volt = if (isPro) voltageOverride[id] else null

        out += DomainDeviceCreateRequest(
            title = title,
            type = def.deviceType,
            count = count,
            ratedPowerW = watts,
            powerFactor = pf ?: def.powerFactor,
            demandRatio = dr ?: def.demandRatio,
            voltage = volt ?: def.voltage
        )
    }
    return out
}

private fun validateRatio(input: String): String? {
    val v = input.trim().replace(',', '.').toDoubleOrNull()
    if (v == null) return "Введите число"
    if (v < 0.1 || v > 1.0) return "Допустимо 0.1 — 1.0"
    return null
}

private fun deviceTypeLabel(type: DeviceType): String = type.toString()

private fun voltageHuman(v: Voltage): String = when (v.type) {
    VoltageType.AC_1PHASE -> "${v.value} В (1ф)"
    VoltageType.AC_3PHASE -> "${v.value} В (3ф)"
    VoltageType.DC -> "${v.value} В (DC)"
}