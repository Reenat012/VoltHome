package ru.mugalimov.volthome.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast
import ru.mugalimov.volthome.ui.onboarding.model.ActiveHint

/**
 * Overlay для onboarding подсказок.
 *
 * Commit 4:
 * - показывает реальный title/body
 * - anchored/fallback поведение остаётся прежним
 */
@Composable
fun CoachMarkOverlay(
    activeHint: ActiveHint,
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        // Полупрозрачный scrim поверх приложения.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onDismiss)
        )

        val cardWidth = 300.dp

        // Если anchor найден и валиден — рисуем карточку рядом с target.
        // Если anchor нет — уходим в центр экрана.
        val anchoredOffset = anchorBounds?.let { bounds ->
            val x = bounds.left.dp
                .coerceAtLeast(12.dp)
                .coerceAtMost((maxWidth - cardWidth - 12.dp).coerceAtLeast(12.dp))

            val y = (bounds.bottom.dp + 12.dp)
                .coerceAtLeast(12.dp)
                .coerceAtMost((maxHeight - 140.dp).coerceAtLeast(12.dp))

            AnchoredOffset(x = x, y = y)
        }

        if (anchoredOffset != null) {
            CoachMarkCard(
                activeHint = activeHint,
                onDismiss = onDismiss,
                modifier = Modifier
                    .offset(x = anchoredOffset.x, y = anchoredOffset.y)
                    .width(cardWidth)
            )
        } else {
            CoachMarkCard(
                activeHint = activeHint,
                onDismiss = onDismiss,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(cardWidth)
            )
        }
    }
}

@Composable
private fun CoachMarkCard(
    activeHint: ActiveHint,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = activeHint.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = activeHint.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text("Понятно")
            }
        }
    }
}

/**
 * Внутренний offset для anchored-сценария.
 */
private data class AnchoredOffset(
    val x: Dp,
    val y: Dp
)