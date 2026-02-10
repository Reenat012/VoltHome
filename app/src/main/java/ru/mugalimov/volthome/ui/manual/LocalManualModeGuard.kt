package ru.mugalimov.volthome.ui.manual

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal для доступа к guard-у из UI без прокидывания параметров через весь граф.
 */
val LocalManualModeGuard = staticCompositionLocalOf<ManualModeGuard> {
    error("LocalManualModeGuard is not provided")
}