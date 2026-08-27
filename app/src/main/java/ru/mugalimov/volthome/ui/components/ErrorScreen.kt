package ru.mugalimov.volthome.ui.components

import androidx.compose.runtime.Composable

// Компонент ошибки
@Composable
fun ErrorScreen(error: String) {
    VhErrorState(message = error)
}
