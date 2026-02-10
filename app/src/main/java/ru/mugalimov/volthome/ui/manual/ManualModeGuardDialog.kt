package ru.mugalimov.volthome.ui.manual

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Единый диалог для всех запрещённых действий в manual.
 * Save / Cancel / Stay.
 *
 * Важно:
 * - Две кнопки в dismissButton делаем через Row, чтобы они не “слипались” в один слот.
 * - Во время выполнения Save/Cancel блокируем повторные клики (state.isProcessing).
 */
@Composable
fun ManualModeGuardDialog(
    guard: ManualModeGuard
) {
    val state by guard.dialogState.collectAsState()
    val s = state ?: return

    val enabled = !s.isProcessing

    AlertDialog(
        onDismissRequest = {
            // Закрывать во время выполнения — плохая идея (гонки), поэтому блокируем.
            if (enabled) guard.dismiss()
        },
        title = { Text(s.action.title) },
        text = { Text(s.action.message) },

        confirmButton = {
            TextButton(
                onClick = { guard.onSaveClicked() },
                enabled = enabled
            ) {
                Text("Сохранить")
            }
        },

        dismissButton = {
            Row {
                TextButton(
                    onClick = { guard.dismiss() },
                    enabled = enabled
                ) {
                    Text("Остаться")
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = { guard.onCancelClicked() },
                    enabled = enabled
                ) {
                    Text("Отменить")
                }
            }
        }
    )
}