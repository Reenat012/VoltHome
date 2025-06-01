package ru.mugalimov.volthome.ui.screens.explication

import GroupCard
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.ui.utilities.TelegramButton

@Composable
fun GroupList(
    groups: List<CircuitGroup>,
    modifier: Modifier = Modifier
) {

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {


        items(groups) { group ->
            GroupCard(
                group = group,
                modifier = Modifier.fillMaxWidth(),
            )
        }

    }

    TelegramConsultationButton()


}

@Composable
fun TelegramConsultationButton(
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        ElevatedButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = MaterialTheme.shapes.medium,
            elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = "Telegram Consultation",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Получить консультацию",
                style = MaterialTheme.typography.labelLarge
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
}




