package ru.mugalimov.volthome.ui.screens.reconfiguration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.reconfiguration.ReconfigurationImpact
import ru.mugalimov.volthome.ui.components.LoadingView
import ru.mugalimov.volthome.ui.viewmodel.ProjectReconfigurationEvent
import ru.mugalimov.volthome.ui.viewmodel.ProjectReconfigurationViewModel

@Composable
fun ProjectReconfigurationScreen(
    onBack: () -> Unit,
    viewModel: ProjectReconfigurationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showApplyConfirmation by remember { mutableStateOf(false) }
    var showUndoConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectReconfigurationEvent.Message -> snackbarHost.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                }
                Column {
                    Text("Переконфигурация", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Безопасное изменение параметров сети",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.isLoading) {
                LoadingView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        IntroCard()
                    }
                    item {
                        SettingsCard(
                            phaseMode = state.phaseMode,
                            hasInputPower = state.hasInputPower,
                            inputPowerText = state.inputPowerText,
                            enabled = !state.isWorking,
                            onPhaseMode = viewModel::selectPhaseMode,
                            onInputPowerEnabled = viewModel::setInputPowerEnabled,
                            onInputPower = viewModel::setInputPower
                        )
                    }
                    state.error?.let { message ->
                        item { MessageCard(message, isWarning = true) }
                    }
                    state.preview?.let { impact ->
                        item { ImpactCard(impact) }
                        if (impact.hasActiveManualSession) {
                            item {
                                MessageCard(
                                    "Сейчас открыт ручной редактор. Сохраните или отмените его изменения перед переконфигурацией.",
                                    isWarning = true
                                )
                            }
                        } else if (impact.hasManualStructure && impact.requiresStructuralRebuild) {
                            item {
                                MessageCard(
                                    "Ручная структура будет заменена автоматическим расчётом. Перед применением приложение сохранит полную резервную копию.",
                                    isWarning = true
                                )
                            }
                        }
                    }
                    item {
                        if (state.preview == null) {
                            Button(
                                onClick = viewModel::buildPreview,
                                enabled = !state.isWorking,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (state.isWorking) SmallProgress() else Icon(Icons.Rounded.Bolt, null)
                                Text("Показать изменения", modifier = Modifier.padding(start = 8.dp))
                            }
                        } else {
                            Button(
                                onClick = { showApplyConfirmation = true },
                                enabled = !state.isWorking &&
                                    state.preview?.hasChanges == true &&
                                    state.preview?.hasActiveManualSession == false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (state.isWorking) SmallProgress()
                                Text("Применить переконфигурацию")
                            }
                        }
                    }
                    if (state.canUndo) {
                        item {
                            UndoCard(
                                backupLabel = state.backupLabel,
                                enabled = !state.isWorking,
                                onUndo = { showUndoConfirmation = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showApplyConfirmation) {
        AlertDialog(
            onDismissRequest = { showApplyConfirmation = false },
            title = { Text("Применить изменения?") },
            text = {
                Text(
                    if (state.preview?.requiresStructuralRebuild == true) {
                        "Линии, распределение по фазам и аппараты будут рассчитаны заново. Предыдущую конфигурацию можно будет восстановить одним действием."
                    } else {
                        "Изменится доступная мощность проекта. Структура линий останется прежней."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApplyConfirmation = false
                        viewModel.applyChanges()
                    }
                ) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirmation = false }) { Text("Отмена") }
            }
        )
    }

    if (showUndoConfirmation) {
        AlertDialog(
            onDismissRequest = { showUndoConfirmation = false },
            title = { Text("Вернуть предыдущую конфигурацию?") },
            text = { Text("Текущие линии и ручные настройки будут заменены сохранённым снимком.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUndoConfirmation = false
                        viewModel.undoLastReconfiguration()
                    }
                ) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { showUndoConfirmation = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun IntroCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Измените сеть без риска", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Сначала ВольтХом покажет результат без записи. После подтверждения создаст резервную копию и применит изменения целиком.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SettingsCard(
    phaseMode: PhaseMode,
    hasInputPower: Boolean,
    inputPowerText: String,
    enabled: Boolean,
    onPhaseMode: (PhaseMode) -> Unit,
    onInputPowerEnabled: (Boolean) -> Unit,
    onInputPower: (String) -> Unit
) {
    Card {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Новые параметры", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Тип сети", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhaseButton(
                    text = "1 фаза",
                    selected = phaseMode == PhaseMode.SINGLE,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onPhaseMode(PhaseMode.SINGLE) }
                )
                PhaseButton(
                    text = "3 фазы",
                    selected = phaseMode == PhaseMode.THREE,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onPhaseMode(PhaseMode.THREE) }
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Доступная мощность", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Ограничение по договору или техусловиям",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = hasInputPower,
                    onCheckedChange = onInputPowerEnabled,
                    enabled = enabled
                )
            }
            if (hasInputPower) {
                OutlinedTextField(
                    value = inputPowerText,
                    onValueChange = onInputPower,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Мощность") },
                    suffix = { Text("кВт") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }
    }
}

@Composable
private fun PhaseButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
    }
}

@Composable
private fun ImpactCard(impact: ReconfigurationImpact) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Предварительный результат", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryColumn(
                    title = "Сейчас",
                    values = summaryValues(
                        impact.currentPhaseMode,
                        impact.currentInputPowerKw,
                        impact.currentGroupsCount,
                        impact.currentMaxPhaseCurrentA
                    ),
                    modifier = Modifier.weight(1f)
                )
                SummaryColumn(
                    title = "После",
                    values = summaryValues(
                        impact.proposedPhaseMode,
                        impact.proposedInputPowerKw,
                        impact.proposedGroupsCount,
                        impact.proposedMaxPhaseCurrentA
                    ),
                    modifier = Modifier.weight(1f),
                    highlighted = true
                )
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = buildString {
                        append("Расчётная нагрузка: ${formatNumber(impact.calculatedPowerKw)} кВт")
                        impact.proposedInputPowerKw?.let {
                            val percent = if (it > 0.0) impact.calculatedPowerKw / it * 100.0 else 0.0
                            append(" · ${formatNumber(percent)}% доступной мощности")
                        }
                    },
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                if (impact.requiresStructuralRebuild) {
                    "Будут автоматически перестроены линии и распределение по фазам."
                } else {
                    "Линии не перестраиваются: меняется только ограничение проекта."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!impact.hasChanges) {
                Text("Изменений нет", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SummaryColumn(
    title: String,
    values: List<String>,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        values.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun summaryValues(
    phaseMode: PhaseMode,
    inputPowerKw: Double?,
    groups: Int,
    maxCurrentA: Double
): List<String> = listOf(
    if (phaseMode == PhaseMode.SINGLE) "1 фаза" else "3 фазы",
    inputPowerKw?.let { "${formatNumber(it)} кВт доступно" } ?: "Мощность не задана",
    "$groups групп",
    "Макс. фаза ${formatNumber(maxCurrentA)} А"
)

@Composable
private fun MessageCard(message: String, isWarning: Boolean) {
    val color = if (isWarning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(if (isWarning) Icons.Rounded.WarningAmber else Icons.Rounded.Info, null)
            Text(message, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun UndoCard(backupLabel: String?, enabled: Boolean, onUndo: () -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Предыдущая конфигурация сохранена", fontWeight = FontWeight.SemiBold)
                backupLabel?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onUndo, enabled = enabled) { Text("Вернуть") }
        }
    }
}

@Composable
private fun SmallProgress() {
    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
}

private fun formatNumber(value: Double): String = "%.1f".format(Locale.getDefault(), value)
