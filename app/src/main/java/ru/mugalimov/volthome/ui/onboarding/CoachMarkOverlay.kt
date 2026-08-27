package ru.mugalimov.volthome.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
    onConfirmed: () -> Unit,
    onDeferred: () -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BackHandler(onBack = onDeferred)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        // Spotlight оставляет целевой элемент визуально читаемым.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spotlightPadding = with(density) { 6.dp.toPx() }
            val spotlight = anchorBounds?.let {
                Rect(
                    left = (it.left - spotlightPadding).coerceAtLeast(0f),
                    top = (it.top - spotlightPadding).coerceAtLeast(0f),
                    right = (it.right + spotlightPadding).coerceAtMost(size.width),
                    bottom = (it.bottom + spotlightPadding).coerceAtMost(size.height)
                )
            }
            val scrimPath = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                spotlight?.let {
                    addRoundRect(
                        RoundRect(
                            rect = it,
                            radiusX = with(density) { 14.dp.toPx() },
                            radiusY = with(density) { 14.dp.toPx() }
                        )
                    )
                }
            }
            drawPath(scrimPath, Color.Black.copy(alpha = 0.58f))
            spotlight?.let {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.72f),
                    topLeft = it.topLeft,
                    size = it.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        with(density) { 14.dp.toPx() }
                    ),
                    style = Stroke(width = with(density) { 1.5.dp.toPx() })
                )
            }
        }

        // Тап за пределами карточки откладывает подсказку, но не помечает
        // её окончательно изученной.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDeferred)
        )

        val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
        val horizontalSafe = 16.dp
        val topSafe = safeDrawingPadding.calculateTopPadding() + 8.dp
        val bottomSafe = safeDrawingPadding.calculateBottomPadding() + 12.dp

        // Ширину делаем адаптивной, а не жёстко 300 dp.
        val cardWidth = remember(maxWidth) {
            (maxWidth - horizontalSafe * 2)
                .coerceAtMost(320.dp)
                .coerceAtLeast(260.dp)
        }

        var measuredCardSize by remember(activeHint.hintId, activeHint.activatedAtMillis) {
            mutableIntStateOf(0)
        }
        val measuredCardHeight = with(density) {
            measuredCardSize.takeIf { it > 0 }?.toDp()
        }
        val cardHeight = measuredCardHeight ?: when (activeHint.targetTag) {
            OnboardingTargetTag.LOADS_DONUT_CHART -> 164.dp
            else -> 188.dp
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
                val aboveY = top - cardHeight - 12.dp

                val fitsBelow = belowY + cardHeight <= maxHeight - bottomSafe

                val finalX = preferredX
                    .coerceAtLeast(horizontalSafe)
                    .coerceAtMost((maxWidth - cardWidth - horizontalSafe).coerceAtLeast(horizontalSafe))

                val finalY = if (fitsBelow) {
                    belowY
                } else {
                    aboveY
                        .coerceAtLeast(topSafe)
                        .coerceAtMost((maxHeight - cardHeight - bottomSafe).coerceAtLeast(topSafe))
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
                onConfirmed = onConfirmed,
                onDeferred = onDeferred,
                onSkipAll = onSkipAll,
                modifier = Modifier
                    .offset(x = anchoredOffset.x, y = anchoredOffset.y)
                    .width(cardWidth)
                    .onSizeChanged { measuredCardSize = it.height }
            )
        } else {
            CoachMarkCard(
                activeHint = activeHint,
                onConfirmed = onConfirmed,
                onDeferred = onDeferred,
                onSkipAll = onSkipAll,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(cardWidth)
                    .onSizeChanged { measuredCardSize = it.height }
            )
        }
    }
}

@Composable
private fun CoachMarkCard(
    activeHint: ActiveHint,
    onConfirmed: () -> Unit,
    onDeferred: () -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .widthIn(max = 320.dp)
            .testTag("coach_mark_card")
    ) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDeferred) {
                    Text("Позже")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onConfirmed) {
                    Text("Понятно")
                }
            }
            TextButton(
                onClick = onSkipAll,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Отключить подсказки")
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
