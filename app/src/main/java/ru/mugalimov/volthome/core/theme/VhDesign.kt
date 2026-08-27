package ru.mugalimov.volthome.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Геометрическая система приложения. Произвольные радиусы в экранах не добавляем. */
val VhShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object VhSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object VhDimens {
    val screenHorizontal = 20.dp
    val screenVertical = 16.dp
    val controlMinHeight = 48.dp
    val iconButton = 48.dp
    val contentMaxWidth = 720.dp
}
