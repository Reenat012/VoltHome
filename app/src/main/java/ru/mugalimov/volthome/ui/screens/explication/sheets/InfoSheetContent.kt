package ru.mugalimov.volthome.ui.screens.explication.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
        Text(text = payload.title, style = MaterialTheme.typography.titleLarge)

        payload.currentValueText
            ?.takeIf { it.isNotBlank() }
            ?.let { current ->
                Text(
                    text = "Текущее значение: $current",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }

        if (payload.bullets.isNotEmpty()) {
            Text(
                text = payload.bullets.joinToString(separator = "\n") { "• $it" },
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }

        payload.interpretation
            ?.takeIf { it.isNotBlank() }
            ?.let { interp ->
                Text(
                    text = interp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }

        if (payload.calcBlocks.isNotEmpty()) {
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

            Text(
                text = "Расчёт",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            payload.calcBlocks.forEach { block ->
                CalcBlockCard(block = block)
            }
        }

        if (payload.normRefs.isNotEmpty()) {
            Text(
                text = "Норматив: " + payload.normRefs.joinToString(separator = ", "),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CalcBlockCard(block: CalcBlockUi) {
    val cs = MaterialTheme.colorScheme
    val outline = cs.outlineVariant.copy(alpha = 0.60f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, outline),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Формула",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant
            )
            Text(
                text = block.formulaText,
                style = MaterialTheme.typography.bodyMedium
            )

            if (block.substitutionLines.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Подстановка",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                block.substitutionLines.forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Результат",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant
            )
            Text(
                text = block.resultText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    Spacer(Modifier.height(10.dp))
}