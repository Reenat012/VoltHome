package ru.mugalimov.volthome.ui.screens.explication.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InfoSheetContent(
    payload: InfoSheetPayload,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Заголовок
        Text(
            text = payload.title,
            style = MaterialTheme.typography.titleLarge
        )

        // Расчётное значение
        payload.currentValueText
            ?.takeIf { it.isNotBlank() }
            ?.let { current ->
                Text(
                    text = "Расчётное значение: $current",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }

        // Буллеты
        if (payload.bullets.isNotEmpty()) {
            Text(
                text = payload.bullets.joinToString(separator = "\n") { "• $it" },
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }

        // Пояснение
        payload.interpretation
            ?.takeIf { it.isNotBlank() }
            ?.let { interp ->
                Text(
                    text = interp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }

        // -------- Детали расчёта (СТРОГО по статусу + типу sheet’а) --------

        // Нормализация на UI-границе:
        // - справка всегда HIDDEN (даже если по ошибке прилетел NONE/LOCKED)
        val effectiveState: CalcDetailsState = when (payload.sheetType) {
            InfoSheetType.REFERENCE -> CalcDetailsState.HIDDEN
            InfoSheetType.CALCULATION -> payload.calcDetailsState
        }

        when (effectiveState) {

            CalcDetailsState.HIDDEN -> {
                // Справочный sheet: секция "Расчёт" не рендерится вообще.
            }

            CalcDetailsState.AVAILABLE -> {
                if (payload.calcBlocks.isNotEmpty()) {
                    HorizontalDivider(
                        color = cs.outlineVariant.copy(alpha = 0.45f)
                    )

                    Text(
                        text = "Расчёт",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        payload.calcBlocks.forEach { block ->
                            CalcBlockCard(block = block)
                        }
                    }
                }
            }

            CalcDetailsState.LOCKED -> {
                HorizontalDivider(
                    color = cs.outlineVariant.copy(alpha = 0.45f)
                )

                Text(
                    text = "Расчёт",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Детали расчёта доступны в PRO.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            CalcDetailsState.NONE -> {
                // КРИТИЧЕСКОЕ ПРАВИЛО:
                // Сообщение об отсутствии шагов допустимо только для CALCULATION + NONE.
                if (payload.sheetType == InfoSheetType.CALCULATION) {
                    HorizontalDivider(
                        color = cs.outlineVariant.copy(alpha = 0.45f)
                    )

                    Text(
                        text = "Расчёт",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Шаги расчёта отсутствуют.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }

        // Нормативы
        if (payload.normRefs.isNotEmpty()) {
            Text(
                text = "Норматив: ${payload.normRefs.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}