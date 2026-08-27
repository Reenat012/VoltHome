package ru.mugalimov.volthome.ui.components.device

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    name: String,
    onNameChange: (String) -> Unit,
    nameError: String? = null,
    powerText: String,
    onPowerTextChange: (String) -> Unit,
    powerError: String?,
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
    locked: Boolean,
    onLockedClick: () -> Unit,
    bringIntoViewRequester: androidx.compose.foundation.relocation.BringIntoViewRequester? = null,
    scope: CoroutineScope? = null,
) {
    val context = LocalContext.current
    var advancedExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Основные параметры",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Название") },
            singleLine = true,
            isError = nameError != null,
            supportingText = nameError?.let { error -> ({ Text(error) }) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().maybeBringIntoView(bringIntoViewRequester, scope)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = powerText,
                onValueChange = onPowerTextChange,
                label = { Text("Мощность, Вт") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = { Icon(Icons.Rounded.ElectricBolt, contentDescription = null) },
                isError = powerError != null,
                supportingText = if (powerError != null) {
                    { Text(powerError) }
                } else {
                    { Text("Для мощных приборов можно указать значение выше 5 кВт") }
                },
                shape = MaterialTheme.shapes.large,
                modifier = (if (locked) Modifier.fillMaxWidth() else Modifier.weight(1f))
                    .maybeBringIntoView(bringIntoViewRequester, scope)
            )
            if (!locked) {
                DeviceTypeField(
                    value = deviceType,
                    label = { it.label(context) },
                    onValueChange = onDeviceTypeChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (locked) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLockedClick),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Расширенные параметры", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Коэффициенты, напряжение и способ подключения",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("PRO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { advancedExpanded = !advancedExpanded },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Расширенные параметры", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Коэффициенты и параметры подключения",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        if (advancedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (advancedExpanded) "Свернуть" else "Развернуть"
                    )
                }
            }

            AnimatedVisibility(
                visible = advancedExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CompactDecimalField(
                            label = "cos φ",
                            value = powerFactorText,
                            error = powerFactorError,
                            onValueChange = onPowerFactorTextChange,
                            modifier = Modifier.weight(1f),
                            bringIntoViewRequester = bringIntoViewRequester,
                            scope = scope
                        )
                        CompactDecimalField(
                            label = "Спрос",
                            value = demandRatioText,
                            error = demandRatioError,
                            onValueChange = onDemandRatioTextChange,
                            modifier = Modifier.weight(1f),
                            bringIntoViewRequester = bringIntoViewRequester,
                            scope = scope
                        )
                    }

                    Text(
                        text = "cos φ влияет на расчёт тока, коэффициент спроса — на одновременную нагрузку.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Напряжение", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = voltageType == VoltageType.AC_1PHASE,
                                onClick = { onVoltageTypeChange(VoltageType.AC_1PHASE) },
                                label = { Text("AC · 1 фаза") }
                            )
                            FilterChip(
                                selected = voltageType == VoltageType.AC_3PHASE,
                                onClick = { onVoltageTypeChange(VoltageType.AC_3PHASE) },
                                label = { Text("AC · 3 фазы") }
                            )
                        }
                    }

                    BooleanSetting("Есть двигатель", hasMotor, onHasMotorChange)
                    BooleanSetting("Выделенная линия", requiresDedicatedCircuit, onRequiresDedicatedCircuitChange)
                    BooleanSetting("Подключение через розетку", requiresSocketConnection, onRequiresSocketConnectionChange)
                    Text(
                        text = "Этот признак описывает способ подключения прибора. Отдельное УЗО выбирается для розеточной линии, а не для каждого подключённого к ней устройства.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceTypeField(
    value: DeviceType,
    label: (DeviceType) -> String,
    onValueChange: (DeviceType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = label(value),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Тип") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DeviceType.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(label(item)) },
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
private fun CompactDecimalField(
    label: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    bringIntoViewRequester: androidx.compose.foundation.relocation.BringIntoViewRequester?,
    scope: CoroutineScope?
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> ({ Text(message) }) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.maybeBringIntoView(bringIntoViewRequester, scope)
    )
}

@Composable
private fun BooleanSetting(label: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onValueChange(!value) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}

private fun Modifier.maybeBringIntoView(
    requester: androidx.compose.foundation.relocation.BringIntoViewRequester?,
    scope: CoroutineScope?
): Modifier {
    if (requester == null || scope == null) return this
    return onFocusChanged {
        bringIntoViewOnFocus(scope = scope, requester = requester, focused = it.isFocused)
    }
}
