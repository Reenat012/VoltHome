// File: volthome/ui/screens/rooms/AddRoomScreen.kt
package ru.mugalimov.volthome.ui.screens.rooms

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.RoomType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest
import ru.mugalimov.volthome.ui.components.device.DeviceParamsEditor
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsDraft
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsEditorState
import ru.mugalimov.volthome.ui.components.device.adapter.InMemoryDeviceParamsAdapter
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.utilities.bringIntoViewOnFocus
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRoomSheet(
    defaultDevices: List<DefaultDevice>,
    roomTypes: List<RoomType>,
    onConfirm: (name: String, roomType: RoomType, devices: List<DeviceCreateRequest>) -> Unit,
    onDismiss: () -> Unit,
    paywallHolder: AddRoomSheetPaywallHolder = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(roomTypes.firstOrNull() ?: RoomType.STANDARD) }

    val qtyMap = remember { mutableStateMapOf<String, Int>() }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val isPro = LocalUserPlan.current.isPro

    // ✅ adapter на уровне экрана, не внутри Lazy-item
    val editorAdapter = remember(isPro, paywallHolder) {
        InMemoryDeviceParamsAdapter<String>(
            isPro = isPro,
            paywall = paywallHolder::onAdvancedEditorLocked
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
                                bringIntoViewOnFocus(
                                    scope = scope,
                                    requester = bringIntoViewRequester,
                                    focused = it.isFocused
                                )
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

                // ✅ стабильный key (не индекс)
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

    st: DeviceParamsEditorState<String>,
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
                    IconButton(onClick = onDec) { Icon(Icons.Rounded.Remove, contentDescription = "Уменьшить") }
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Text(qty.toString()) }
                    IconButton(onClick = onInc) { Icon(Icons.Rounded.Add, contentDescription = "Увеличить") }
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
                    // ✅ весь блок полей — теперь общий DeviceParamsEditor
                    DeviceParamsEditor(
                        name = st.draft.name,
                        onNameChange = st.onNameChange,
                        nameError = st.errors.nameError,

                        powerText = st.draft.powerText,
                        onPowerTextChange = st.onPowerTextChange,
                        powerError = st.errors.powerError,

                        deviceType = st.draft.deviceType,
                        onDeviceTypeChange = st.onDeviceTypeChange,

                        powerFactorText = st.draft.powerFactorText,
                        onPowerFactorTextChange = st.onPowerFactorTextChange,
                        powerFactorError = st.errors.powerFactorError,

                        demandRatioText = st.draft.demandRatioText,
                        onDemandRatioTextChange = st.onDemandRatioTextChange,
                        demandRatioError = st.errors.demandRatioError,

                        voltageType = st.draft.voltageType,
                        onVoltageTypeChange = st.onVoltageTypeChange,

                        hasMotor = st.draft.hasMotor,
                        onHasMotorChange = st.onHasMotorChange,
                        requiresDedicatedCircuit = st.draft.requiresDedicatedCircuit,
                        onRequiresDedicatedCircuitChange = st.onRequiresDedicatedCircuitChange,
                        requiresSocketConnection = st.draft.requiresSocketConnection,
                        onRequiresSocketConnectionChange = st.onRequiresSocketConnectionChange,

                        locked = !isPro,
                        onLockedClick = onLockedClick,

                        bringIntoViewRequester = bringIntoViewRequester,
                        scope = scope
                    )
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

        // ✅ Коммит 6: берём NORMALIZED draft из адаптера (UI не парсит/нормализует)
        val nd = editorAdapter.peekDraftNormalized(key = key, seed = seed)

        val title = nd.name.trim().ifBlank { def.name }

        // nd.powerText уже нормализован валидатором (включая запятую)
        val watts = nd.powerText.toDoubleOrNull()?.toInt() ?: continue

        val pf = if (isPro) (nd.powerFactorText.toDoubleOrNull() ?: continue) else def.powerFactor
        val dr = if (isPro) (nd.demandRatioText.toDoubleOrNull() ?: continue) else def.demandRatio

        val type = if (isPro) nd.deviceType else def.deviceType

        val volt = if (isPro) {
            when (nd.voltageType) {
                VoltageType.AC_1PHASE -> Voltage(220, VoltageType.AC_1PHASE)
                VoltageType.AC_3PHASE -> Voltage(380, VoltageType.AC_3PHASE)
                VoltageType.DC -> def.voltage
            }
        } else def.voltage

        val hm = if (isPro) nd.hasMotor else def.hasMotor
        val rd = if (isPro) nd.requiresDedicatedCircuit else def.requiresDedicatedCircuit
        val rs = if (isPro) nd.requiresSocketConnection else def.requiresSocketConnection

        out += DeviceCreateRequest(
            title = title,
            type = type,
            count = count,
            ratedPowerW = watts,
            powerFactor = pf,
            demandRatio = dr,
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

private fun Double.format(digits: Int): String =
    String.format(Locale.US, "%.${digits}f", this)

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

@HiltViewModel
class AddRoomSheetPaywallHolder @Inject constructor(
    private val paywallBus: PaywallBus
) : androidx.lifecycle.ViewModel() {

    fun onAdvancedEditorLocked() {
        paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR)
    }
}