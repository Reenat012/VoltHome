package ru.mugalimov.volthome.ui.components.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.core.validation.InputConstraints
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.VoltageType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceParamsEditor(
    // FREE
    name: String,
    onNameChange: (String) -> Unit,
    powerText: String,
    onPowerTextChange: (String) -> Unit,
    powerError: String?,

    // PRO
    deviceType: DeviceType,
    onDeviceTypeChange: (DeviceType) -> Unit,

    powerFactorText: String,
    onPowerFactorTextChange: (String) -> Unit,
    powerFactorError: String?,

    demandRatioText: String,
    onDemandRatioTextChange: (String) -> Unit,
    demandRatioError: String?,

    voltageType: VoltageType,
    onVoltageTypeChange: (VoltageType) -> Unit,

    hasMotor: Boolean,
    onHasMotorChange: (Boolean) -> Unit,
    requiresDedicatedCircuit: Boolean,
    onRequiresDedicatedCircuitChange: (Boolean) -> Unit,
    requiresSocketConnection: Boolean,
    onRequiresSocketConnectionChange: (Boolean) -> Unit,

    // gating
    isPro: Boolean,
    onLockedClick: () -> Unit,

    // UX helpers (как у тебя)
    bringIntoViewRequester: androidx.compose.foundation.relocation.BringIntoViewRequester? = null,
    scope: CoroutineScope? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { onNameChange(it.take(80)) },
            label = { Text("Название") },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .maybeBringIntoView(bringIntoViewRequester, scope)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = powerText,
                onValueChange = onPowerTextChange,
                label = { Text("Мощность (Вт)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = { Icon(Icons.Rounded.ElectricBolt, contentDescription = null) },
                isError = powerError != null,
                supportingText = {
                    Text(
                        powerError ?: "Допустимо от ${InputConstraints.MIN_POWER_W} до ${InputConstraints.MAX_POWER_W} Вт"
                    )
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .maybeBringIntoView(bringIntoViewRequester, scope)
            )

            EnumDropdownField(
                label = "Тип устройства",
                value = deviceType,
                values = DeviceType.values().toList(),
                valueLabel = { it.name },
                locked = !isPro,
                onLockedClick = onLockedClick,
                onValueChange = onDeviceTypeChange,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LockedDecimalField(
                label = "Коэфф. мощности (PF)",
                value = powerFactorText,
                locked = !isPro,
                hint = "Влияет на расчёт тока",
                error = powerFactorError,
                onLockedClick = onLockedClick,
                onValueChange = onPowerFactorTextChange,
                modifier = Modifier.weight(1f),
                bringIntoViewRequester = bringIntoViewRequester,
                scope = scope
            )

            LockedDecimalField(
                label = "Коэфф. спроса",
                value = demandRatioText,
                locked = !isPro,
                hint = "Учитывает реальную нагрузку",
                error = demandRatioError,
                onLockedClick = onLockedClick,
                onValueChange = onDemandRatioTextChange,
                modifier = Modifier.weight(1f),
                bringIntoViewRequester = bringIntoViewRequester,
                scope = scope
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VoltageTypeDropdownField(
                label = "Напряжение",
                value = voltageType,
                locked = !isPro,
                onLockedClick = onLockedClick,
                onValueChange = onVoltageTypeChange,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            YesNoDropdownField(
                label = "Есть двигатель",
                value = hasMotor,
                locked = !isPro,
                onLockedClick = onLockedClick,
                onValueChange = onHasMotorChange,
                modifier = Modifier.weight(1f)
            )
            YesNoDropdownField(
                label = "Выделенная линия",
                value = requiresDedicatedCircuit,
                locked = !isPro,
                onLockedClick = onLockedClick,
                onValueChange = onRequiresDedicatedCircuitChange,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            YesNoDropdownField(
                label = "Подключение розеткой",
                value = requiresSocketConnection,
                locked = !isPro,
                onLockedClick = onLockedClick,
                onValueChange = onRequiresSocketConnectionChange,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

/* -------------------- building blocks -------------------- */

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

    val open = {
        if (locked) onLockedClick() else expanded = true
    }

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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(valueLabel(item)) },
                    onClick = {
                        expanded = false
                        onValueChange(item)
                    }
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

    val open = {
        if (locked) onLockedClick() else expanded = true
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open() },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        OutlinedTextField(
            value = when (value) {
                VoltageType.AC_1PHASE -> "AC 1ф"
                VoltageType.AC_3PHASE -> "AC 3ф"
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("AC 1ф") },
                onClick = { expanded = false; onValueChange(VoltageType.AC_1PHASE) }
            )
            DropdownMenuItem(
                text = { Text("AC 3ф") },
                onClick = { expanded = false; onValueChange(VoltageType.AC_3PHASE) }
            )
            DropdownMenuItem(
                enabled = false,
                text = { Text("DC — скоро") },
                onClick = {}
            )
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
    bringIntoViewRequester: androidx.compose.foundation.relocation.BringIntoViewRequester?,
    scope: CoroutineScope?
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
        isError = error != null,
        supportingText = {
            when {
                error != null -> Text(error)
                hint != null -> Text(hint)
            }
        },
        trailingIcon = { if (locked) Icon(Icons.Rounded.Lock, contentDescription = null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = m.maybeBringIntoView(bringIntoViewRequester, scope)
    )
}

private fun Modifier.maybeBringIntoView(
    bringIntoViewRequester: androidx.compose.foundation.relocation.BringIntoViewRequester?,
    scope: CoroutineScope?
): Modifier {
    if (bringIntoViewRequester == null || scope == null) return this
    return this.onFocusChanged {
        if (it.isFocused) scope.launch {
            delay(200)
            bringIntoViewRequester.bringIntoView()
        }
    }
}