import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.R

@Composable
fun GlassExtendedFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Экспорт PDF",
    iconPainter: Painter = painterResource(R.drawable.ic_pdf_bolt), // твоя иконка
) {
    val shape = RoundedCornerShape(28.dp)

    // фон и контент из темы
    val baseBg = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface

    // лёгкий «блик» — полупрозрачный градиент
    val sheen = remember {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.12f),
                Color.Transparent,
                Color.White.copy(alpha = 0.08f)
            )
        )
    }

    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = Color.Unspecified // сохраняем цвета вектора
            )
        },
        text = { Text(text) },
        modifier = modifier
            .clip(shape)
            // полупрозрачный «стеклянный» фон
            .background(baseBg.copy(alpha = 0.55f), shape)
            // тонкий светлый контур
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            // лёгкий блик поверх
            .drawWithContent {
                drawContent()
                drawRect(sheen, alpha = 0.35f)
            },
        containerColor = Color.Transparent,            // фон уже задан модификаторами
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 10.dp
        )
    )
}