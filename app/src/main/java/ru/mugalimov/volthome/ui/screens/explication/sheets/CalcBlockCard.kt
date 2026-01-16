package ru.mugalimov.volthome.ui.screens.explication.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CalcBlockCard(
    block: CalcBlockUi,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val outline = cs.outlineVariant.copy(alpha = 0.60f)

    val labelStyle = MaterialTheme.typography.labelSmall
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val monoStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, outline),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(12.dp)) {

            // --- Формула ---
            Text(
                text = "Формула",
                style = labelStyle,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = block.formulaText,
                style = monoStyle,
                color = cs.onSurface
            )

            // --- Подстановка ---
            if (block.substitutionLines.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Подстановка",
                    style = labelStyle,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                block.substitutionLines.forEach { line ->
                    Text(
                        text = "• $line",
                        style = bodyStyle,
                        color = cs.onSurface
                    )
                }
            }

            // --- Результат ---
            Spacer(Modifier.height(10.dp))

            Text(
                text = "Результат",
                style = labelStyle,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            Text(
                text = block.resultText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        }
    }
}