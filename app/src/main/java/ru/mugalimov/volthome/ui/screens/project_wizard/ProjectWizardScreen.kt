package ru.mugalimov.volthome.ui.screens.project_wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectObjectType
import ru.mugalimov.volthome.domain.model.projectwizard.ProjectTemplate
import ru.mugalimov.volthome.domain.config.ProjectsLimitConfig
import ru.mugalimov.volthome.ui.format.UiTextFormat
import ru.mugalimov.volthome.ui.model.LocalUserPlan

@Composable
fun ProjectWizardScreen(
    onClose: () -> Unit,
    onOpenRooms: () -> Unit,
    onOpenLines: () -> Unit,
    onOpenPanel: () -> Unit,
    viewModel: ProjectWizardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val projectLimit = LocalUserPlan.current.capabilities.projectsLimit()
    val projectLimitReached = projectLimit != ProjectsLimitConfig.UNLIMITED &&
        state.existingProjectsCount >= projectLimit
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    fun requestClose() {
        if (state.result == null && state.step > 0) showDiscardDialog = true else onClose()
    }
    BackHandler {
        if (!viewModel.back()) requestClose()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Закрыть мастер?") },
            text = { Text("Настройки текущего проекта будут сброшены.") },
            confirmButton = {
                TextButton(onClick = onClose) { Text("Закрыть") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Продолжить") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WizardHeader(
                step = state.step,
                resultShown = state.result != null,
                onBack = { if (!viewModel.back()) requestClose() },
                onClose = ::requestClose
            )
        },
        bottomBar = {
            if (!state.isLoading && state.result == null) {
                WizardBottomBar(
                    step = state.step,
                    canContinue = state.canContinue,
                    isCreating = state.isCreating,
                    onBack = { viewModel.back() },
                    onNext = when {
                        state.step == 0 && projectLimitReached -> viewModel::requestProjectsUpgrade
                        state.step == 3 -> viewModel::createProject
                        else -> viewModel::next
                    }
                )
            }
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.result != null -> WizardSuccess(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = state,
                onOpenRooms = onOpenRooms,
                onOpenLines = onOpenLines,
                onOpenPanel = onOpenPanel
            )

            else -> AnimatedContent(
                targetState = state.step,
                label = "project-wizard-step",
                modifier = Modifier.fillMaxSize().padding(padding)
            ) { step ->
                when (step) {
                    0 -> ObjectTypeStep(state, viewModel::selectTemplate)
                    1 -> NetworkStep(
                        state = state,
                        onNameChanged = viewModel::setProjectName,
                        onPhaseChanged = viewModel::setPhaseMode,
                        onInputKnownChanged = viewModel::setInputPowerKnown,
                        onInputPowerChanged = viewModel::setInputPower
                    )
                    2 -> RoomsStep(state, viewModel::changeRoomCount)
                    else -> LoadsStep(state, viewModel::changeDeviceCount)
                }
            }
        }
    }
}

