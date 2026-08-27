
package ru.mugalimov.volthome.ui.sheets

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.DefaultDevice
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.use_case.AddDevicesToRoomUseCase
import ru.mugalimov.volthome.ui.components.device.DeviceParamsEditor
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsDraft
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsEditorState
import ru.mugalimov.volthome.ui.components.device.adapter.InMemoryDeviceParamsAdapter
import ru.mugalimov.volthome.ui.format.UiTextFormat
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.utilities.bringIntoViewOnFocus
import java.util.Locale
import javax.inject.Inject
import ru.mugalimov.volthome.domain.model.create.DeviceCreateRequest as DomainDeviceCreateRequest

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(
    roomId: Long,
    defaultDevices: List<DefaultDevice>,
    onDismiss: () -> Unit,
    onAdded: (List<Long>) -> Unit,
    helperVm: DevicePickerHelperVm = hiltViewModel(),
    paywallHolder: DevicePickerSheetPaywallHolder = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val canUseExtendedEditor = LocalUserPlan.current.capabilities.extendedDeviceEditor

    // ✅ adapter на уровне sheet (НЕ внутри Lazy-item)
    val editorAdapter = remember(canUseExtendedEditor, paywallHolder) {
        InMemoryDeviceParamsAdapter<String>(
            isAllowed = canUseExtendedEditor,
            paywall = paywallHolder::onAdvancedEditorLocked
        )
    }

    var search by remember { mutableStateOf("") }
    val qty = remember { mutableStateMapOf<Long, Int>() }

    val totalTop by remember { derivedStateOf { qty.values.sum() } }

    // ✅ aggregated validation: только adapter
    val byId = remember(defaultDevices) { defaultDevices.associateBy { it.id } }

    val hasErrorsTop by remember {
        derivedStateOf {
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
                }
            )
        }
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
                                    canUseExtendedEditor = canUseExtendedEditor
                                )
                                if (reqs.isEmpty()) {
                                    onDismiss()
                                    return@launch
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
            // ✅ поле поиска оставляем (OutlinedTextField допустим)
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
                        isAllowed = canUseExtendedEditor,
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
                                canUseExtendedEditor = canUseExtendedEditor
                            )
                            if (reqs.isEmpty()) {
                                onDismiss()
                                return@launch
                            }
                            val ids = helperVm.add(roomId, reqs)
                            onAdded(ids)
                            onDismiss()
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Добавить")
                }

                Button(onClick = onDismiss) { Text("Отмена") }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DevicePickerDeviceCard(
    def: DefaultDevice,
    count: Int,
    onDec: () -> Unit,
    onInc: () -> Unit,
    st: DeviceParamsEditorState<String>,
    isAllowed: Boolean,
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
                    Text(UiTextFormat.power(def.power.toDouble()))
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

                    // ✅ вместо ручных полей — общий DeviceParamsEditor
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

                        locked = !isAllowed,
                        onLockedClick = onLockedClick,

                        bringIntoViewRequester = bringIntoViewRequester,
                        scope = scope
                    )
                }
            }
        }
    }
}

@HiltViewModel
class DevicePickerSheetPaywallHolder @Inject constructor(
    private val paywallBus: PaywallBus
) : androidx.lifecycle.ViewModel() {

    fun onAdvancedEditorLocked() {
        paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR)
    }
}

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
    canUseExtendedEditor: Boolean
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

        val watts = nd.powerText.toDoubleOrNull()?.toInt() ?: continue

        val pf = if (canUseExtendedEditor) (nd.powerFactorText.toDoubleOrNull() ?: continue) else def.powerFactor
        val dr = if (canUseExtendedEditor) (nd.demandRatioText.toDoubleOrNull() ?: continue) else def.demandRatio

        val type = if (canUseExtendedEditor) nd.deviceType else def.deviceType

        val volt = if (canUseExtendedEditor) {
            when (nd.voltageType) {
                VoltageType.AC_1PHASE -> Voltage(230, VoltageType.AC_1PHASE)
                VoltageType.AC_3PHASE -> Voltage(400, VoltageType.AC_3PHASE)
                VoltageType.DC -> def.voltage
            }
        } else def.voltage

        val hm = if (canUseExtendedEditor) nd.hasMotor else def.hasMotor
        val rd = if (canUseExtendedEditor) nd.requiresDedicatedCircuit else def.requiresDedicatedCircuit
        val rs = if (canUseExtendedEditor) nd.requiresSocketConnection else def.requiresSocketConnection

        out += DomainDeviceCreateRequest(
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

private fun Double.format(digits: Int): String =
    String.format(Locale.US, "%.${digits}f", this)
