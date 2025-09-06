package ru.mugalimov.volthome.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.ui.viewmodel.DeviceEditViewModel
import ru.mugalimov.volthome.ui.viewmodel.DeviceEditViewModel.PowerUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceEditSheet(
    deviceId: Long,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    vm: DeviceEditViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
        ) {
            Text("Редактирование устройства")

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = ui.name,
                onValueChange = vm::setName,
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = ui.powerText,
                onValueChange = vm::setPowerText,
                label = { Text("Мощность") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ui.unit == PowerUnit.W,
                    onClick = { vm.setUnit(PowerUnit.W) },
                    label = { Text("Вт") }
                )
                FilterChip(
                    selected = ui.unit == PowerUnit.kW,
                    onClick = { vm.setUnit(PowerUnit.kW) },
                    label = { Text("кВт") }
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !ui.isSaving,
                    onClick = { vm.save(onSuccess = { onSaved(); onDismiss() }, onError = { /* показать snackbar снаружи */ }) }
                ) {
                    Text(if (ui.isSaving) "Сохранение…" else "Сохранить")
                }
                Button(onClick = onDismiss) { Text("Отмена") }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}