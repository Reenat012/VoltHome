package ru.mugalimov.volthome.ui.manual

import androidx.compose.runtime.staticCompositionLocalOf

val LocalManualModeGuard = staticCompositionLocalOf<ManualModeGuard> {
    error("LocalManualModeGuard is not provided")
}