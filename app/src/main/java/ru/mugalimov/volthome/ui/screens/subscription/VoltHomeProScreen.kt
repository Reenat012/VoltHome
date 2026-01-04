package ru.mugalimov.volthome.ui.screens.subscription

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import ru.mugalimov.volthome.domain.model.UserPlan
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.viewmodel.SubscriptionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoltHomeProScreen(
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val userPlan: UserPlan = LocalUserPlan.current
    val context = LocalContext.current
    val activity = context as? Activity

    var showInvalidActivePurchaseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStatus()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { e ->
            when (e) {
                SubscriptionViewModel.UiEvent.ShowInvalidActivePurchaseDialog -> {
                    showInvalidActivePurchaseDialog = true
                }
            }
        }
    }

    if (showInvalidActivePurchaseDialog) {
        AlertDialog(
            onDismissRequest = { showInvalidActivePurchaseDialog = false },
            title = { Text("Не удалось открыть оплату") },
            text = {
                Text(
                    "RuStore сообщил о некорректном активном состоянии покупки (тип активной покупки не совпадает). " +
                            "Попробуйте восстановить покупки."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showInvalidActivePurchaseDialog = false
                        if (activity != null) {
                            viewModel.restorePurchases(activity)
                        }
                    }
                ) { Text("Восстановить покупки") }
            },
            dismissButton = {
                TextButton(onClick = { showInvalidActivePurchaseDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("VoltHome PRO") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Подписка VoltHome PRO",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            val statusText = if (userPlan.isPro) {
                "У вас активна подписка VoltHome PRO."
            } else {
                "Сейчас вы пользуетесь бесплатным тарифом."
            }

            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Что даёт VoltHome PRO:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Bullet("Безлимитное количество проектов (FREE — до 3 проектов).")
                Bullet("Продвинутые алгоритмы распределения нагрузок и ввода.")
                Bullet("Расширенный PDF-отчёт с расчётами и диаграммами.")
                Bullet("Приоритетные обновления и новые PRO-фичи.")
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))

            uiState.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            uiState.infoMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))

            if (activity == null) {
                Text(
                    text = "Оплата недоступна: нет ссылки на Activity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Button(
                    onClick = { if (!userPlan.isPro) viewModel.buyPro(activity) },
                    enabled = !userPlan.isPro && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (userPlan.isPro) "Подписка уже активна" else "Купить VoltHome PRO")
                }
            }

            TextButton(
                onClick = { if (activity != null) viewModel.restorePurchases(activity) },
                enabled = !uiState.isLoading && activity != null,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Восстановить покупки")
            }

            Spacer(modifier = Modifier.padding(bottom = 24.dp))

            Text(
                text = "Оплата обрабатывается через RuStore. После успешной покупки подписка привязывается к вашему аккаунту.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text(text = "• $text", style = MaterialTheme.typography.bodyMedium)
}