package ru.mugalimov.volthome.ui.utilities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

// ---------- Открытие Telegram-бота (tg:// + fallback https://) ----------
fun openVoltHomeBot(
    context: android.content.Context,
    botName: String = "VoltHomeBot",
    startPayloadBase64: String? = null
) {
    val startSuffix = startPayloadBase64?.let { "&start=$it" } ?: ""
    val tgUrl = "tg://resolve?domain=$botName$startSuffix"
    val webUrl = "https://t.me/$botName${startSuffix.replaceFirst("&", "?")}"

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, tgUrl.toUri()))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, webUrl.toUri()))
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть Telegram", Toast.LENGTH_LONG).show()
        }
    }
}

// (опционально) кодировщик payload — оставляем как было
fun encodeStartPayload(fields: Map<String, Any?>): String {
    val json = buildString {
        append('{')
        append(fields.entries.joinToString(",") { (k, v) ->
            val value = when (v) {
                null -> "null"
                is Number, is Boolean -> v.toString()
                else -> "\"${v.toString().replace("\"", "\\\"")}\""
            }
            "\"$k\":$value"
        })
        append('}')
    }
    return Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)
}

// ---------- ДИАЛОГ: используем в StartDrawer по клику на пункт меню ----------
@Composable
fun TelegramConsultationDialog(
    botName: String = "VoltHomeBot",
    startPayloadBase64: String? = null,   // MVP: передаём null
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SupportAgent, contentDescription = null) },
        title = {
            Column {
                Text("Консультация со специалистом", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Бета-версия",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        text = {
            Column {
                Text("Разберём ваш проект и поможем с подбором:")
                Spacer(Modifier.height(6.dp))
                Text("• автоматов и УЗО по маркам\n• кабельных линий (ВВГнг и др.)\n• балансировки фаз и схемы щита")
                Spacer(Modifier.height(12.dp))
                Text(
                    "Функция находится в бета-тестировании. " +
                            "По кнопке «Открыть Telegram» запустится бот VoltHome, где вы оставите заявку на консультацию."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                openVoltHomeBot(
                    context = context,
                    botName = botName,
                    startPayloadBase64 = startPayloadBase64  // MVP: null
                )
            }) { Text("Открыть Telegram") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ---------- Кнопка (на будущее): показывает тот же диалог ----------
@Composable
fun TelegramConsultationButton(
    botName: String = "VoltHomeBot",
    startPayloadBase64: String? = null,
    asIconOnly: Boolean = true,
    buttonContentDescription: String = "Консультация"
) {
    var show by remember { mutableStateOf(false) }
    IconButton(onClick = { show = true }) {
        Icon(imageVector = Icons.Outlined.SupportAgent, contentDescription = buttonContentDescription)
    }
    if (show) TelegramConsultationDialog(
        botName = botName,
        startPayloadBase64 = startPayloadBase64,
        onDismiss = { show = false }
    )
}