package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhSpacing
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor

/**
 * Связанные представления проекта. Блок находится в потоке экрана и не
 * перекрывает карточки, навигацию и действия ручного режима.
 */
@Composable
fun ShieldProjectActions(
    enabled: Boolean,
    onSingleLineClick: () -> Unit,
    onPdfClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(VhSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(VhSpacing.xs)
        ) {
            Text(
                text = "Документы проекта",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = cs.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VhSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProjectSecondaryAction(
                    text = "Однолинейная схема",
                    icon = Icons.Rounded.AccountTree,
                    onClick = onSingleLineClick,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .onboardingAnchor(
                            targetTag = OnboardingTargetTag.EXPLICATION_SINGLE_LINE_BUTTON,
                            screenId = OnboardingScreen.EXPLICATION
                        )
                )
                ProjectSecondaryAction(
                    text = "PDF-отчёт",
                    icon = Icons.Rounded.FileDownload,
                    onClick = onPdfClick,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .onboardingAnchor(
                            targetTag = OnboardingTargetTag.EXPLICATION_PDF_FAB,
                            screenId = OnboardingScreen.EXPLICATION
                        )
                )
            }
        }
    }
}

@Composable
private fun ProjectSecondaryAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 60.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = VhSpacing.sm,
            vertical = VhSpacing.xs
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = VhSpacing.xs),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
