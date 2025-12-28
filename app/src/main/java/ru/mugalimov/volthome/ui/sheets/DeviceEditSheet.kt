package ru.mugalimov.volthome.ui.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.ui.components.device.DeviceParamsEditor
import ru.mugalimov.volthome.ui.components.device.DeviceParamsEditorGate
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsDraft
import ru.mugalimov.volthome.ui.components.device.adapter.InMemoryDeviceParamsAdapter
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.viewmodel.DeviceEditViewModel
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceEditSheet(
    deviceId: Long,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    vm: DeviceEditViewModel = hiltViewModel(),
    paywallBus: PaywallBus = hiltViewModel<DeviceEditSheetPaywallHolder>().paywallBus
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val isPro = LocalUserPlan.current.isPro

    // ✅ Коммит 5: paywall теперь живёт в adapter.onLockedClick()
    val adapter = remember(isPro) {
        InMemoryDeviceParamsAdapter<Long>(
            isPro = isPro,
            paywall = { paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR) }
        )
    }

    LaunchedEffect(deviceId) { vm.load(deviceId) }
    LaunchedEffect(isPro) { vm.setPlan(isPro) }

    val ui = vm.ui.collectAsState().value

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Редактирование устройства")
                    IconButton(
                        enabled = !ui.isSaving
                                && ui.powerError == null
                                && (!isPro || (ui.powerFactorError == null && ui.demandRatioError == null)),
                        onClick = {
                            vm.save(
                                onSuccess = { onSaved(); onDismiss() },
                                onError = { /* snackbar снаружи */ }
                            )
                        }
                    ) { Icon(Icons.Rounded.Check, contentDescription = "Сохранить") }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
                .bringIntoViewRequester(bringIntoViewRequester)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DeviceParamsEditor(
                // ✅ Коммит 5: locked click только через adapter.onLockedClick()
                gate = DeviceParamsEditorGate(
                    locked = !isPro,
                    onLockedClick = adapter::onLockedClick
                ),

                name = ui.name,
                onNameChange = vm::setName,
                nameError = ui.nameError,

                powerText = ui.powerText,
                onPowerTextChange = vm::setPowerText,
                powerError = ui.powerError,

                deviceType = ui.deviceType,
                onDeviceTypeChange = vm::setDeviceType,

                powerFactorText = ui.powerFactorText,
                onPowerFactorTextChange = vm::setPowerFactorText,
                powerFactorError = ui.powerFactorError,

                demandRatioText = ui.demandRatioText,
                onDemandRatioTextChange = vm::setDemandRatioText,
                demandRatioError = ui.demandRatioError,

                voltageType = ui.voltageType,
                onVoltageTypeChange = vm::setVoltageType,

                hasMotor = ui.hasMotor,
                onHasMotorChange = vm::setHasMotor,
                requiresDedicatedCircuit = ui.requiresDedicatedCircuit,
                onRequiresDedicatedCircuitChange = vm::setRequiresDedicatedCircuit,
                requiresSocketConnection = ui.requiresSocketConnection,
                onRequiresSocketConnectionChange = vm::setRequiresSocketConnection,

                bringIntoViewRequester = bringIntoViewRequester,
                scope = scope
            )

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !ui.isSaving
                            && ui.nameError == null
                            && ui.powerError == null
                            && (!isPro || (ui.powerFactorError == null && ui.demandRatioError == null)),
                    onClick = {
                        vm.save(
                            onSuccess = { onSaved(); onDismiss() },
                            onError = { /* snackbar */ }
                        )
                    }
                ) { Text(if (ui.isSaving) "Сохранение…" else "Сохранить") }

                Button(onClick = onDismiss) { Text("Отмена") }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class DeviceEditSheetPaywallHolder @Inject constructor(
    val paywallBus: PaywallBus
) : androidx.lifecycle.ViewModel()