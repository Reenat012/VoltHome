package ru.mugalimov.volthome.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessment
import ru.mugalimov.volthome.ui.model.IncomerAssessmentPresentation
import ru.mugalimov.volthome.ui.model.IncomerAssessmentTone
import ru.mugalimov.volthome.ui.model.toPresentation

@Composable
fun IncomerAssessmentStatusCard(
    assessment: IncomerAssessment,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val presentation = assessment.toPresentation() ?: return
    val colors = assessmentColors(presentation)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.container),
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.48f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 10.dp else 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = colors.icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(if (compact) 20.dp else 22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = presentation.title,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.content
                )
                Text(
                    text = presentation.message,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = colors.content.copy(alpha = 0.84f)
                )
            }
        }
    }
}

private data class AssessmentColors(
    val container: Color,
    val content: Color,
    val accent: Color,
    val icon: ImageVector
)

@Composable
private fun assessmentColors(presentation: IncomerAssessmentPresentation): AssessmentColors {
    val scheme = MaterialTheme.colorScheme
    return when (presentation.tone) {
        IncomerAssessmentTone.INFO -> AssessmentColors(
            container = scheme.primaryContainer.copy(alpha = 0.55f),
            content = scheme.onPrimaryContainer,
            accent = scheme.primary,
            icon = Icons.Outlined.Info
        )
        IncomerAssessmentTone.WARNING -> AssessmentColors(
            container = scheme.tertiaryContainer.copy(alpha = 0.64f),
            content = scheme.onTertiaryContainer,
            accent = scheme.tertiary,
            icon = Icons.Outlined.WarningAmber
        )
        IncomerAssessmentTone.CRITICAL -> AssessmentColors(
            container = scheme.errorContainer.copy(alpha = 0.64f),
            content = scheme.onErrorContainer,
            accent = scheme.error,
            icon = Icons.Outlined.ErrorOutline
        )
    }
}