@Composable
private fun WizardHeader(step: Int, resultShown: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (resultShown) "Проект готов" else "Новый проект",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (!resultShown) {
                    Text(
                        text = "Шаг ${step + 1} из 4",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть") }
        }
        if (!resultShown) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(4) { index ->
                    Surface(
                        modifier = Modifier.weight(1f).height(4.dp),
                        shape = CircleShape,
                        color = if (index <= step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun ObjectTypeStep(state: ProjectWizardUiState, onSelect: (String) -> Unit) {
    val projectLimit = LocalUserPlan.current.capabilities.projectsLimit()
    WizardList(
        title = "Что вы рассчитываете?",
        subtitle = "Шаблон подготовит стартовый состав. Количество можно изменить сейчас, параметры — после создания."
    ) {
        if (projectLimit != ProjectsLimitConfig.UNLIMITED) {
            item {
                val limitReached = state.existingProjectsCount >= projectLimit
                if (limitReached) {
                    ErrorCard(
                        "Использовано проектов Free: ${state.existingProjectsCount} из $projectLimit. " +
                            "При продолжении откроется предложение PRO."
                    )
                } else {
                    InfoCard("Проекты Free: ${state.existingProjectsCount} из $projectLimit")
                }
            }
        }
        items(state.templates, key = ProjectTemplate::id) { template ->
            val selected = state.selectedTemplate?.id == template.id
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(template.id) },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .32f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Icon(
                            imageVector = template.objectType.icon(),
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp).size(26.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(template.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun NetworkStep(
    state: ProjectWizardUiState,
    onNameChanged: (String) -> Unit,
    onPhaseChanged: (PhaseMode) -> Unit,
    onInputKnownChanged: (Boolean) -> Unit,
    onInputPowerChanged: (String) -> Unit
) {
    WizardList(
        title = "Параметры проекта",
        subtitle = "Укажите сеть сейчас — алгоритм сразу построит корректную фазную структуру."
    ) {
        item {
            OutlinedTextField(
                value = state.projectName,
                onValueChange = onNameChanged,
                label = { Text("Название проекта") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
        }
        item { SectionLabel("Сеть") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PhaseCard(
                    title = "1 фаза",
                    subtitle = "230 В",
                    selected = state.phaseMode == PhaseMode.SINGLE,
                    modifier = Modifier.weight(1f),
                    onClick = { onPhaseChanged(PhaseMode.SINGLE) }
                )
                PhaseCard(
                    title = "3 фазы",
                    subtitle = "400/230 В",
                    selected = state.phaseMode == PhaseMode.THREE,
                    modifier = Modifier.weight(1f),
                    onClick = { onPhaseChanged(PhaseMode.THREE) }
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Доступная мощность известна", fontWeight = FontWeight.Medium)
                            Text("Необязательно — можно уточнить позже", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.inputPowerKnown, onCheckedChange = onInputKnownChanged)
                    }
                    if (state.inputPowerKnown) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.inputPowerText,
                            onValueChange = onInputPowerChanged,
                            label = { Text("Доступная мощность, кВт") },
                            isError = state.inputPowerError != null,
                            supportingText = {
                                state.inputPowerError?.let { Text(it) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Используем как справочное ограничение и предупредим, если оценочная нагрузка его превышает.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        state.inputPowerWarning?.let { warning ->
                            Text(
                                warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomsStep(state: ProjectWizardUiState, onCountChanged: (String, Int) -> Unit) {
    WizardList(
        title = if (state.isBlankTemplate) "Пустой проект" else "Выберите помещения",
        subtitle = if (state.isBlankTemplate) "Помещения можно добавить после создания проекта."
        else "Настройте количество комнат. Тип зоны уже учитывает требования к защите."
    ) {
        if (state.isBlankTemplate) {
            item { InfoCard("Проект будет создан без помещений и нагрузок. Вы сразу перейдёте к ручному наполнению.") }
        } else {
            items(state.rooms, key = { it.template.key }) { room ->
                CountCard(
                    title = room.template.title,
                    subtitle = room.template.description,
                    count = room.count,
                    optional = room.template.optional,
                    onMinus = { onCountChanged(room.template.key, -1) },
                    onPlus = { onCountChanged(room.template.key, 1) }
                )
            }
            item { InfoCard("Будет создано помещений: ${state.roomsCount}") }
        }
    }
}

@Composable
private fun LoadsStep(state: ProjectWizardUiState, onCountChanged: (String, Long, Int) -> Unit) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    WizardList(
        title = "Проверьте нагрузки",
        subtitle = "Это стартовый набор. После создания параметры каждого устройства можно уточнить."
    ) {
        if (state.isBlankTemplate) {
            item { InfoCard("Нагрузки не добавляются — вы начинаете с чистого проекта.") }
        } else {
            items(state.rooms.filter { it.count > 0 }, key = { it.template.key }) { room ->
                val isExpanded = expanded[room.template.key] == true
                OutlinedCard(shape = RoundedCornerShape(18.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                expanded[room.template.key] = !isExpanded
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (room.count > 1) "${room.template.title} × ${room.count}" else room.template.title,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${room.deviceCounts.values.sum()} устройств на помещение",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                        }
                        if (isExpanded) {
                            HorizontalDivider()
                            room.deviceCounts.forEach { (deviceId, count) ->
                                val device = state.catalog[deviceId] ?: return@forEach
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            UiTextFormat.power(device.power.toDouble()),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    CompactStepper(
                                        count = count,
                                        onMinus = { onCountChanged(room.template.key, deviceId, -1) },
                                        onPlus = { onCountChanged(room.template.key, deviceId, 1) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { InfoCard("Итого: ${state.roomsCount} помещений · ${state.devicesCount} устройств") }
            item {
                InfoCard(
                    "Оценка по выбранным нагрузкам: " +
                        "${formatKw(state.estimatedInstalledPowerW)} кВт установлено · " +
                        "${formatKw(state.estimatedCalculatedPowerW)} кВт расчётно"
                )
            }
        }
        if (!state.isBlankTemplate && state.devicesCount == 0) {
            item { ErrorCard("Добавьте хотя бы одну нагрузку либо выберите «Пустой проект» на первом шаге.") }
        }
        state.inputPowerWarning?.let { warning -> item { ErrorCard(warning) } }
        if (state.incompatibleThreePhaseDevices.isNotEmpty()) {
            item {
                InfoCard(
                    "Для однофазной сети исключите трёхфазные устройства: " +
                        state.incompatibleThreePhaseDevices.joinToString()
                )
            }
        }
        state.error?.let { message -> item { ErrorCard(message) } }
    }
}

@Composable
private fun WizardSuccess(
    modifier: Modifier,
    state: ProjectWizardUiState,
    onOpenRooms: () -> Unit,
    onOpenLines: () -> Unit,
    onOpenPanel: () -> Unit
) {
    val result = state.result ?: return
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(20.dp)) }
        item {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(18.dp).size(34.dp))
            }
        }
        item {
            Text("Проект сформирован", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                if (result.calculationCompleted) "Помещения и нагрузки сохранены, линии успешно рассчитаны."
                else "Пустой проект готов к ручному наполнению.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryLine("Сеть", if (result.phaseMode == PhaseMode.THREE) "3 фазы" else "1 фаза")
                    SummaryLine("Помещения", result.roomsCount.toString())
                    SummaryLine("Устройства", result.devicesCount.toString())
                    SummaryLine("Линии", result.linesCount.toString())
                    if (result.calculationCompleted) {
                        SummaryLine("Установленная мощность", "${formatKw(result.installedPowerW)} кВт")
                        SummaryLine("Расчётная нагрузка", "${formatKw(result.calculatedPowerW)} кВт")
                    }
                    result.inputPowerKw?.let { SummaryLine("Доступная мощность", "${formatNumber(it)} кВт") }
                }
            }
        }
        result.warnings.forEach { warning -> item { ErrorCard(warning) } }
        item {
            Button(onClick = if (result.calculationCompleted) onOpenLines else onOpenRooms, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (result.calculationCompleted) "Посмотреть линии" else "Добавить первое помещение")
            }
        }
        if (result.calculationCompleted) {
            item { OutlinedButton(onClick = onOpenPanel, modifier = Modifier.fillMaxWidth()) { Text("Открыть щит") } }
            item { OutlinedButton(onClick = onOpenRooms, modifier = Modifier.fillMaxWidth()) { Text("Изменить помещения") } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun WizardList(
    title: String,
    subtitle: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        content()
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun WizardBottomBar(
    step: Int,
    canContinue: Boolean,
    isCreating: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Surface(tonalElevation = 5.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (step > 0) OutlinedButton(onClick = onBack, modifier = Modifier.weight(.4f)) { Text("Назад") }
            Button(
                onClick = onNext,
                enabled = canContinue,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                if (isCreating) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (step == 3) "Создать и рассчитать" else "Продолжить")
            }
        }
    }
}

@Composable
private fun PhaseCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.ElectricBolt, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CountCard(title: String, subtitle: String, count: Int, optional: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    OutlinedCard(shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    if (optional) {
                        Spacer(Modifier.size(8.dp))
                        Text("необязательно", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CompactStepper(count, onMinus, onPlus)
        }
    }
}

@Composable
private fun CompactStepper(count: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus, enabled = count > 0) { Icon(Icons.Rounded.Remove, contentDescription = "Уменьшить") }
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, contentDescription = "Увеличить") }
    }
}

@Composable private fun SectionLabel(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)), shape = RoundedCornerShape(16.dp)) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
        Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun ProjectObjectType.icon(): ImageVector = when (this) {
    ProjectObjectType.APARTMENT -> Icons.Rounded.Apartment
    ProjectObjectType.HOUSE -> Icons.Rounded.Home
    ProjectObjectType.GARAGE_WORKSHOP -> Icons.Rounded.Garage
    ProjectObjectType.COMMERCIAL -> Icons.Rounded.Business
    ProjectObjectType.CUSTOM -> Icons.Rounded.Tune
}

private fun formatKw(valueW: Double): String = formatNumber(valueW / 1000.0)

private fun formatNumber(value: Double): String =
    String.format(java.util.Locale("ru", "RU"), "%.1f", value)
