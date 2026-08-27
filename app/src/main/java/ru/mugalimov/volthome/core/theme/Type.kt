package ru.mugalimov.volthome.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
private val VhFontFamily = FontFamily.Default

/**
 * Полная типографическая шкала VoltHome.
 *
 * У интерфейса намеренно спокойная и плотная подача: крупные стили используются
 * для навигации, а инженерные значения читаются через вес, не через гигантский размер.
 */
val Typography = Typography(
    displayLarge = vhText(FontWeight.SemiBold, 44, 52, -0.5f),
    displayMedium = vhText(FontWeight.SemiBold, 38, 46, -0.35f),
    displaySmall = vhText(FontWeight.SemiBold, 32, 40, -0.25f),

    headlineLarge = vhText(FontWeight.SemiBold, 28, 36, -0.2f),
    headlineMedium = vhText(FontWeight.SemiBold, 25, 32, -0.1f),
    headlineSmall = vhText(FontWeight.SemiBold, 22, 28, 0f),

    titleLarge = vhText(FontWeight.SemiBold, 20, 26, 0f),
    titleMedium = vhText(FontWeight.SemiBold, 17, 23, 0f),
    titleSmall = vhText(FontWeight.SemiBold, 15, 20, 0.05f),

    bodyLarge = vhText(FontWeight.Normal, 16, 24, 0f),
    bodyMedium = vhText(FontWeight.Normal, 14, 21, 0.05f),
    bodySmall = vhText(FontWeight.Normal, 12, 18, 0.1f),

    labelLarge = vhText(FontWeight.Medium, 14, 20, 0.05f),
    labelMedium = vhText(FontWeight.Medium, 12, 17, 0.1f),
    labelSmall = vhText(FontWeight.Medium, 11, 16, 0.15f)
)

private fun vhText(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float
) = TextStyle(
    fontFamily = VhFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)
