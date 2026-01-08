package ru.mugalimov.volthome.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material3 ColorScheme, собранная из VhColors.tokens.
 *
 * Цель:
 * - все прямые MaterialTheme.colorScheme.* в UI должны отдавать нашу индустриальную базу;
 * - Purple/Pink не участвуют в фактической схеме;
 * - API/архитектуру VhColors не трогаем.
 */
private fun vhColorScheme(
    tokens: VhColorTokens,
    dark: Boolean
): ColorScheme {
    val background = tokens.bg
    val surface = tokens.surface
    val surfaceVariant = tokens.surfaceAlt
    val outline = tokens.border
    val outlineVariant = tokens.divider

    val primary = tokens.primary
    val secondary = tokens.primaryMuted
    val tertiary = tokens.info

    val onBackground = tokens.textPrimary
    val onSurface = tokens.textPrimary
    val onSurfaceVariant = tokens.textSecondary

    val onPrimary = tokens.textOnAccent
    val onSecondary = tokens.textOnAccent
    val onTertiary = tokens.textOnAccent

    val primaryContainer = tokens.primarySurface
    val onPrimaryContainer = tokens.textPrimary
    val secondaryContainer = tokens.surfaceAlt
    val onSecondaryContainer = tokens.textPrimary
    val tertiaryContainer = tokens.surfaceAlt
    val onTertiaryContainer = tokens.textPrimary

    val error = tokens.error
    val onError = tokens.textOnAccent
    val errorContainer = tokens.errorSurface
    val onErrorContainer = tokens.textPrimary

    val inverseSurface = tokens.surfaceAlt
    val inverseOnSurface = tokens.textPrimary
    val inversePrimary = tokens.primary

    val scrim = tokens.scrim
    val surfaceTint = tokens.primary

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,

            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,

            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,

            background = background,
            onBackground = onBackground,

            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,

            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,

            outline = outline,
            outlineVariant = outlineVariant,

            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            inversePrimary = inversePrimary,

            scrim = scrim,
            surfaceTint = surfaceTint
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,

            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,

            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,

            background = background,
            onBackground = onBackground,

            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,

            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,

            outline = outline,
            outlineVariant = outlineVariant,

            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            inversePrimary = inversePrimary,

            scrim = scrim,
            surfaceTint = surfaceTint
        )
    }
}

@Composable
fun VoltHomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val tokens = VhColors.tokens

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.copy(
                primary = tokens.primary,
                onPrimary = tokens.textOnAccent,
                primaryContainer = tokens.primarySurface,
                onPrimaryContainer = tokens.textPrimary,

                background = tokens.bg,
                onBackground = tokens.textPrimary,

                surface = tokens.surface,
                onSurface = tokens.textPrimary,
                surfaceVariant = tokens.surfaceAlt,
                onSurfaceVariant = tokens.textSecondary,

                outline = tokens.border,
                outlineVariant = tokens.divider,

                error = tokens.error,
                onError = tokens.textOnAccent,
                errorContainer = tokens.errorSurface,
                onErrorContainer = tokens.textPrimary,

                scrim = tokens.scrim,
                surfaceTint = tokens.primary
            )
        }

        else -> vhColorScheme(tokens = tokens, dark = true /* индустриальная база единая */)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}