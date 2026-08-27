package ru.mugalimov.volthome.ui.screens.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ru.mugalimov.volthome.domain.model.catalog.SelectedApparatusSnapshot
import ru.mugalimov.volthome.domain.model.panel.PanelModule
import ru.mugalimov.volthome.domain.model.pricing.MoneyRange
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceKind
import ru.mugalimov.volthome.domain.model.pricing.ProtectionDeviceSpec
import ru.mugalimov.volthome.domain.use_case.ApparatusCandidate
import ru.mugalimov.volthome.ui.format.UiTextFormat
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun ApparatusSelectionSheet(
    module: PanelModule,
    currentSelection: SelectedApparatusSnapshot?,
    candidates: List<ApparatusCandidate>,
    catalogPublishedAt: String,
    isSaving: Boolean,
    onSelectProduct: (String) -> Unit,
    onSaveUserPrice: (Long?) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Модель аппарата",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${module.label} · ${requireNotNull(module.priceSpec).displaySpec()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Показываем только электрически совместимые модели. Выбор сохраняется в этом проекте.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        currentSelection?.let { selection ->
            item {
                CurrentApparatusCard(
                    selection = selection,
                    isSaving = isSaving,
                    onSaveUserPrice = onSaveUserPrice,
                    onClearSelection = onClearSelection
                )
            }
        }

        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentSelection == null) "Выберите модель" else "Другие модели",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${candidates.size} ${UiTextFormat.plural(candidates.size, "модель", "модели", "моделей")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (candidates.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Совместимых товарных позиций пока нет",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "В общей смете сохранится усреднённая оценка. Каталог будет расширяться без изменения расчётной схемы.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(candidates, key = { it.product.productId }) { candidate ->
                ApparatusCandidateCard(
                    candidate = candidate,
                    selected = currentSelection?.productId == candidate.product.productId,
                    enabled = !isSaving,
                    onSelect = { onSelectProduct(candidate.product.productId) }
                )
            }
        }

        item {
            Text(
                text = buildString {
                    append("Цены ориентировочные, зависят от поставщика и региона")
                    if (catalogPublishedAt.isNotBlank()) append(" · цены на ${UiTextFormat.dateFromIso(catalogPublishedAt)}")
                    append(". Кабель, корпус, аксессуары и монтаж не включены.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CurrentApparatusCard(
    selection: SelectedApparatusSnapshot,
    isSaving: Boolean,
    onSaveUserPrice: (Long?) -> Unit,
    onClearSelection: () -> Unit
) {
    var priceText by remember(selection.productId, selection.userPriceKopecks) {
        mutableStateOf(
            selection.userPriceKopecks
                ?.let { (it / 100L).toString() }
                .orEmpty()
        )
    }
    val parsedRubles = priceText.toLongOrNull()
    val priceValid = priceText.isBlank() || (parsedRubles != null && parsedRubles >= 0L && parsedRubles <= MAX_PRICE_RUB)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Выбрано для проекта",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = selection.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Артикул ${selection.article}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Модель выбрана. Свою закупочную цену можно указать ниже — она повлияет только на итоговую смету.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (selection.userPriceKopecks != null) {
                    "В смете: ${selection.effectivePrice.typicalKopecks.formatRubles()} · цена пользователя"
                } else {
                    "Ориентир: ${selection.catalogPrice.typicalKopecks.formatRubles()} " +
                        "(${selection.catalogPrice.rangeLabel()})"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { value ->
                    priceText = value.filter(Char::isDigit).take(8)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                label = { Text("Ваша цена, ₽") },
                placeholder = { Text("Например, 1250") },
                supportingText = {
                    Text(
                        if (priceValid) "Необязательно. Оставьте пустым, чтобы использовать цену каталога"
                        else "Введите сумму от 0 до ${MAX_PRICE_RUB.formatPlainRubles()} ₽"
                    )
                },
                isError = !priceValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveUserPrice(parsedRubles?.times(100L)) },
                    enabled = !isSaving && priceValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "Сохраняем…" else "Сохранить цену")
                }
                OutlinedButton(
                    onClick = {
                        priceText = ""
                        onSaveUserPrice(null)
                    },
                    enabled = !isSaving && selection.userPriceKopecks != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Вернуть цену каталога")
                }
            }
            TextButton(
                onClick = onClearSelection,
                enabled = !isSaving,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Убрать модель из проекта")
            }
        }
    }
}

@Composable
private fun ApparatusCandidateCard(
    candidate: ApparatusCandidate,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val product = candidate.product
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = product.manufacturer,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${product.series} ${product.model}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Артикул ${product.article}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "≈ ${product.price.range.typicalKopecks.formatRubles()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
            text = "${product.spec.displaySpec()} · ${product.moduleUnits} " +
                UiTextFormat.plural(product.moduleUnits, "DIN-модуль", "DIN-модуля", "DIN-модулей"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Диапазон ${product.price.range.rangeLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onSelect,
                enabled = enabled && !selected,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (selected) "Выбрано" else "Выбрать")
            }
        }
    }
}

private fun ProtectionDeviceSpec.displaySpec(): String {
    val polesLabel = "${poles}P"
    return when (kind) {
        ProtectionDeviceKind.MCB -> buildString {
            append("Автомат $polesLabel ${breakerCurve.orEmpty()}$ratedCurrentA${UiTextFormat.NBSP}А")
            breakingCapacityA?.let { append(" · ${it / 1000}${UiTextFormat.NBSP}кА") }
        }
        ProtectionDeviceKind.RCD ->
            "УЗО $polesLabel $ratedCurrentA${UiTextFormat.NBSP}А / ${leakageCurrentMa ?: 30}${UiTextFormat.NBSP}мА · тип ${rcdType ?: "—"}" +
                if (selectivity.name == "S") " · селективное" else ""
        ProtectionDeviceKind.RCBO ->
            "Дифавтомат $polesLabel ${breakerCurve.orEmpty()}$ratedCurrentA${UiTextFormat.NBSP}А / ${leakageCurrentMa ?: 30}${UiTextFormat.NBSP}мА"
    }
}

private fun MoneyRange.rangeLabel(): String =
    "${minKopecks.formatRubles()}–${maxKopecks.formatRubles()}"

private fun Long.formatRubles(): String =
    UiTextFormat.rubles(this)

private fun Long.formatPlainRubles(): String =
    NumberFormat.getIntegerInstance(Locale("ru", "RU")).format(this)

private const val MAX_PRICE_RUB = 99_999_999L
