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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.*
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsDraft
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsValidator
import ru.mugalimov.volthome.ui.components.device.adapter.InMemoryDeviceParamsAdapter
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import javax.inject.Inject

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

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val isPro = LocalUserPlan.current.isPro

    val editorAdapter = remember(isPro) {
        InMemoryDeviceParamsAdapter<String>(
            isPro = isPro,
            paywall = { paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR) }
        )
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Expanded,
            skipHiddenState = false
        )
    )

    LaunchedEffect(Unit) { applyPresetFor(selectedType, defaultDevices, qtyMap) }

    val hasAnyQty by remember { derivedStateOf { qtyMap.values.any { it > 0 } } }

    // ✅ aggregated validation: без локальных карт ошибок (только adapter)
    val byId = remember(defaultDevices) { defaultDevices.associateBy { it.id.toString() } }
    val hasErrors by remember {
        derivedStateOf {
            val selectedKeys = qtyMap.filterValues { it > 0 }.keys
            editorAdapter.hasAnyErrors(
                keys = selectedKeys,
                seedForKey = { key ->
                    val def = byId[key] ?: return@hasAnyErrors DeviceParamsDraft(
                        name = "",
                        powerText = "0",
                        deviceType = DeviceType.SOCKET,
                        powerFactorText = "1.00",
                        demandRatioText = "1.00",
                        voltageType = VoltageType.AC_1PHASE,
                        hasMotor = false,
                        requiresDedicatedCircuit = false,
                        requiresSocketConnection = true
                    )

                    DeviceParamsDraft(
                        name = def.name,
                        powerText = def.power.toString(),
                        deviceType = def.deviceType,
                        powerFactorText = def.powerFactor.format(2),
                        demandRatioText = def.demandRatio.format(2),
                        voltageType = def.voltage.type,
                        hasMotor = def.hasMotor,
                        requiresDedicatedCircuit = def.requiresDedicatedCircuit,
                        requiresSocketConnection = def.requiresSocketConnection
                    )
                },
                predicate = { e ->
                    e.nameError != null || e.powerError != null ||
                            e.powerFactorError != null || e.demandRatioError != null
                }
            )
        }
    }

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
                                    editorAdapter = editorAdapter,
                                    isPro = isPro
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

                item { Text("Устройства", style = MaterialTheme.typography.titleMedium) }

                items(defaultDevices, key = { it.id }) { device ->
                    val key = device.id.toString()
                    val qty = qtyMap[key] ?: 0

                    val st = editorAdapter.state(
                        key = key,
                        seed = DeviceParamsDraft(
                            name = device.name,
                            powerText = device.power.toString(),
                            deviceType = device.deviceType,
                            powerFactorText = device.powerFactor.format(2),
                            demandRatioText = device.demandRatio.format(2),
                            voltageType = device.voltage.type,
                            hasMotor = device.hasMotor,
                            requiresDedicatedCircuit = device.requiresDedicatedCircuit,
                            requiresSocketConnection = device.requiresSocketConnection
                        )
                    )

                    DeviceRowEditable(
                        device = device,
                        qty = qty,
                        stExpanded = st.isExpanded,
                        onToggle = { st.onExpandedChange(!st.isExpanded) },
                        onInc = { qtyMap[key] = (qty + 1).coerceAtMost(99) },
                        onDec = { qtyMap[key] = (qty - 1).coerceAtLeast(0) },

                        st = st,
                        bringIntoViewRequester = bringIntoViewRequester,
                        scope = scope,

                        isPro = isPro,
                        onLockedClick = editorAdapter::onLockedClick
                    )
                }
            }
        },
        content = { /* основной экран пустой */ }
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceRowEditable(
    device: DefaultDevice,
    qty: Int,
    stExpanded: Boolean,
    onToggle: () -> Unit,
    onInc: () -> Unit,
    onDec: () -> Unit,

    st: ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsEditorState<String>,
    bringIntoViewRequester: BringIntoViewRequester,
    scope: CoroutineScope,

    isPro: Boolean,
    onLockedClick: () -> Unit
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
                    if (!stExpanded) {
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
                        Icon(
                            Icons.Rounded.Remove,
                            contentDescription = "Уменьшить"
                        )
                    }
                    Box(
                        Modifier.width(28.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(qty.toString()) }
                    IconButton(onClick = onInc) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "Увеличить"
                        )
                    }
                }

                IconButton(onClick = onToggle) {
                    Icon(
                        if (stExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = stExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {

                    OutlinedTextField(
                        value = st.draft.name,
                        onValueChange = st.onNameChange,
                        label = { Text("Название") },
                        singleLine = true,
                        isError = st.errors.nameError != null,
                        supportingText = {
                            st.errors.nameError?.let { Text(it) }
                        },
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val error = st.errors.powerError

                            OutlinedTextField(
                                value = st.draft.powerText,
                                onValueChange = st.onPowerTextChange,
                                label = { Text("Мощность (Вт)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.ElectricBolt,
                                        contentDescription = null
                                    )
                                },
                                isError = error != null,
                                supportingText = { error?.let { Text(it) } },
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

                            EnumDropdownField(
                                label = "Тип устройства",
                                value = st.draft.deviceType,
                                values = DeviceType.values().toList(),
                                valueLabel = { it.name },
                                locked = !isPro,
                                onLockedClick = onLockedClick,
                                onValueChange = st.onDeviceTypeChange,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val pfText = st.draft.powerFactorText
                            val drText = st.draft.demandRatioText

                            LockedDecimalField(
                                label = "Коэфф. мощности (PF)",
                                value = pfText,
                                locked = !isPro,
                                hint = "Влияет на расчёт тока",
                                error = st.errors.powerFactorError,
                                onLockedClick = onLockedClick,
                                onValueChange = st.onPowerFactorTextChange,
                                modifier = Modifier.weight(1f),
                                bringIntoViewRequester = bringIntoViewRequester,
                                scope = scope
                            )

                            LockedDecimalField(
                                label = "Коэфф. спроса",
                                value = drText,
                                locked = !isPro,
                                hint = "Учитывает реальную нагрузку",
                                error = st.errors.demandRatioError,
                                onLockedClick = onLockedClick,
                                onValueChange = st.onDemandRatioTextChange,
                                modifier = Modifier.weight(1f),
                                bringIntoViewRequester = bringIntoViewRequester,
                                scope = scope
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            VoltageTypeDropdownField(
                                label = "Напряжение",
                                value = st.draft.voltageType,
                                locked = !isPro,
                                onLockedClick = onLockedClick,
                                onValueChange = st.onVoltageTypeChange,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.weight(1f))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            YesNoDropdownField(
                                label = "Есть двигатель",
                                value = st.draft.hasMotor,
                                locked = !isPro,
                                onLockedClick = onLockedClick,
                                onValueChange = st.onHasMotorChange,
                                modifier = Modifier.weight(1f)
                            )
                            YesNoDropdownField(
                                label = "Выделенная линия",
                                value = st.draft.requiresDedicatedCircuit,
                                locked = !isPro,
                                onLockedClick = onLockedClick,
                                onValueChange = st.onRequiresDedicatedCircuitChange,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            YesNoDropdownField(
                                label = "Подключение розеткой",
                                value = st.draft.requiresSocketConnection,
                                locked = !isPro,
                                onLockedClick = onLockedClick,
                                onValueChange = st.onRequiresSocketConnectionChange,
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

private fun buildRequests(
    defaults: List<DefaultDevice>,
    qtyMap: Map<String, Int>,
    editorAdapter: InMemoryDeviceParamsAdapter<String>,
    isPro: Boolean
): List<DeviceCreateRequest> {
    val byId = defaults.associateBy { it.id.toString() }
    val out = mutableListOf<DeviceCreateRequest>()

    for ((key, count) in qtyMap) {
        if (count <= 0) continue
        val def = byId[key] ?: continue

        val seed = DeviceParamsDraft(
            name = def.name,
            powerText = def.power.toString(),
            deviceType = def.deviceType,
            powerFactorText = def.powerFactor.format(2),
            demandRatioText = def.demandRatio.format(2),
            voltageType = def.voltage.type,
            hasMotor = def.hasMotor,
            requiresDedicatedCircuit = def.requiresDedicatedCircuit,
            requiresSocketConnection = def.requiresSocketConnection
        )

        val rawDraft = editorAdapter.peekDraft(key = key, seed = seed)

        val res = DeviceParamsValidator.validated(rawDraft)
        val e = DeviceParamsValidator.validateForPlan(res.normalized, isPro)

        // Решение "валидно/не валидно" — только по errors
        if (e.nameError != null) continue
        if (e.powerError != null) continue
        if (isPro && (e.powerFactorError != null || e.demandRatioError != null)) continue

        val n = res.normalized

        // Парсим только из normalized (после того как errors == null)
        val title = n.name
        val watts = n.powerText.toDouble().toInt()
        val pf = if (isPro) n.powerFactorText.toDouble() else null
        val dr = if (isPro) n.demandRatioText.toDouble() else null

        val type = if (isPro) n.deviceType else def.deviceType

        val volt = if (isPro) {
            when (n.voltageType) {
                VoltageType.AC_1PHASE -> Voltage(220, VoltageType.AC_1PHASE)
                VoltageType.AC_3PHASE -> Voltage(380, VoltageType.AC_3PHASE)
                VoltageType.DC -> def.voltage
            }
        } else def.voltage

        val hm = if (isPro) n.hasMotor else def.hasMotor
        val rd = if (isPro) n.requiresDedicatedCircuit else def.requiresDedicatedCircuit
        val rs = if (isPro) n.requiresSocketConnection else def.requiresSocketConnection

        out += DeviceCreateRequest(
            title = title,
            type = type,
            count = count,
            ratedPowerW = watts,
            powerFactor = pf ?: def.powerFactor,
            demandRatio = dr ?: def.demandRatio,
            voltage = volt,
            hasMotor = hm,
            requiresDedicatedCircuit = rd,
            requiresSocketConnection = rs
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

private fun Double.format(digits: Int) = "%.${digits}f".format(this).replace(',', '.')

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdownField(
    label: String,
    value: T,
    values: List<T>,
    valueLabel: (T) -> String,
    locked: Boolean,
    onLockedClick: () -> Unit,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val open = { if (locked) onLockedClick() else expanded = true }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open() },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        OutlinedTextField(
            value = valueLabel(value),
            onValueChange = {},
            readOnly = true,
            enabled = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = {
                if (locked) Icon(Icons.Rounded.Lock, contentDescription = null)
                else ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(valueLabel(item)) },
                    onClick = { expanded = false; onValueChange(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoltageTypeDropdownField(
    label: String,
    value: VoltageType,
    locked: Boolean,
    onLockedClick: () -> Unit,
    onValueChange: (VoltageType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val open = { if (locked) onLockedClick() else expanded = true }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open() },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        OutlinedTextField(
            value = when (value) {
                VoltageType.AC_1PHASE -> "AC 1ф (220 В)"
                VoltageType.AC_3PHASE -> "AC 3ф (380 В)"
                VoltageType.DC -> "DC"
            },
            onValueChange = {},
            readOnly = true,
            enabled = true,
            singleLine = true,
            label = { Text(label) },
            supportingText = { Text("DC пока недоступен") },
            trailingIcon = {
                if (locked) Icon(Icons.Rounded.Lock, contentDescription = null)
                else ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("AC 1ф (220 В)") },
                onClick = { expanded = false; onValueChange(VoltageType.AC_1PHASE) }
            )
            DropdownMenuItem(
                text = { Text("AC 3ф (380 В)") },
                onClick = { expanded = false; onValueChange(VoltageType.AC_3PHASE) }
            )
            DropdownMenuItem(enabled = false, text = { Text("DC — скоро") }, onClick = {})
        }
    }
}

@Composable
private fun YesNoDropdownField(
    label: String,
    value: Boolean,
    locked: Boolean,
    onLockedClick: () -> Unit,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    EnumDropdownField(
        label = label,
        value = value,
        values = listOf(true, false),
        valueLabel = { if (it) "Да" else "Нет" },
        locked = locked,
        onLockedClick = onLockedClick,
        onValueChange = onValueChange,
        modifier = modifier
    )
}

@Composable
private fun LockedDecimalField(
    label: String,
    value: String,
    locked: Boolean,
    hint: String?,
    error: String?,
    onLockedClick: () -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    bringIntoViewRequester: BringIntoViewRequester,
    scope: CoroutineScope
) {
    val m = modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .let { base -> if (locked) base.clickable { onLockedClick() } else base }

    OutlinedTextField(
        value = value,
        onValueChange = { if (!locked) onValueChange(it) },
        label = { Text(label) },
        enabled = true,
        readOnly = locked,
        singleLine = true,
        isError = (error != null && !locked),
        supportingText = {
            when {
                locked && hint != null -> Text(hint)
                !locked && error != null -> Text(error)
                hint != null -> Text(hint)
            }
        },
        trailingIcon = { if (locked) Icon(Icons.Rounded.Lock, contentDescription = null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        modifier = m.onFocusChanged {
            if (it.isFocused) scope.launch {
                delay(200)
                bringIntoViewRequester.bringIntoView()
            }
        }
    )
}

@HiltViewModel
class AddRoomSheetPaywallHolder @Inject constructor(
    val paywallBus: PaywallBus
) : androidx.lifecycle.ViewModel()