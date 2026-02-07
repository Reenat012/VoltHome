package ru.mugalimov.volthome.ui.manual

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState

@Composable
fun ManualModeGuardDialog(guard: ManualModeGuard) {
    val st = guard.state.collectAsState().value
    if (!st.isDialogVisible) return

    AlertDialog(
        onDismissRequest = { guard.onStay() },
        title = { Text(st.title) },
        text = { Text(st.message) },
        confirmButton = {
            TextButton(onClick = { guard.onSave() }) { Text("Сохранить") }
        },
        dismissButton = {
            // две кнопки снизу: Cancel + Stay
            // Material3 AlertDialog ограничен, поэтому: Stay = onDismissRequest,
            // а Cancel делаем второй "dismissButton" через текст
            TextButton(onClick = { guard.onCancel() }) { Text("Отменить") }
        }
    )
}

/**
 * Важно: по ТЗ нужно 3 действия: Save / Cancel / Stay.
 * В Material3 AlertDialog штатно 2 слота кнопок.
 *
 * Политика:
 * - Stay = закрыть диалог (tap outside / back / onDismissRequest)
 * - Cancel = кнопка "Отменить"
 * - Save = кнопка "Сохранить"
 *
 * Если хочешь именно 3 явные кнопки внизу — сделаем кастомный Dialog в следующем коммите.
 */