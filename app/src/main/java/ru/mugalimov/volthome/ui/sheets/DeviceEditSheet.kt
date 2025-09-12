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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.viewmodel.DeviceEditViewModel
import ru.mugalimov.volthome.ui.viewmodel.DeviceEditViewModel.PowerUnit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import ru.mugalimov.volthome.core.validation.InputConstraints

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceEditSheet(
    deviceId: Long,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    vm: DeviceEditViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(deviceId) { vm.load(deviceId) }

    val ui = vm.ui.collectAsState().value
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
                .bringIntoViewRequester(bringIntoViewRequester)
                .imePadding()
        ) {
            Text("Редактирование устройства")

            Spacer(Modifier.height(12.dp))

            // Название — полноширинное, аккуратное
            OutlinedTextField(
                value = ui.name,
                onValueChange = vm::setName,
                label = { Text("Название") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .onFocusChanged {
                        if (it.isFocused) {
                            scope.launch {
                                delay(200)
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    }
            )

            Spacer(Modifier.height(12.dp))

            // ——— Сетка 2×3 «как в прошлом примере» ———
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Ряд 1: Мощность | Тип устройства (readonly)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = ui.powerText,
                        onValueChange = vm::setPowerText,
                        label = { Text("Мощность (Вт)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = ui.powerError != null,
                        supportingText = { Text(ui.powerError ?: "Допустимо от ${ru.mugalimov.volthome.core.validation.InputConstraints.MIN_POWER_W} до ${InputConstraints.MAX_POWER_W} Вт") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    scope.launch {
                                        delay(200)
                                        bringIntoViewRequester.bringIntoView()
                                    }
                                }
                            }
                    )

                    // ui.deviceTypeLabel — из VM (readonly)
                    Box(Modifier.weight(1f)) {
                        ReadonlyField(
                            label = "Тип устройства",
                            value = ui.deviceTypeLabel
                        )
                    }
                }

                // Ряд 2: PF | Коэфф. спроса (оба readonly)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        ReadonlyField(
                            label = "Коэфф. мощности (PF)",
                            value = ui.powerFactorText
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        ReadonlyField(
                            label = "Коэфф. спроса",
                            value = ui.demandRatioText
                        )
                    }
                }

                // Ряд 3: Напряжение | пустая ячейка (резерв)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        ReadonlyField(
                            label = "Напряжение",
                            value = ui.voltageText
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !ui.isSaving && ui.powerError == null,
                    onClick = {
                        vm.save(
                            onSuccess = { onSaved(); onDismiss() },
                            onError = { /* показать snackbar снаружи */ }
                        )
                    }
                ) {
                    Text(if (ui.isSaving) "Сохранение…" else "Сохранить")
                }
                Button(onClick = onDismiss) { Text("Отмена") }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReadonlyField(
    label: String,
    value: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = false,
        singleLine = true,
        trailingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Lock, contentDescription = null) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    )
}