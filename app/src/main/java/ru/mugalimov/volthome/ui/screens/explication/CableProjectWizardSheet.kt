package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.cable.CableInsulation
import ru.mugalimov.volthome.domain.model.cable.CableInstallationMethod
import ru.mugalimov.volthome.domain.model.cable.ConductorMaterial
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CableProjectWizardSheet(
    state: ExplicationViewModel.CableWizardState,
    onDismiss: () -> Unit,
    onMaterialChange: (ConductorMaterial) -> Unit,
    onInsulationChange: (CableInsulation) -> Unit,
    onInstallationMethodChange: (CableInstallationMethod) -> Unit,
    onAmbientTemperatureChange: (String) -> Unit,
    onGroupedCircuitsChange: (String) -> Unit,
    onMaxVoltageDropChange: (String) -> Unit,
    onLineLengthChange: (Long, String) -> Unit,
    onManualSectionEnabled: (Long, Boolean) -> Unit,
    onManualSectionChange: (Long, String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Расчёт кабельных линий",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (state.step == 1) "Шаг 1 из 2 · Условия проекта" else "Шаг 2 из 2 · Длины и сечения",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (state.step == 1) {
                    ProjectConditionsStep(
                        state = state,
                        onMaterialChange = onMaterialChange,
                        onInsulationChange = onInsulationChange,
                        onInstallationMethodChange = onInstallationMethodChange,
                        onAmbientTemperatureChange = onAmbientTemperatureChange,
                        onGroupedCircuitsChange = onGroupedCircuitsChange,
                        onMaxVoltageDropChange = onMaxVoltageDropChange
                    )
                } else {
                    LineLengthsStep(
                        state = state,
                        onLineLengthChange = onLineLengthChange,
                        onManualSectionEnabled = onManualSectionEnabled,
                        onManualSectionChange = onManualSectionChange
                    )
                }
                state.error?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.step == 2) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f)
                    ) { Text("Назад") }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("Отмена") }
                }
                Button(
                    onClick = if (state.step == 1) onNext else onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            state.isSaving -> "Расчёт…"
                            state.step == 1 -> "К длинам"
                            else -> "Рассчитать"
                        }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProjectConditionsStep(
    state: ExplicationViewModel.CableWizardState,
    onMaterialChange: (ConductorMaterial) -> Unit,
    onInsulationChange: (CableInsulation) -> Unit,
    onInstallationMethodChange: (CableInstallationMethod) -> Unit,
    onAmbientTemperatureChange: (String) -> Unit,
    onGroupedCircuitsChange: (String) -> Unit,
    onMaxVoltageDropChange: (String) -> Unit
) {
    Text(
        text = "Общие параметры применяются ко всем линиям. На следующем шаге можно уточнить сечение каждой трассы.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    Text("Материал проводника", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ConductorMaterial.entries.forEach { material ->
            FilterChip(
                selected = state.defaults.material == material,
                onClick = { onMaterialChange(material) },
                label = { Text(material.title) }
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("Изоляция", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CableInsulation.entries.forEach { insulation ->
            FilterChip(
                selected = state.defaults.insulation == insulation,
                onClick = { onInsulationChange(insulation) },
                label = { Text(insulation.title) }
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("Способ прокладки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CableInstallationMethod.entries.forEach { method ->
            FilterChip(
                selected = state.defaults.installationMethod == method,
                onClick = { onInstallationMethodChange(method) },
                label = { Text(method.title) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.ambientTemperatureText,
        onValueChange = onAmbientTemperatureChange,
        label = { Text("Температура среды, °C") },
        supportingText = { Text("Поправка к допустимому току кабеля") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.groupedCircuitsText,
        onValueChange = onGroupedCircuitsChange,
        label = { Text("Совместно проложенных цепей") },
        supportingText = { Text("Учитывает взаимный нагрев соседних линий") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.maxVoltageDropText,
        onValueChange = onMaxVoltageDropChange,
        label = { Text("Допустимое падение напряжения, %") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LineLengthsStep(
    state: ExplicationViewModel.CableWizardState,
    onLineLengthChange: (Long, String) -> Unit,
    onManualSectionEnabled: (Long, Boolean) -> Unit,
    onManualSectionChange: (Long, String) -> Unit
) {
    Text(
        text = "Укажите длину трассы от щита до наиболее удалённой точки. Пустые линии останутся с предварительным сечением.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(14.dp))
    state.lines.forEach { line ->
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Группа ${line.group.groupNumber} · ${line.group.roomName.orEmpty()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Iрасч ${formatCurrent(line.group.nominalCurrent)} А · автомат ${line.group.breakerType}${line.group.circuitBreaker}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = line.lengthText,
                        onValueChange = { onLineLengthChange(line.group.groupId, it) },
                        label = { Text("Длина, м") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.62f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Проверить своё сечение", style = MaterialTheme.typography.labelLarge)
                        if (!line.useManualSection) {
                            Text(
                                text = "Автоматический выбор",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = line.useManualSection,
                        onCheckedChange = { onManualSectionEnabled(line.group.groupId, it) }
                    )
                }
                if (line.useManualSection) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = line.manualSectionText,
                        onValueChange = { onManualSectionChange(line.group.groupId, it) },
                        label = { Text("Сечение, мм²") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun formatCurrent(value: Double): String =
    String.format(java.util.Locale("ru", "RU"), "%.2f", value)
