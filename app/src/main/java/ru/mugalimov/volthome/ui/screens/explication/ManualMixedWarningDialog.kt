package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Блокирующее предупреждение о смешении типов в группе (MIXED_MANUAL).
 *
 * Первый раз — показываем диалог.
 * Дальше — snackbar/бейдж (логика "первого раза" живёт во ViewModel / session state).
 */
@Composable
fun ManualMixedWarningDialog(
    onConfirm: (dontShowAgainInSession: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val dontShowAgain = remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смешение типов в группе") },
        text = {
            Text(
                "Ты переносишь устройство в группу другого назначения. " +
                        "Это допустимо, но группа будет помечена как MIXED (ручное смешение)."
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontShowAgain.value) }) {
                Text("Продолжить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )

    // Отдельным блоком — чекбокс (чтобы не раздувать AlertDialog layout)
    // Если хочешь — можно перенести внутрь text(), но так читаемее.
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = {},
        text = {
            Row {
                Checkbox(
                    checked = dontShowAgain.value,
                    onCheckedChange = { dontShowAgain.value = it }
                )
                Spacer(Modifier.width(8.dp))
                Text("Не показывать снова в этой сессии")
            }
        }
    )
}