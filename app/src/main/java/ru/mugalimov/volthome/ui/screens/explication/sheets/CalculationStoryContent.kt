package ru.mugalimov.volthome.ui.screens.explication.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhSpacing

@Composable
fun CalculationResultHero(
    story: CalculationStoryUi,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.primaryContainer.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier.padding(VhSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VhSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    icon = Icons.Outlined.AutoAwesome,
                    text = story.sourceText,
                    accent = cs.primary
                )
                StatusChip(
                    icon = Icons.Outlined.CheckCircle,
                    text = story.statusText,
                    accent = cs.tertiary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = story.resultLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant
                )
                Text(
                    text = story.resultText,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
            }

            if (!story.comparisonLabel.isNullOrBlank() && !story.comparisonText.isNullOrBlank()) {
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = story.comparisonLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        text = story.comparisonText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface
                    )
                }
            }

            story.impactText?.takeIf(String::isNotBlank)?.let { impact ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VhSpacing.xs),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.TrendingDown,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = impact,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CalculationContributionCard(
    contribution: CalculationContributionUi,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(VhSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VhSpacing.sm)
        ) {
            Text(
                text = contribution.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VhSpacing.xs)
            ) {
                CalculationValueCell(
                    label = contribution.inputLabel,
                    value = contribution.inputText,
                    modifier = Modifier.weight(1f)
                )

                contribution.factorText?.let { factor ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = cs.primaryContainer.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = factor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.primary
                        )
                    }
                }

                Text(
                    text = "=",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant
                )

                CalculationValueCell(
                    label = contribution.resultLabel,
                    value = contribution.resultText,
                    modifier = Modifier.weight(1f),
                    emphasized = true
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SourceLine("Исходное значение", contribution.inputSource)
                if (!contribution.factorLabel.isNullOrBlank() &&
                    !contribution.factorSource.isNullOrBlank()
                ) {
                    SourceLine(contribution.factorLabel, contribution.factorSource)
                }
            }
        }
    }
}

@Composable
fun CalculationInterpretationCard(
    text: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(VhSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VhSpacing.xs)
        ) {
            Text(
                text = "Что означает результат",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CalculationFormulaDisclosure(
    story: CalculationStoryUi,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(VhSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Functions,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(VhSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Формула расчёта",
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface
                    )
                    Text(
                        text = if (expanded) "Скрыть профессиональные детали" else "Показать профессиональные детали",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Скрыть формулу" else "Показать формулу",
                    tint = cs.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = VhSpacing.md, end = VhSpacing.md, bottom = VhSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(VhSpacing.xs)
                ) {
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
                    Text(
                        text = story.formulaText,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = cs.onSurface
                    )
                    story.formulaDescription?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculationValueCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            color = if (emphasized) cs.primary else cs.onSurface
        )
    }
}

@Composable
private fun SourceLine(label: String, source: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
        Text(
            text = source,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            color = cs.onSurface
        )
    }
}

@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    accent: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = accent.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
    }
}
