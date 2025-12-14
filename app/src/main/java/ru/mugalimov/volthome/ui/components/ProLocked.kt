package ru.mugalimov.volthome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.ProFeature

/**
 * Обёртка для PRO-фич.
 *
 * - isPro = true  -> просто показывает content
 * - isPro = false -> показывает content + overlay с замком
 *
 * По клику на overlay вызывает onLockedClick(feature)
 */
@Composable
fun ProLocked(
    isPro: Boolean,
    feature: ProFeature,
    onLockedClick: (ProFeature) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()

        if (!isPro) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onLockedClick(feature) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Доступно в PRO",
                        tint = Color.White,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}