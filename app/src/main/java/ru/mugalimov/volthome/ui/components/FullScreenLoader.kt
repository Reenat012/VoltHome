package ru.mugalimov.volthome.ui.components

import androidx.compose.runtime.Composable

// Компонент загрузки
@Composable
fun FullScreenLoader() {
    VhLoadingState(message = "Запускаем ВольтХом…")
}
