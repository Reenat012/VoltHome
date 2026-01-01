package ru.mugalimov.volthome.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.ui.components.device.DeviceParamsEditor
import ru.mugalimov.volthome.ui.components.device.adapter.DeviceParamsDraft
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.viewmodel.DeviceEditViewModel
import ru.mugalimov.volthome.ui.viewmodel.DeviceEditVmAdapter
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceEditSheet(
    deviceId: Long,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    vm: DeviceEditViewModel = hiltViewModel(),
    paywallHolder: DeviceEditSheetPaywallHolder = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val isPro = LocalUserPlan.current.isPro

    // ✅ Коммит 2: DeviceEdit работает через DeviceEditVmAdapter (vm.ui -> draft/errors/callbacks)
    val adapter = remember(isPro, vm, paywallHolder) {
        DeviceEditVmAdapter(
            isPro = isPro,
            paywall = paywallHolder::onAdvancedEditorLocked,
            vm = vm
        )
    }

    LaunchedEffect(deviceId) { vm.load(deviceId) }
    LaunchedEffect(isPro) { vm.setPlan(isPro) }

    val ui = vm.ui.collectAsState().value

    // ✅ editor state берём из adapter, а не пробрасываем поля вручную
    val st = adapter.state(
        key = deviceId,
        seed = DeviceParamsDraft(
            name = "",
            powerText = "",
            deviceType = DeviceType.SOCKET,
            powerFactorText = "",
            demandRatioText = "",
            voltageType = VoltageType.AC_1PHASE,
            hasMotor = false,
            requiresDedicatedCircuit = false,
            requiresSocketConnection = false
        )
    )

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
                                && st.errors.nameError == null
                                && st.errors.powerError == null
                                && (!isPro || (st.errors.powerFactorError == null && st.errors.demandRatioError == null)),
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
                locked = !isPro,
                onLockedClick = adapter::onLockedClick,

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

                bringIntoViewRequester = bringIntoViewRequester,
                scope = scope
            )

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !ui.isSaving
                            && st.errors.nameError == null
                            && st.errors.powerError == null
                            && (!isPro || (st.errors.powerFactorError == null && st.errors.demandRatioError == null)),
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
    private val paywallBus: PaywallBus
) : androidx.lifecycle.ViewModel() {

    fun onAdvancedEditorLocked() {
        paywallBus.request(ProFeature.ADVANCED_DEVICE_EDITOR)
    }
}