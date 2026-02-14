package ru.mugalimov.volthome.ui.screens.explication.manual

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Ghost-элемент для перетаскивания устройства.
 * Рисуется ОВЕРЛЕЕМ поверх всего контента на уровне корневого Box.
 *
 * ВАЖНО:
 * - координаты positionRoot ожидаются в системе root-контейнера экрана (positionInRoot / localToRoot).
 * - в этом коммите никаких drop/targets тут нет — только визуальный "призрак".
 */
@Composable
fun DragGhostOverlay(
    text: String,
    positionRoot: Offset,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    Surface(
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.60f)),
        modifier = modifier
            // Рисуем в абсолютных координатах root (в пикселях)
            .graphicsLayer {
                translationX = positionRoot.x
                translationY = positionRoot.y
                // Чуть "приподнимаем" ghost над UI
                shadowElevation = 10f
                // Небольшая прозрачность, чтобы было ясно что это "перетаскиваемое"
                alpha = 0.95f
            }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}