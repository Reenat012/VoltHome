package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus
import ru.mugalimov.volthome.domain.model.cable.CableLineCalculation
import ru.mugalimov.volthome.domain.model.cable.CableInsulation
import ru.mugalimov.volthome.domain.model.cable.CableInstallationMethod
import ru.mugalimov.volthome.domain.model.cable.ConductorMaterial
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CableLineEditorSheet(
    editor: ExplicationViewModel.CableEditorState,
    calculation: CableLineCalculation?,
    onDismiss: () -> Unit,
    onLengthChange: (String) -> Unit,
    onManualSectionEnabled: (Boolean) -> Unit,
    onManualSectionChange: (String) -> Unit,
    onMaterialChange: (ConductorMaterial) -> Unit,
    onInsulationChange: (CableInsulation) -> Unit,
    onInstallationMethodChange: (CableInstallationMethod) -> Unit,
    onAmbientTemperatureChange: (String) -> Unit,
    onGroupedCircuitsChange: (String) -> Unit,
    onMaxVoltageDropChange: (String) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("Параметры кабельной линии", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Группа ${editor.group.groupNumber} · ${editor.group.roomName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Text("Трасса", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = editor.lengthText,
                onValueChange = onLengthChange,
                label = { Text("Длина трассы, м") },
                supportingText = { Text("Длина от щита до наиболее удалённой точки линии") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Задать сечение вручную", fontWeight = FontWeight.Medium)
                    Text("Автоматический выбор останется проверяемым", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = editor.useManualSection, onCheckedChange = onManualSectionEnabled)
            }
            if (editor.useManualSection) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editor.manualSectionText,
                    onValueChange = onManualSectionChange,
                    label = { Text("Сечение, мм²") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            Text("Условия прокладки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Эти значения сохраняются как настройки проекта и применяются к следующим линиям.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text("Материал проводника", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConductorMaterial.entries.forEach { material ->
                    FilterChip(
                        selected = editor.defaults.material == material,
                        onClick = { onMaterialChange(material) },
                        label = { Text(material.title) }
                    )
                }
            }
            Text("Изоляция", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CableInsulation.entries.forEach { insulation ->
                    FilterChip(
                        selected = editor.defaults.insulation == insulation,
                        onClick = { onInsulationChange(insulation) },
                        label = { Text(insulation.title) }
                    )
                }
            }
            Text("Способ прокладки", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CableInstallationMethod.entries.forEach { method ->
                    FilterChip(
                        selected = editor.defaults.installationMethod == method,
                        onClick = { onInstallationMethodChange(method) },
                        label = { Text(method.title) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editor.ambientTemperatureText,
                    onValueChange = onAmbientTemperatureChange,
                    label = { Text("Темп., °C") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = editor.groupedCircuitsText,
                    onValueChange = onGroupedCircuitsChange,
                    label = { Text("Цепей рядом") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = editor.maxVoltageDropText,
                    onValueChange = onMaxVoltageDropChange,
                    label = { Text("ΔU, %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            editor.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            calculation?.let { result ->
                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Text("Последний результат", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ResultLine("Кабель", result.cable.compactLabel)
                ResultLine("Материал", result.cable.material.title)
                ResultLine("Прокладка", result.input.defaults.installationMethod.title)
                ResultLine("Допустимый ток Iz", "${format(result.correctedAmpacityA)} А")
                ResultLine("Падение напряжения", result.voltageDropPercent?.let { "${format(it)}%" } ?: "Не рассчитано")
                ResultLine("Статус", result.status.title())
                Spacer(Modifier.height(8.dp))
                result.checks.forEach { check ->
                    Text("• ${check.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSave,
                enabled = !editor.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (editor.isSaving) "Расчёт…" else "Рассчитать и сохранить")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun CableCalculationStatus.title(): String = when (this) {
    CableCalculationStatus.PRELIMINARY -> "Предварительно"
    CableCalculationStatus.PASSED -> "Проверки по току и ΔU пройдены"
    CableCalculationStatus.WARNING -> "Есть предупреждения"
    CableCalculationStatus.FAILED -> "Требует изменения"
}

private fun format(value: Double): String = String.format(java.util.Locale("ru", "RU"), "%.2f", value)
