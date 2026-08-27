package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.pricing.ProtectionCostEstimate
import ru.mugalimov.volthome.ui.format.UiTextFormat

@Composable
fun ProtectionCostEstimateCard(
    estimate: ProtectionCostEstimate,
    detailsAvailable: Boolean,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, tint = cs.primary)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        text = "Оценка комплектации",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (estimate.auxiliaryLines.isEmpty()) {
                            "Аппараты защиты · ориентир"
                        } else {
                            "Защита и ручные аппараты · ориентир"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "≈ ${formatRubles(estimate.total.typicalKopecks)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Рыночный диапазон: ${formatRubles(estimate.total.minKopecks)}–${formatRubles(estimate.total.maxKopecks)}",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Оценено ${estimate.pricedDeviceCount} из ${estimate.totalDeviceCount} аппаратов",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                TextButton(onClick = onDetailsClick) {
                    Text(if (detailsAvailable) "Смотреть состав" else "Состав · PRO")
                }
            }
        }
    }
}

@Composable
fun ProtectionCostEstimateDetails(
    estimate: ProtectionCostEstimate,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Комплектация защиты", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Среднерыночная оценка по параметрам рассчитанных аппаратов. Это не коммерческое предложение.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant
        )
        estimate.lines.forEach { line ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(line.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("× ${line.quantity}", color = cs.onSurfaceVariant)
                }
                Text(
                    "${formatRubles(line.totalPrice.minKopecks)}–${formatRubles(line.totalPrice.maxKopecks)} · обычно ${formatRubles(line.totalPrice.typicalKopecks)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
        }
        estimate.auxiliaryLines.forEach { line ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${line.designation} · ${line.title}",
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = line.functionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "≈ ${formatRubles(line.price.typicalKopecks)}",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!line.connectionDefined) {
                    Text(
                        text = "Точка подключения не задана",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error
                    )
                }
            }
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
        }
        Text(
            "Итого: ≈ ${formatRubles(estimate.total.typicalKopecks)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Не включены: ${estimate.exclusions.joinToString()}. Цены актуальны на ${UiTextFormat.dateFromIso(estimate.catalogUpdatedAt)}.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
    }
}

private fun formatRubles(kopecks: Long): String =
    UiTextFormat.rubles(kopecks)
