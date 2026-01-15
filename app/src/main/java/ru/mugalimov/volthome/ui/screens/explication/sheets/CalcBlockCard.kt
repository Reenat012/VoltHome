package ru.mugalimov.volthome.ui.screens.explication.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CalcBlockCard(
    block: CalcBlockUi,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val outline = cs.outlineVariant.copy(alpha = 0.60f)

    Surface(
        modifier = modifier,
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
                text = "Результат шага",
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
}