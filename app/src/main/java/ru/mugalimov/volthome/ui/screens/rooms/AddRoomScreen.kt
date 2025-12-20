package ru.mugalimov.volthome.ui.screens.rooms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.core.validation.InputConstraints
import ru.mugalimov.volthome.domain.model.*
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import javax.inject.Inject

// ✅ ДОБАВЛЕНО:
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Экран добавления комнаты с устройствами.
 * Реализован на BottomSheetScaffold.
 * Контент внутри реагирует на клавиатуру через imePadding().
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRoomSheet(
    defaultDevices: List<DefaultDevice>,
    roomTypes: List<RoomType>,
    onConfirm: (name: String, roomType: RoomType, devices: List<DeviceCreateRequest>) -> Unit,
    onDismiss: () -> Unit,
    paywallBus: PaywallBus = hiltViewModel<AddRoomSheetPaywallHolder>().paywallBus
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(roomTypes.firstOrNull() ?: RoomType.STANDARD) }

    val qtyMap = remember { mutableStateMapOf<String, Int>() }
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }
    val nameOverride = remember { mutableStateMapOf<String, String>() }
    val powerOverride = remember { mutableStateMapOf<String, String>() }
    val powerErrors = remember { mutableStateMapOf<String, String?>() }

    // ✅ Advanced overrides (применяются только для PRO)
    val pfOverride = remember { mutableStateMapOf<String, String>() }
    val drOverride = remember { mutableStateMapOf<String, String>() }
    val voltageOverride = remember { mutableStateMapOf<String, Voltage>() }

    // dialogs state
    var pfDialogFor by remember { mutableStateOf<String?>(null) }
    var drDialogFor by remember { mutableStateOf<String?>(null) }
    var voltageDialogFor by remember { mutableStateOf<String?>(null) }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val isPro = LocalUserPlan.current.isPro

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Expanded,
            skipHiddenState = false
        )
    )

    LaunchedEffect(Unit) { applyPresetFor(selectedType, defaultDevices, qtyMap) }

    // агрегированное состояние валидности:
    val hasAnyQty by remember { derivedStateOf { qtyMap.values.any { it > 0 } } }
    val hasErrors by remember {
        derivedStateOf { qtyMap.any { (k, v) -> v > 0 && powerErrors[k] != null } }
    }

    // ✅ при свайпе вниз состояние уходит в Hidden/PartiallyExpanded — закрываем
    LaunchedEffect(scaffoldState.bottomSheetState) {
        snapshotFlow { scaffoldState.bottomSheetState.currentValue }
            .collectLatest { value ->
                if (value == SheetValue.Hidden || value == SheetValue.PartiallyExpanded) {
                    onDismiss()
                }
            }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContent = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .bringIntoViewRequester(bringIntoViewRequester),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Добавить комнату",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            enabled = hasAnyQty && !hasErrors,
                            onClick = {
                                val requests = buildRequests(
                                    defaults = defaultDevices,
                                    qtyMap = qtyMap,
                                    nameOverride = nameOverride,
                                    powerOverride = powerOverride,
                                    isPro = isPro,
                                    pfOverride = pfOverride,
                                    drOverride = drOverride,
                                    voltageOverride = voltageOverride
                                )
                                onConfirm(
                                    name.ifBlank { roomTypeLabel(selectedType) },
                                    selectedType,
                                    requests
                                )
                                onDismiss()
                            }
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = "Создать")
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название комнаты") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                if (it.isFocused) scope.launch {
                                    delay(200); bringIntoViewRequester.bringIntoView()
                                }
                            }
                    )
                }

                item {
                    RoomTypeRow(
                        types = roomTypes,
                        selected = selectedType,
                        onSelect = {
                            selectedType = it
                            applyPresetFor(it, defaultDevices, qtyMap)
                        }
                    )
                }

                item {
                    Text("Устройства", style = MaterialTheme.typography.titleMedium)
                }

                items(defaultDevices, key = { it.id }) { device ->
                    val key = device.id.toString()
                    val expanded = expandedMap[key] == true
                    val qty = qtyMap[key] ?: 0
                    val err = powerErrors[key]

                    // показываем overrides (если есть), иначе дефолт
                    val pfText = pfOverride[key] ?: device.powerFactor.format(2)
                    val drText = drOverride[key] ?: device.demandRatio.format(2)
                    val voltageText = voltageHuman(voltageOverride[key] ?: device.voltage)

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
                        onPowerChange = { newText ->
                            val norm = newText.replace(',', '.')
                            powerOverride[key] = norm
                            val v = norm.toDoubleOrNull()?.toInt()
                            powerErrors[key] = when {
                                v == null -> "Введите число > 0"
                                v < InputConstraints.MIN_POWER_W ->
                                    "Минимум ${InputConstraints.MIN_POWER_W} Вт"
                                v > InputConstraints.MAX_POWER_W ->
                                    "Максимум ${InputConstraints.MAX_POWER_W} Вт"
                                else -> null
                            }
                        },
                        powerError = err,
                        bringIntoViewRequester = bringIntoViewRequester,
                        scope = scope,

                        // ✅ Advanced (PRO gating)
                        isPro = isPro,
                        pfText = pfText,
                        drText = drText,
                        voltageText = voltageText,
                        onPfClick = {
                            if (!isPro) paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR)
                            else pfDialogFor = key
                        },
                        onDrClick = {
                            if (!isPro) paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR)
                            else drDialogFor = key
                        },
                        onVoltageClick = {
                            if (!isPro) paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR)
                            else voltageDialogFor = key
                        }
                    )
                }
            }
        },
        content = { /* основной экран здесь пустой */ }
    )

    // -------------------- DIALOGS --------------------

    // PF Dialog
    pfDialogFor?.let { key ->
        val def = defaultDevices.firstOrNull { it.id.toString() == key } ?: run { pfDialogFor = null; return@let }
        var text by remember { mutableStateOf(pfOverride[key] ?: def.powerFactor.toString()) }
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
                        pfOverride[key] = text.trim().replace(',', '.')
                        pfDialogFor = null
                    }
                ) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { pfDialogFor = null }) { Text("Отмена") } }
        )
    }

    // Demand Dialog
    drDialogFor?.let { key ->
        val def = defaultDevices.firstOrNull { it.id.toString() == key } ?: run { drDialogFor = null; return@let }
        var text by remember { mutableStateOf(drOverride[key] ?: def.demandRatio.toString()) }
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
                        drOverride[key] = text.trim().replace(',', '.')
                        drDialogFor = null
                    }
                ) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { drDialogFor = null }) { Text("Отмена") } }
        )
    }

    // Voltage Dialog (220/380 presets)
    voltageDialogFor?.let { key ->
        AlertDialog(
            onDismissRequest = { voltageDialogFor = null },
            title = { Text("Напряжение") },
            text = { Text("Выбери тип сети для устройства:") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = {
                            voltageOverride[key] = Voltage(220, VoltageType.AC_1PHASE)
                            voltageDialogFor = null
                        }
                    ) { Text("220 В") }

                    TextButton(
                        onClick = {
                            voltageOverride[key] = Voltage(380, VoltageType.AC_3PHASE)
                            voltageDialogFor = null
                        }
                    ) { Text("380 В") }
                }
            },
            dismissButton = { TextButton(onClick = { voltageDialogFor = null }) { Text("Отмена") } }
        )
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
                color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            ) {
                Text(
                    roomTypeLabel(t),
                    modifier = Modifier
                        .clickable { onSelect(t) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DeviceRowEditable(
    device: DefaultDevice,
    expanded: Boolean,
    qty: Int,
    title: String,
    powerText: String,
    powerError: String?,
    onToggle: () -> Unit,
    onInc: () -> Unit,
    onDec: () -> Unit,
    onTitleChange: (String) -> Unit,
    onPowerChange: (String) -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    scope: CoroutineScope,

    // ✅ Advanced gating inputs
    isPro: Boolean,
    pfText: String,
    drText: String,
    voltageText: String,
    onPfClick: () -> Unit,
    onDrClick: () -> Unit,
    onVoltageClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
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

                    // 1) Название — FREE
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        label = { Text("Название") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .onFocusChanged {
                                if (it.isFocused) scope.launch {
                                    delay(200); bringIntoViewRequester.bringIntoView()
                                }
                            }
                    )

                    Spacer(Modifier.height(10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Ряд 1: Мощность (FREE) | Тип устройства (readonly)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            OutlinedTextField(
                                value = powerText,
                                onValueChange = onPowerChange,
                                label = { Text("Мощность (Вт)") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal
                                ),
                                trailingIcon = { Icon(Icons.Rounded.ElectricBolt, contentDescription = null) },
                                isError = powerError != null,
                                supportingText = {
                                    Text(
                                        powerError
                                            ?: "Допустимо от ${InputConstraints.MIN_POWER_W} до ${InputConstraints.MAX_POWER_W} Вт"
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .onFocusChanged {
                                        if (it.isFocused) scope.launch {
                                            delay(200); bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                            )

                            ReadonlyFieldPro(
                                label = "Тип устройства",
                                value = deviceTypeLabel(device.deviceType),
                                locked = false,
                                hint = null,
                                onClick = null,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Ряд 2: PF | Кс (PRO)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            ReadonlyFieldPro(
                                label = "Коэфф. мощности (PF)",
                                value = pfText,
                                locked = !isPro,
                                hint = "Влияет на расчёт тока",
                                onClick = onPfClick,
                                modifier = Modifier.weight(1f)
                            )
                            ReadonlyFieldPro(
                                label = "Коэфф. спроса",
                                value = drText,
                                locked = !isPro,
                                hint = "Учитывает реальную нагрузку",
                                onClick = onDrClick,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Ряд 3: Напряжение (PRO) — пресеты 220/380
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            ReadonlyFieldPro(
                                label = "Напряжение",
                                value = voltageText,
                                locked = !isPro,
                                hint = "Влияет на ток и фазность",
                                onClick = onVoltageClick,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/* ====== Хелперы ====== */

private fun buildRequests(
    defaults: List<DefaultDevice>,
    qtyMap: Map<String, Int>,
    nameOverride: Map<String, String>,
    powerOverride: Map<String, String>,
    isPro: Boolean,
    pfOverride: Map<String, String>,
    drOverride: Map<String, String>,
    voltageOverride: Map<String, Voltage>
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

        val watts = powerRaw
            .toInt()
            .coerceIn(InputConstraints.MIN_POWER_W, InputConstraints.MAX_POWER_W)

        // ✅ advanced применяем только если PRO (в FREE — игнорим overrides)
        val pf = if (isPro) pfOverride[key]?.trim()?.replace(',', '.')?.toDoubleOrNull() else null
        val dr = if (isPro) drOverride[key]?.trim()?.replace(',', '.')?.toDoubleOrNull() else null
        val v = if (isPro) voltageOverride[key] else null

        out += DeviceCreateRequest(
            title = title,
            type = def.deviceType,
            count = count,
            ratedPowerW = watts,
            powerFactor = pf ?: def.powerFactor,
            demandRatio = dr ?: def.demandRatio,
            voltage = v ?: def.voltage
        )
    }
    return out
}

private fun roomTypeLabel(type: RoomType): String = when (type) {
    RoomType.STANDARD -> "Стандартная"
    RoomType.BATHROOM -> "Ванная (УЗО)"
    RoomType.KITCHEN -> "Кухня (УЗО)"
    RoomType.OUTDOOR -> "Улица (УЗО)"
}

private fun deviceTypeLabel(type: DeviceType): String = type.name
private fun Double.format(digits: Int) = "%.${digits}f".format(this).replace(',', '.')

private fun voltageHuman(v: Voltage): String = when (v.type) {
    VoltageType.AC_1PHASE -> "${v.value} В (1ф)"
    VoltageType.AC_3PHASE -> "${v.value} В (3ф)"
    VoltageType.DC -> "${v.value} В (DC)"
}

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

        RoomType.OUTDOOR -> addFirstOf(DeviceType.SOCKET, 1)
    }
}

@Composable
private fun ReadonlyFieldPro(
    label: String,
    value: String,
    locked: Boolean,
    hint: String? = null,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    // ✅ ВАЖНО: enabled=true, иначе не поймаешь намерение в FREE
    val clickable = onClick != null
    val m = modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .let { base -> if (clickable) base.clickable { onClick?.invoke() } else base }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = true,
        singleLine = true,
        supportingText = { if (hint != null) Text(hint) },
        trailingIcon = { if (locked) Icon(Icons.Rounded.Lock, contentDescription = null) },
        shape = RoundedCornerShape(12.dp),
        modifier = m
    )
}

private fun validateRatio(input: String): String? {
    val v = input.trim().replace(',', '.').toDoubleOrNull()
    if (v == null) return "Введите число"
    if (v < 0.1 || v > 1.0) return "Допустимо 0.1 — 1.0"
    return null
}

/**
 * Хелпер, чтобы получить PaywallBus через Hilt без ручного DI в composable.
 */
@HiltViewModel
class AddRoomSheetPaywallHolder @Inject constructor(
    val paywallBus: PaywallBus
) : androidx.lifecycle.ViewModel()