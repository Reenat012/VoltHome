package ru.mugalimov.volthome.ui.components.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.ui.utilities.bringIntoViewOnFocus
import ru.mugalimov.volthome.ui.utilities.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceParamsEditor(
    // FREE-поля
    name: String,
    onNameChange: (String) -> Unit,
    nameError: String? = null,

    powerText: String,
    onPowerTextChange: (String) -> Unit,
    powerError: String?,

    // PRO-поля
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

    // Ограничение PRO-функций
    locked: Boolean,
    onLockedClick: () -> Unit,

    bringIntoViewRequester: androidx.compose.foundation.relocation.BringIntoViewRequester? = null,
    scope: CoroutineScope? = null,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // FREE: название устройства
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Название") },
            singleLine = true,
            isError = nameError != null,
            supportingText = { nameError?.let { Text(it) } },
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
            // FREE: мощность
            OutlinedTextField(
                value = powerText,
                onValueChange = onPowerTextChange,
                label = { Text("Мощность (Вт)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = { Icon(Icons.Rounded.ElectricBolt, contentDescription = null) },
                isError = powerError != null,
                supportingText = { powerError?.let { Text(it) } },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .maybeBringIntoView(bringIntoViewRequester, scope)
            )

            // PRO: тип устройства
            EnumDropdownField(
                label = "Тип устройства",
                value = deviceType,
                values = DeviceType.values().toList(),
                valueLabel = { it.label(context) },
                locked = locked,
                onLockedClick = onLockedClick,
                onValueChange = onDeviceTypeChange,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // PRO: коэффициент мощности
            LockedDecimalField(
                label = "Коэфф. мощности (PF)",
                value = powerFactorText,
                locked = locked,
                hint = "Влияет на расчёт тока",
                error = powerFactorError,
                onLockedClick = onLockedClick,
                onValueChange = onPowerFactorTextChange,
                modifier = Modifier.weight(1f),
                bringIntoViewRequester = bringIntoViewRequester,
                scope = scope
            )

            // PRO: коэффициент спроса
            LockedDecimalField(
                label = "Коэфф. спроса",
                value = demandRatioText,
                locked = locked,
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
            // PRO: напряжение
            VoltageTypeDropdownField(
                label = "Напряжение",
                value = voltageType,
                locked = locked,
                onLockedClick = onLockedClick,
                onValueChange = onVoltageTypeChange,
                modifier = Modifier.weight(1f)
            )

            // PRO: подключение розеткой
            YesNoDropdownField(
                label = "Подключение розеткой",
                value = requiresSocketConnection,
                locked = locked,
                onLockedClick = onLockedClick,
                onValueChange = onRequiresSocketConnectionChange,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // PRO: есть двигатель
            YesNoDropdownField(
                label = "Есть двигатель",
                value = hasMotor,
                locked = locked,
                onLockedClick = onLockedClick,
                onValueChange = onHasMotorChange,
                modifier = Modifier.weight(1f)
            )

            // PRO: выделенная линия
            YesNoDropdownField(
                label = "Выделенная линия",
                value = requiresDedicatedCircuit,
                locked = locked,
                onLockedClick = onLockedClick,
                onValueChange = onRequiresDedicatedCircuitChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/* -------------------- Вспомогательные блоки -------------------- */

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

    // Если поле заблокировано, сразу открываем paywall.
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
                if (locked) {
                    Icon(Icons.Rounded.Lock, contentDescription = null)
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                // Гарантируем, что даже на readOnly/locked anchor
                // пользователь не получит "тишину" при тапе.
                .clickable { open() }
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

    // Если поле заблокировано, сразу открываем paywall.
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
                if (locked) {
                    Icon(Icons.Rounded.Lock, contentDescription = null)
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                // Гарантируем, что даже на readOnly/locked anchor
                // пользователь не получит "тишину" при тапе.
                .clickable { open() }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("AC 1ф") },
                onClick = {
                    expanded = false
                    onValueChange(VoltageType.AC_1PHASE)
                }
            )

            DropdownMenuItem(
                text = { Text("AC 3ф") },
                onClick = {
                    expanded = false
                    onValueChange(VoltageType.AC_3PHASE)
                }
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
    // Не вешаем clickable прямо на OutlinedTextField:
    // такие клики иногда ведут себя нестабильно.
    // Вместо этого кладём прозрачный overlay поверх поля.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .maybeBringIntoView(bringIntoViewRequester, scope)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (!locked) onValueChange(it) },
            label = { Text(label) },
            enabled = true,
            readOnly = locked,
            singleLine = true,
            isError = error != null && !locked,
            supportingText = {
                when {
                    locked && hint != null -> Text(hint)
                    !locked && error != null -> Text(error)
                    hint != null -> Text(hint)
                }
            },
            trailingIcon = {
                if (locked) {
                    Icon(Icons.Rounded.Lock, contentDescription = null)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (locked) {
            // Прозрачный слой гарантирует,
            // что tap по любой части заблокированного поля откроет paywall.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onLockedClick() }
            )
        }
    }
}

private fun Modifier.maybeBringIntoView(
    bringIntoViewRequester: androidx.compose.foundation.relocation.BringIntoViewRequester?,
    scope: CoroutineScope?
): Modifier {
    if (bringIntoViewRequester == null || scope == null) return this

    return this.onFocusChanged {
        bringIntoViewOnFocus(
            scope = scope,
            requester = bringIntoViewRequester,
            focused = it.isFocused
        )
    }
}