package ru.mugalimov.volthome.ui.utilities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.R

@Composable
fun TelegramButton() {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Кнопка с иконкой Телеграмм
    IconButton(
        onClick = { showDialog = true }
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = "Telegram Consultation",
            modifier = Modifier.size(24.dp)
        )
    }

    // Диалоговое окно (вызывается только если showDialog = true)
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Переход в Telegram") },
            text = { Text("Открыть чат с ботом?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Ссылка на вашего бота (замените your_bot_name)
                        val telegramUrl = "tg://resolve?domain=VoltHomeBot"
                        val webUrl = "https://t.me/VoltHomeBot"

                        try {
                            // Пытаемся открыть в приложении
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl))
                            )
                        } catch (e: ActivityNotFoundException) {
                            // Если приложение не установлено, открываем в браузере
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                            )
                        }
                        showDialog = false
                    }
                ) {
                    Text("Открыть")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false}
                ) {
                    Text("Отмена")
                }
            }
        )
    }


}