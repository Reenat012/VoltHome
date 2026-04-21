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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.ui.onboarding.model.ActiveHint
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag

/**
 * Overlay для onboarding-подсказок.
 *
 * Ключевые исправления:
 * - boundsInRoot() приходит в PX, поэтому переводим координаты через LocalDensity;
 * - позиционируем карточку относительно ЦЕНТРА anchor, а не его левого края;
 * - если снизу не хватает места — поднимаем карточку НАД anchor;
 * - ограничиваем карточку safe-отступами, чтобы она не врезалась в bottom/navigation area.
 */
@Composable
fun CoachMarkOverlay(
    activeHint: ActiveHint,
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

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

        val horizontalSafe = 16.dp
        val topSafe = 16.dp
        val bottomSafe = 24.dp

        // Ширину делаем адаптивной, а не жёстко 300 dp.
        val cardWidth = remember(maxWidth) {
            (maxWidth - horizontalSafe * 2)
                .coerceAtMost(320.dp)
                .coerceAtLeast(260.dp)
        }

        // Оценочная высота карточки.
        // Нам не нужна математическая идеальность, нужна стабильная безопасная раскладка.
        val estimatedCardHeight = when (activeHint.targetTag) {
            OnboardingTargetTag.LOADS_DONUT_CHART -> 132.dp
            else -> 148.dp
        }

        val anchoredOffset = anchorBounds?.let { boundsPx ->
            with(density) {
                val left = boundsPx.left.toDp()
                val right = boundsPx.right.toDp()
                val top = boundsPx.top.toDp()
                val bottom = boundsPx.bottom.toDp()

                val anchorCenterX = (left + right) / 2f
                val preferredX = anchorCenterX - (cardWidth / 2)

                // По умолчанию пытаемся показать ПОД anchor.
                val belowY = bottom + 12.dp

                // Если снизу места не хватает — показываем НАД anchor.
                val aboveY = top - estimatedCardHeight - 12.dp

                val fitsBelow = belowY + estimatedCardHeight <= maxHeight - bottomSafe

                val finalX = preferredX
                    .coerceAtLeast(horizontalSafe)
                    .coerceAtMost((maxWidth - cardWidth - horizontalSafe).coerceAtLeast(horizontalSafe))

                val finalY = if (fitsBelow) {
                    belowY
                } else {
                    aboveY
                        .coerceAtLeast(topSafe)
                        .coerceAtMost((maxHeight - estimatedCardHeight - bottomSafe).coerceAtLeast(topSafe))
                }

                AnchoredOffset(
                    x = finalX,
                    y = finalY
                )
            }
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