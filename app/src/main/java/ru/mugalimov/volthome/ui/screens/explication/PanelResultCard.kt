package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.pricing.ProtectionCostEstimate
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor
import ru.mugalimov.volthome.ui.format.UiTextFormat

/** Компактный результат расчёта щита и переход в самостоятельный раздел. */
@Composable
fun PanelResultCard(
    estimate: ProtectionCostEstimate,
    groupsCount: Int,
    panelVisualizationAllowed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .onboardingAnchor(
                targetTag = OnboardingTargetTag.EXPLICATION_PANEL_CARD,
                screenId = OnboardingScreen.EXPLICATION
            )
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = cs.primary.copy(alpha = 0.12f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = null,
                            modifier = Modifier.padding(9.dp).size(22.dp),
                            tint = cs.primary
                        )
                    }
                    Column {
                        Text(
                            text = "Щит сформирован",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$groupsCount ${UiTextFormat.plural(groupsCount, "группа", "группы", "групп")} · " +
                                "${estimate.totalDeviceCount} ${UiTextFormat.plural(estimate.totalDeviceCount, "аппарат", "аппарата", "аппаратов")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (panelVisualizationAllowed) {
                        Icons.AutoMirrored.Rounded.OpenInNew
                    } else {
                        Icons.Rounded.Lock
                    },
                    contentDescription = null,
                    tint = cs.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Оценка аппаратов",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        text = "≈ ${formatPanelEstimate(estimate.total.typicalKopecks)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary
                    )
                }
                Text(
                    text = if (panelVisualizationAllowed) "Открыть щит" else "Превью · PRO",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.primary
                )
            }
        }
    }
}

private fun formatPanelEstimate(kopecks: Long): String =
    UiTextFormat.rubles(kopecks)
