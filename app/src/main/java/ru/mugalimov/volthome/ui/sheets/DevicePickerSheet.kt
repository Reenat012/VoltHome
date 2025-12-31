package ru.mugalimov.volthome.ui.sheets

import android.annotation.SuppressLint
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest as DomainDeviceCreateRequest
import ru.mugalimov.volthome.domain.use_case.AddDevicesToRoomUseCase
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsDraft
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsValidator
import ru.mugalimov.volthome.ui.components.device.adapter.InMemoryDeviceParamsAdapter
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.utilities.bringIntoViewOnFocus
import javax.inject.Inject

@SuppressLint("UnrememberedMutableState")
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

    // ✅ paywallBus.request — только здесь (создание адаптера)
    val editorAdapter = remember(isPro) {
        InMemoryDeviceParamsAdapter<String>(
            isPro = isPro,
            paywall = { paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR) }
        )
    }

    var search by remember { mutableStateOf("") }
    val qty = remember { mutableStateMapOf<Long, Int>() }

    val totalTop by remember { derivedStateOf { qty.values.sum() } }

    // ✅ aggregated validation: без локальных кешей ошибок (только adapter)
    val byId = remember(defaultDevices) { defaultDevices.associateBy { it.id } }

    val hasErrorsTop by derivedStateOf<Boolean> {
        val selectedKeys = qty.filterValues { it > 0 }.keys.map { it.toString() }

        editorAdapter.hasAnyErrors(
            keys = selectedKeys,
            seedForKey = { key ->
                val id = key.toLong()
                val def = byId[id] ?: return@hasAnyErrors DeviceParamsDraft(
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
                e.nameError != null ||
                        e.powerError != null ||
                        e.powerFactorError != null ||
                        e.demandRatioError != null
            }
        )
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val filtered = remember(search, defaultDevices) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) defaultDevices else defaultDevices.filter {
            it.name.lowercase().contains(q)
        }
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

                    IconButton(
                        onClick = {
                            scope.launch {
                                val reqs = buildRequestsForPicker(
                                    defaults = defaultDevices,
                                    qtyMap = qty,
                                    editorAdapter = editorAdapter,
                                    isPro = isPro
                                )
                                if (reqs.isEmpty()) {
                                    onDismiss(); return@launch
                                }
                                val ids = helperVm.add(roomId, reqs)
                                onAdded(ids)
                                onDismiss()
                            }
                        },
                        enabled = totalTop > 0 && !hasErrorsTop
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "Добавить")
                    }
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
                        bringIntoViewOnFocus(
                            scope = scope,
                            requester = bringIntoViewRequester,
                            focused = it.isFocused
                        )
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
                    val key = def.id.toString()
                    val count = qty[def.id] ?: 0

                    val st = editorAdapter.state(
                        key = key,
                        seed = DeviceParamsDraft(
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
                    )

                    DevicePickerDeviceCard(
                        def = def,
                        count = count,
                        onDec = { qty[def.id] = (count - 1).coerceAtLeast(0) },
                        onInc = { qty[def.id] = (count + 1).coerceAtMost(99) },
                        st = st,
                        isPro = isPro,
                        onLockedClick = editorAdapter::onLockedClick,
                        bringIntoViewRequester = bringIntoViewRequester,
                        scope = scope
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = totalTop > 0 && !hasErrorsTop,
                    onClick = {
                        scope.launch {
                            val reqs = buildRequestsForPicker(
                                defaults = defaultDevices,
                                qtyMap = qty,
                                editorAdapter = editorAdapter,
                                isPro = isPro
                            )
                            if (reqs.isEmpty()) {
                                onDismiss(); return@launch
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePickerDeviceCard(
    def: DefaultDevice,
    count: Int,
    onDec: () -> Unit,
    onInc: () -> Unit,
    st: ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsEditorState<String>,
    isPro: Boolean,
    onLockedClick: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    scope: CoroutineScope
) {
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
                    IconButton(onClick = onDec) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Уменьшить")
                    }

                    Box(
                        Modifier.width(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = count.toString(), textAlign = TextAlign.Center)
                    }

                    IconButton(onClick = onInc) {
                        Icon(Icons.Rounded.Add, contentDescription = "Увеличить")
                    }

                    IconButton(onClick = { st.onExpandedChange(!st.isExpanded) }) {
                        Icon(
                            if (st.isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }

            AnimatedVisibility(st.isExpanded) {
                Column {
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = st.draft.name,
                        onValueChange = st.onNameChange,
                        label = { Text("Название") },
                        singleLine = true,
                        isError = st.errors.nameError != null,
                        supportingText = { st.errors.nameError?.let { Text(it) } },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .onFocusChanged {
                                bringIntoViewOnFocus(
                                    scope = scope,
                                    requester = bringIntoViewRequester,
                                    focused = it.isFocused
                                )
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
                                isError = error != null,
                                supportingText = { error?.let { Text(it) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .onFocusChanged {
                                        bringIntoViewOnFocus(
                                            scope = scope,
                                            requester = bringIntoViewRequester,
                                            focused = it.isFocused
                                        )
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            bringIntoViewOnFocus(
                scope = scope,
                requester = bringIntoViewRequester,
                focused = it.isFocused
            )
        }
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
    editorAdapter: InMemoryDeviceParamsAdapter<String>,
    isPro: Boolean
): List<DomainDeviceCreateRequest> {
    val byId = defaults.associateBy { it.id }
    val out = mutableListOf<DomainDeviceCreateRequest>()

    for ((id, count) in qtyMap) {
        if (count <= 0) continue
        val def = byId[id] ?: continue
        val key = id.toString()

        val seed = DeviceParamsDraft(
            name = def.name,
            powerText = def.power.toString(),
            deviceType = def.deviceType,
            demandRatioText = def.demandRatio.format(2),
            powerFactorText = def.powerFactor.format(2),
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

        out += DomainDeviceCreateRequest(
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

private fun Double.format(digits: Int) = "%.${digits}f".format(this).replace(',', '.')