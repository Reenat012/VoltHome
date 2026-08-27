package ru.mugalimov.volthome.ui.components

import androidx.compose.runtime.Composable

//отображение ошибки
@Composable
fun ErrorView(
    error: Throwable,
    onRetry: () -> Unit = {}
) {
    VhErrorState(
        message = error.localizedMessage ?: "Неизвестная ошибка",
        onRetry = onRetry
    )
}
