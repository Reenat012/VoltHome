package ru.mugalimov.volthome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import ru.mugalimov.volthome.domain.model.ProFeature

/**
 * Обёртка для PRO-действий (actions).
 *
 * ❗ ВАЖНО:
 * Этот компонент предназначен ИСКЛЮЧИТЕЛЬНО для UI-действий:
 * - Button / IconButton
 * - FAB
 * - Toggle / Switch
 *
 * 🚫 ЗАПРЕЩЕНО использовать для:
 * - Card / Column / Row с контентом
 * - Секций отчёта
 * - Любых composable, формирующих данные
 *
 * Причина:
 * ProLocked НЕ является логическим gate — content() всегда строится.
 *
 *  * shape:
 *  * Используется ТОЛЬКО для action-контейнеров (FAB/иконка).
 *  * Не предназначен для карточек или секций.
 */
@Composable
fun ProLocked(
    isAllowed: Boolean,
    feature: ProFeature,
    onLockedClick: (ProFeature) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    showLockIcon: Boolean = true,
    action: @Composable () -> Unit
) {
    // Важно: клипуем контейнер, чтобы overlay НЕ был квадратом
    Box(
        modifier = modifier.clip(shape)
    ) {
        action()

        if (!isAllowed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .clickable { onLockedClick(feature) },
                contentAlignment = Alignment.Center
            ) {
                if (showLockIcon) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Доступно в PRO",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}