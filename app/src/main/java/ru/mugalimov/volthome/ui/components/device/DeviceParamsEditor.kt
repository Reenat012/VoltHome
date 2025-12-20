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

/**
 * Единый UI-редактор параметров устройства.
 *
 * FREE: имя + мощность.
 * PRO: всё остальное (PF/DR/Voltage/DeviceType/flags).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceParamsEditor(
    // базовые (FREE)
    name: String,
    onNameChange: (String) -> Unit,
    powerText: String,
    onPowerTextChange: (String) -> Unit,
    powerError: String?,

    // advanced (PRO)
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

    // UX helpers (как у тебя в AddRoomSheet)
    bringIntoViewRequester: androidx.compose.foundation.relocation.BringIntoViewRequester? = null,
    scope: CoroutineScope? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1) Название (FREE)
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Название") },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .maybeBringIntoView(bringIntoViewRequester, scope)
        )

        // 2) Ряд: Мощность (FREE) | Тип устройства (PRO dropdown)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        powerError
                            ?: "Допустимо от ${InputConstraints.MIN_POWER_W} до ${InputConstraints.MAX_POWER_W} Вт"
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
                hint = null,
                onLockedClick = onLockedClick,
                onValueChange = onDeviceTypeChange,
                modifier = Modifier.weight(1f)
            )
        }

        // 3) Ряд: PF | Кс (PRO — но UI такой же, просто блокируем)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LockedTextField(
                label = "Коэфф. мощности (PF)",
                value = powerFactorText,
                hint = "Влияет на расчёт тока",
                locked = !isPro,
                error = powerFactorError,
                onLockedClick = onLockedClick,
                onValueChange = onPowerFactorTextChange,
                modifier = Modifier.weight(1f),
                bringIntoViewRequester = bringIntoViewRequester,
                scope = scope
            )

            LockedTextField(
                label = "Коэфф. спроса",
                value = demandRatioText,
                hint = "Учитывает реальную нагрузку",
                locked = !isPro,
                error = demandRatioError,
                onLockedClick = onLockedClick,
                onValueChange = onDemandRatioTextChange,
                modifier = Modifier.weight(1f),
                bringIntoViewRequester = bringIntoViewRequester,
                scope = scope
            )
        }

        // 4) Ряд: Напряжение (PRO dropdown по VoltageType)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EnumDropdownField(
                label = "Напряжение",
                value = voltageType,
                values = VoltageType.values().toList(),
                valueLabel = { vt ->
                    when (vt) {
                        VoltageType.AC_1PHASE -> "AC 1ф"
                        VoltageType.AC_3PHASE -> "AC 3ф"
                        VoltageType.DC -> "DC"
                    }
                },
                locked = !isPro,
                hint = "Влияет на ток и фазность",
                onLockedClick = onLockedClick,
                onValueChange = onVoltageTypeChange,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.weight(1f))
        }

        // 5) Остальные флаги (PRO dropdown Да/Нет)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            YesNoDropdownField(
                label = "Есть двигатель",
                value = hasMotor,
                locked = !isPro,
                hint = null,
                onLockedClick = onLockedClick,
                onValueChange = onHasMotorChange,
                modifier = Modifier.weight(1f)
            )
            YesNoDropdownField(
                label = "Выделенная линия",
                value = requiresDedicatedCircuit,
                locked = !isPro,
                hint = null,
                onLockedClick = onLockedClick,
                onValueChange = onRequiresDedicatedCircuitChange,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            YesNoDropdownField(
                label = "Подключение розеткой",
                value = requiresSocketConnection,
                locked = !isPro,
                hint = null,
                onLockedClick = onLockedClick,
                onValueChange = onRequiresSocketConnectionChange,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

/* ----------------- building blocks ----------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdownField(
    label: String,
    value: T,
    values: List<T>,
    valueLabel: (T) -> String,
    locked: Boolean,
    hint: String?,
    onLockedClick: () -> Unit,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val click = {
        if (locked) onLockedClick() else expanded = true
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { click() },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        OutlinedTextField(
            value = valueLabel(value),
            onValueChange = {},
            readOnly = true,
            enabled = true,
            label = { Text(label) },
            supportingText = { if (hint != null) Text(hint) },
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

@Composable
private fun YesNoDropdownField(
    label: String,
    value: Boolean,
    locked: Boolean,
    hint: String?,
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
        hint = hint,
        onLockedClick = onLockedClick,
        onValueChange = onValueChange,
        modifier = modifier
    )
}

@Composable
private fun LockedTextField(
    label: String,
    value: String,
    hint: String?,
    locked: Boolean,
    error: String?,
    onLockedClick: () -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    bringIntoViewRequester: androidx.compose.foundation.relocation.BringIntoViewRequester? = null,
    scope: CoroutineScope? = null,
) {
    val m = modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .let { base ->
            if (locked) base.clickable { onLockedClick() } else base
        }

    OutlinedTextField(
        value = value,
        onValueChange = { if (!locked) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        enabled = true,
        readOnly = locked,
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