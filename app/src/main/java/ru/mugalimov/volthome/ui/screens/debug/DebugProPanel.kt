package ru.mugalimov.volthome.ui.screens.debug


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.ui.viewmodel.DebugProViewModel

@Composable
fun DebugProPanel(
    modifier: Modifier = Modifier,
    vm: DebugProViewModel = hiltViewModel()
) {
    if (!BuildConfig.DEBUG) return

    val enabled = vm.enabled.collectAsStateWithLifecycle().value

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // 🔹 Существующий переключатель
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (enabled) "DEBUG: PRO ON" else "DEBUG: PRO OFF",
                    style = MaterialTheme.typography.labelLarge
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { vm.setEnabled(it) }
                )
            }

            // 🔥 ВОТ СЮДА ДОБАВЛЯЕМ КНОПКУ
            Button(
                onClick = { vm.forceRefreshSession() }
            ) {
                Text("Force refresh session")
            }
        }
    }
}