package ru.mugalimov.volthome.ui.screens.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusKind
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryApparatusProduct
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryConnectionPoint
import ru.mugalimov.volthome.domain.model.catalog.AuxiliaryElectricalConnection
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.panel.PanelAssembly
import ru.mugalimov.volthome.domain.model.panel.PanelEnclosureConfig
import ru.mugalimov.volthome.domain.model.panel.PanelMoveDirection
import ru.mugalimov.volthome.ui.format.UiTextFormat

@Composable
internal fun PanelEditorBanner(
    calculatedStructureChanged: Boolean,
    enclosure: PanelEnclosureConfig,
    onAddApparatus: () -> Unit,
    onChangeEnclosure: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Редактор компоновки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Удерживайте аппарат и перетащите его в нужное место. " +
                    "Перестановка меняет только физическую раскладку щита.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (calculatedStructureChanged) {
                Text(
                    text = "Расчётная структура изменилась. Новые аппараты уже добавлены, " +
                        "сохраните обновлённую компоновку.",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddApparatus, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("Аппарат", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onChangeEnclosure, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.GridView, contentDescription = null)
                    Text(
                        "${enclosure.railCount} × ${enclosure.modulesPerRail}",
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

internal data class PanelEnclosurePreset(
    val title: String,
    val railCount: Int,
    val modulesPerRail: Int
) {
    val totalModules: Int
        get() = railCount * modulesPerRail
}

internal val PanelEnclosurePresets = listOf(
    PanelEnclosurePreset("Компактный", railCount = 1, modulesPerRail = 12),
    PanelEnclosurePreset("24 модуля", railCount = 2, modulesPerRail = 12),
    PanelEnclosurePreset("36 модулей", railCount = 3, modulesPerRail = 12),
    PanelEnclosurePreset("48 модулей", railCount = 4, modulesPerRail = 12),
    PanelEnclosurePreset("54 модуля", railCount = 3, modulesPerRail = 18),
    PanelEnclosurePreset("72 модуля", railCount = 4, modulesPerRail = 18)
)

internal data class CustomPriceInput(
    val isValid: Boolean,
    val kopecks: Long?
)

internal fun parseCustomPriceInput(value: String): CustomPriceInput {
    val normalized = value.trim().replace(',', '.')
    if (normalized.isBlank()) return CustomPriceInput(isValid = true, kopecks = null)
    if (!normalized.matches(Regex("\\d{1,8}(\\.\\d{1,2})?"))) {
        return CustomPriceInput(isValid = false, kopecks = null)
    }
    val parts = normalized.split('.')
    val rubles = parts[0].toLongOrNull() ?: return CustomPriceInput(false, null)
    val kopecksPart = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2)
    val kopecks = rubles * 100L + (kopecksPart.toLongOrNull() ?: 0L)
    return CustomPriceInput(isValid = true, kopecks = kopecks)
}

internal fun parsePanelEnclosureInput(
    railText: String,
    modulesText: String
): PanelEnclosureConfig? {
    val railCount = railText.toIntOrNull() ?: return null
    val modulesPerRail = modulesText.toIntOrNull() ?: return null
    return runCatching {
        PanelEnclosureConfig(
            modulesPerRail = modulesPerRail,
            railCount = railCount
        )
    }.getOrNull()
}

@Composable
internal fun PanelEnclosureSheet(
    current: PanelEnclosureConfig,
    occupiedModuleUnits: Int,
    onApply: (modulesPerRail: Int, railCount: Int) -> Boolean,
    modifier: Modifier = Modifier
) {
    var railText by remember(current) { mutableStateOf(current.railCount.toString()) }
    var modulesText by remember(current) { mutableStateOf(current.modulesPerRail.toString()) }
    val requested = parsePanelEnclosureInput(railText, modulesText)
    val total = requested?.totalModuleUnits
    val isCurrentSize = requested == current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Размер щита",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Выберите типовой корпус или задайте собственное число модулей " +
                        "в ряду. Аппараты будут перепакованы автоматически.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Текущий корпус", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${current.railCount} × ${current.modulesPerRail} мод.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "$occupiedModuleUnits/${current.totalModuleUnits}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Text(
                text = "Типовые размеры",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PanelEnclosurePresets.forEach { preset ->
                    OutlinedButton(
                        onClick = {
                            railText = preset.railCount.toString()
                            modulesText = preset.modulesPerRail.toString()
                        },
                        border = BorderStroke(
                            1.dp,
                            if (requested?.railCount == preset.railCount &&
                                requested.modulesPerRail == preset.modulesPerRail
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                preset.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${preset.railCount} ряда × ${preset.modulesPerRail} мод.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Свой размер",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = railText,
                    onValueChange = { railText = it.filter(Char::isDigit).take(2) },
                    modifier = Modifier.weight(1f),
                    label = { Text("DIN-реек") },
                    supportingText = { Text("1–12") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = modulesText,
                    onValueChange = { modulesText = it.filter(Char::isDigit).take(2) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Модулей в ряду") },
                    supportingText = { Text("4–72") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }

        item {
            val remaining = total?.minus(occupiedModuleUnits)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    requested == null -> MaterialTheme.colorScheme.errorContainer
                    remaining != null && remaining < 0 -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                }
            ) {
                Text(
                    text = when {
                        requested == null -> "Проверьте допустимый диапазон размеров."
                        remaining != null && remaining < 0 ->
                            "Не хватает как минимум ${-remaining} мод. Точная проверка учитывает " +
                                "ширину каждого аппарата."
                        isCurrentSize ->
                            "Этот размер уже используется. Занято $occupiedModuleUnits из $total мод."
                        else -> "Всего: $total мод. · предварительный резерв: $remaining мод. " +
                            "Размещение по рядам проверим при применении."
                    },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        requested == null || (remaining != null && remaining < 0) ->
                            MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        }

        item {
            Button(
                onClick = {
                    requested?.let { config ->
                        onApply(config.modulesPerRail, config.railCount)
                    }
                },
                enabled = requested != null &&
                    !isCurrentSize &&
                    requested.totalModuleUnits >= occupiedModuleUnits,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Применить размер")
            }
        }
    }
}

@Composable
internal fun PanelEditorBottomBar(
    hasChanges: Boolean,
    calculatedStructureChanged: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Отменить")
            }
            Button(
                onClick = onSave,
                enabled = hasChanges || calculatedStructureChanged,
                modifier = Modifier.weight(1f)
            ) {
                Text("Сохранить")
            }
        }
    }
}

@Composable
internal fun AuxiliaryCatalogSheet(
    products: List<AuxiliaryApparatusProduct>,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Добавить аппарат",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                text = "Дополнительный аппарат попадёт в компоновку и смету, " +
                    "но не изменит автоматический расчёт защиты.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(products, key = AuxiliaryApparatusProduct::productId) { product ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = product.function.title(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${product.moduleUnits} мод.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = product.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = product.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "≈ ${UiTextFormat.rubles(product.price.range.typicalKopecks)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = { onAdd(product.productId) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Добавить")
                    }
                }
            }
        }
    }
}

@Composable
internal fun PanelBlockEditorSheet(
    assembly: PanelAssembly,
    isEditing: Boolean,
    groups: List<CircuitGroup>,
    onMove: (PanelMoveDirection) -> Unit,
    onDelete: () -> Unit,
    onSaveCustomDetails: (
        customModuleId: String,
        manufacturer: String?,
        model: String?,
        priceKopecks: Long?,
        connection: AuxiliaryElectricalConnection
    ) -> Boolean,
    modifier: Modifier = Modifier
) {
    val custom = assembly.modules.singleOrNull()?.auxiliarySnapshot
    var manufacturerText by remember(
        custom?.customModuleId,
        custom?.userManufacturer,
        custom?.manufacturer
    ) {
        mutableStateOf(custom?.displayManufacturer.orEmpty())
    }
    var modelText by remember(custom?.customModuleId, custom?.userModel, custom?.model) {
        mutableStateOf(custom?.displayModel.orEmpty())
    }
    var priceText by remember(custom?.customModuleId, custom?.userPriceKopecks) {
        mutableStateOf(
            custom?.userPriceKopecks?.let { kopecks ->
                (kopecks / 100.0).let { rubles ->
                    if (rubles % 1.0 == 0.0) rubles.toLong().toString() else rubles.toString()
                }
            }.orEmpty()
        )
    }
    var selectedConnectionPoint by remember(custom?.customModuleId) {
        mutableStateOf(custom?.connection?.point ?: AuxiliaryConnectionPoint.UNASSIGNED)
    }
    var selectedGroupId by remember(custom?.customModuleId) {
        mutableStateOf(custom?.connection?.groupId)
    }
    val selectedGroup = groups.firstOrNull { it.groupId == selectedGroupId }
    val requestedConnection = AuxiliaryElectricalConnection(
        point = selectedConnectionPoint,
        phase = if (selectedConnectionPoint == AuxiliaryConnectionPoint.GROUP) {
            selectedGroup?.phase
        } else {
            custom?.connection?.phase
        },
        groupId = if (selectedConnectionPoint == AuxiliaryConnectionPoint.GROUP) {
            selectedGroupId
        } else null
    )
    val priceInput = parseCustomPriceInput(priceText)
    val requestedManufacturer = custom?.let { apparatus ->
        manufacturerText.trim().takeUnless { it == apparatus.manufacturer }
    }
    val requestedModel = custom?.let { apparatus ->
        modelText.trim().takeUnless { it == apparatus.model }
    }
    val hasCustomChanges = custom != null && (
        requestedManufacturer != custom.userManufacturer ||
            requestedModel != custom.userModel ||
            priceInput.kopecks != custom.userPriceKopecks
            || requestedConnection != custom.connection
        )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (assembly.isUserAdded) "Дополнительный аппарат" else "Блок защиты",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = assembly.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isEditing) {
            Text(
                text = "Перемещение",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MoveButton(Icons.AutoMirrored.Rounded.ArrowBack, "Влево") {
                    onMove(PanelMoveDirection.LEFT)
                }
                MoveButton(Icons.AutoMirrored.Rounded.ArrowForward, "Вправо") {
                    onMove(PanelMoveDirection.RIGHT)
                }
                MoveButton(Icons.Rounded.ArrowUpward, "Выше") {
                    onMove(PanelMoveDirection.UP)
                }
                MoveButton(Icons.Rounded.ArrowDownward, "Ниже") {
                    onMove(PanelMoveDirection.DOWN)
                }
            }
        }

        custom?.let { apparatus ->
            HorizontalDivider()
            Text(
                text = apparatus.function.title(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = apparatus.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            apparatus.characteristics.forEach { characteristic ->
                Text(
                    text = "• $characteristic",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Исходная позиция каталога",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = listOf(apparatus.manufacturer, apparatus.series, apparatus.model)
                            .filter(String::isNotBlank)
                            .joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Артикул ${apparatus.article} · ≈ " +
                            UiTextFormat.rubles(apparatus.catalogPrice.typicalKopecks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "Данные для проекта",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Электрическое подключение",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Положение на DIN-рейке не определяет место аппарата в схеме.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    AuxiliaryConnectionPoint.UNASSIGNED to "Не задано",
                    AuxiliaryConnectionPoint.PANEL_INPUT to "До вводного",
                    AuxiliaryConnectionPoint.AFTER_INCOMER to "После вводного",
                    AuxiliaryConnectionPoint.DISTRIBUTION_BUS to "Распределительная шина"
                ).forEach { (point, label) ->
                    FilterChip(
                        selected = selectedConnectionPoint == point,
                        onClick = {
                            selectedConnectionPoint = point
                            selectedGroupId = null
                        },
                        label = { Text(label) }
                    )
                }
            }
            if (groups.isNotEmpty()) {
                Text(
                    text = "Или подключить к линии",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.sortedBy(CircuitGroup::groupNumber).forEach { group ->
                        FilterChip(
                            selected = selectedConnectionPoint == AuxiliaryConnectionPoint.GROUP &&
                                selectedGroupId == group.groupId,
                            onClick = {
                                selectedConnectionPoint = AuxiliaryConnectionPoint.GROUP
                                selectedGroupId = group.groupId
                            },
                            label = { Text("Гр. ${group.groupNumber} · ${group.roomName}") }
                        )
                    }
                }
            }
            if (!requestedConnection.isDefined) {
                Text(
                    text = "Подключение не задано: аппарат сохранится в щите и смете, " +
                        "но в схеме будет отмечен предупреждением.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            OutlinedTextField(
                value = manufacturerText,
                onValueChange = { manufacturerText = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Производитель") },
                singleLine = true
            )
            OutlinedTextField(
                value = modelText,
                onValueChange = { modelText = it.take(100) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Модель") },
                singleLine = true
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { value ->
                    priceText = value.filter { it.isDigit() || it == ',' || it == '.' }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Своя цена, ₽") },
                supportingText = {
                    Text(
                        if (priceInput.isValid) {
                            "Оставьте пустым, чтобы использовать цену каталога"
                        } else {
                            "Введите сумму до 99 999 999 ₽ и не более двух знаков после запятой"
                        }
                    )
                },
                isError = !priceInput.isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        manufacturerText = apparatus.manufacturer
                        modelText = apparatus.model
                        priceText = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Подставить каталог")
                }
                Button(
                    onClick = {
                        onSaveCustomDetails(
                            apparatus.customModuleId,
                            requestedManufacturer,
                            requestedModel,
                            priceInput.kopecks,
                            requestedConnection
                        )
                    },
                    enabled = manufacturerText.isNotBlank() &&
                        modelText.isNotBlank() &&
                        priceInput.isValid &&
                        hasCustomChanges,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Сохранить")
                }
            }
            if (apparatus.hasUserIdentity || apparatus.userPriceKopecks != null) {
                Text(
                    text = "В смете и на щите используются данные, заданные для этого проекта.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isEditing) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Text("Удалить из компоновки", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun MoveButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

internal fun AuxiliaryApparatusKind.title(): String = when (this) {
    AuxiliaryApparatusKind.VOLTAGE_RELAY -> "Реле напряжения"
    AuxiliaryApparatusKind.PHASE_CONTROL_RELAY -> "Реле контроля фаз"
    AuxiliaryApparatusKind.CURRENT_RELAY -> "Реле тока и мощности"
    AuxiliaryApparatusKind.MODULAR_CONTACTOR -> "Модульный контактор"
    AuxiliaryApparatusKind.SURGE_PROTECTION_DEVICE -> "УЗИП"
}
