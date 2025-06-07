package ru.mugalimov.volthome.ui.utilities

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.R

@Composable
fun TelegramButton() {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val email = "reenat056@yandex.ru" // Ваша почта

    // Кнопка с иконкой
    IconButton(
        onClick = { showDialog = true }
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = "Консультация",
            modifier = Modifier.size(24.dp)
        )
    }

    // Диалоговое окно
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Нужна помощь профессионала?") },
            text = {
                Column {
                    Text("Получите платную консультацию по проектированию вашего щита! Также консультируем по другим учебным и рабочим вопросам любой сложности!")
                    Spacer(Modifier.height(8.dp))
                    Text("Отправьте детали проекта на email:")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = email,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Рекомендуем приложить:")
                    Text("• Скриншоты технического задания (курсовой работы и др.)")
                    Text("• Срочность выполнения работ")
                    Text("• Описание особенностей вашего вопроса")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Копирование email в буфер обмена
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Консультация email", email)
                        clipboard.setPrimaryClip(clip)

                        // Показать уведомление
                        Toast.makeText(context, "Email скопирован!", Toast.LENGTH_SHORT).show()
                        showDialog = false
                    }
                ) {
                    Text("Скопировать почту")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}