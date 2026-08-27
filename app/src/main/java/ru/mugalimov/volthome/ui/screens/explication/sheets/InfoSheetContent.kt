package ru.mugalimov.volthome.ui.screens.explication.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InfoSheetContent(
    payload: InfoSheetPayload,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    // Нормализация на UI-границе:
    // - справка всегда HIDDEN (даже если по ошибке прилетел NONE/LOCKED)
    val effectiveState: CalcDetailsState = when (payload.sheetType) {
        InfoSheetType.REFERENCE -> CalcDetailsState.HIDDEN
        InfoSheetType.CALCULATION -> payload.calcDetailsState
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Заголовок
        item {
            Text(
                text = payload.title,
                style = MaterialTheme.typography.titleLarge
            )
        }

        payload.calculationStory?.let { story ->
            item {
                CalculationResultHero(story = story)
            }
        }

        // Решение
        item {
            payload.decisionSummary?.let { summary ->
                DecisionSummaryCard(summary)
            }
        }

        // Расчётное значение / решение
        if (payload.calculationStory == null) {
            item {
                payload.currentValueText
                    ?.takeIf { it.isNotBlank() }
                    ?.let { current ->
                        Text(
                            text = "${payload.currentValueLabel}: $current",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
            }
        }

        if (payload.ruleChecks.isNotEmpty()) {
            item {
                Text(
                    text = "Что проверил алгоритм",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(payload.ruleChecks) { check ->
                RuleCheckRow(check)
            }
        }

        // Буллеты
        if (payload.bullets.isNotEmpty()) {
            item {
                Text(
                    text = payload.bullets.joinToString(separator = "\n") { "• $it" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }

        // Пояснение
        item {
            payload.interpretation
                ?.takeIf { it.isNotBlank() }
                ?.let { interp ->
                    Text(
                        text = interp,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
        }

        if (payload.formulaLines.isNotEmpty()) {
            item {
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
            }
            item {
                Text(
                    text = payload.formulaTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(payload.formulaLines) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }

        payload.detailSections.forEach { section ->
            item {
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
            }
            item {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                Text(
                    text = section.lines.joinToString(separator = "\n") { "• $it" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }

        // -------- Детали расчёта (СТРОГО по статусу + типу sheet’а) --------
        when (effectiveState) {

            CalcDetailsState.HIDDEN -> {
                // Справочный sheet: секция "Расчёт" не рендерится вообще.
            }

            CalcDetailsState.AVAILABLE -> {
                val story = payload.calculationStory
                if (story != null) {
                    item {
                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
                    }
                    item {
                        Text(
                            text = "Как получен результат",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(story.contributions) { contribution ->
                        CalculationContributionCard(contribution = contribution)
                    }
                    item {
                        CalculationInterpretationCard(text = story.interpretation)
                    }
                    item {
                        CalculationFormulaDisclosure(story = story)
                    }
                } else if (payload.calcBlocks.isNotEmpty()) {

                    item {
                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
                    }

                    item {
                        Text(
                            text = "Расчёт",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(payload.calcBlocks) { block ->
                        CalcBlockCard(block = block)
                    }
                }
            }

            CalcDetailsState.LOCKED -> {
                item { HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f)) }

                item {
                    Text(
                        text = "Расчёт",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                item {
                    Text(
                        text = "Детали расчёта доступны в PRO.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            CalcDetailsState.NONE -> {
                if (payload.sheetType == InfoSheetType.CALCULATION) {

                    item { HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f)) }

                    item {
                        Text(
                            text = "Расчёт",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    item {
                        Text(
                            text = "Шаги расчёта отсутствуют.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Нормативы
        if (payload.normRefs.isNotEmpty()) {
            item {
                Text(
                    text = "Норматив: ${payload.normRefs.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }

        if (payload.limitations.isNotEmpty()) {
            item {
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
            }
            item {
                Text(
                    text = "Границы расчёта",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                Text(
                    text = payload.limitations.joinToString(separator = "\n") { "• $it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }

        payload.sourceText?.takeIf(String::isNotBlank)?.let { source ->
            item {
                Text(
                    text = source,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DecisionSummaryCard(summary: DecisionSummaryUi) {
    val cs = MaterialTheme.colorScheme
    val accent = when (summary.tone) {
        DecisionToneUi.POSITIVE -> cs.primary
        DecisionToneUi.NEUTRAL -> cs.onSurfaceVariant
        DecisionToneUi.WARNING -> cs.tertiary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            Text(
                text = summary.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface
            )
        }
    }
}

@Composable
private fun RuleCheckRow(check: RuleCheckUi) {
    val cs = MaterialTheme.colorScheme
    val (icon, tint) = when (check.status) {
        RuleCheckStatusUi.MATCHED -> Icons.Outlined.CheckCircle to cs.primary
        RuleCheckStatusUi.NOT_MATCHED -> Icons.Outlined.RemoveCircleOutline to cs.onSurfaceVariant
        RuleCheckStatusUi.ASSUMPTION -> Icons.Outlined.Info to cs.tertiary
        RuleCheckStatusUi.WARNING -> Icons.Outlined.WarningAmber to cs.error
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = check.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface
            )
            Text(
                text = check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}
