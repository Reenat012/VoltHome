package ru.mugalimov.volthome.ui.screens.subscription

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.domain.model.UserPlan
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.viewmodel.SubscriptionViewModel

@Composable
fun VoltHomeProScreen(
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val userPlan: UserPlan = LocalUserPlan.current

    // При первом открытии экрана:
    // 1) обновляем текущий тариф с сервера,
    // 2) пробуем загрузить реальный продукт из RuStore SDK.
    LaunchedEffect(Unit) {
        Log.d("VoltHomeProScreen", "LaunchedEffect → init")
        viewModel.refreshStatus()
        viewModel.loadProProductIfNeeded()
    }

    Scaffold(
        // Insets уже обработаны родительским контейнером,
        // поэтому здесь их обнуляем, чтобы не получить двойной верхний отступ.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val featureItems = listOf(
            ProFeatureUiModel(
                icon = Icons.Outlined.Folder,
                title = stringResource(R.string.pro_feature_projects_title),
                description = stringResource(R.string.pro_feature_projects_description)
            ),
            ProFeatureUiModel(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.pro_feature_device_edit_title),
                description = stringResource(R.string.pro_feature_device_edit_description)
            ),
            ProFeatureUiModel(
                icon = Icons.Outlined.AccountTree,
                title = stringResource(R.string.pro_feature_manual_mode_title),
                description = stringResource(R.string.pro_feature_manual_mode_description)
            ),
            ProFeatureUiModel(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.pro_feature_pdf_title),
                description = stringResource(R.string.pro_feature_pdf_description)
            )
        )

        val statusText = if (userPlan.isPro) {
            stringResource(R.string.pro_status_active)
        } else {
            stringResource(R.string.pro_status_free)
        }

        // Кнопка покупки живёт не по старому isLoading,
        // а по продуктовой готовности и purchase-related stage.
        val buttonEnabled =
            !userPlan.isPro &&
                uiState.isProductLoaded &&
                !uiState.isProductLoading &&
                uiState.stage != SubscriptionViewModel.BillingStage.PURCHASING &&
                uiState.stage != SubscriptionViewModel.BillingStage.CONFIRMING

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Верхняя hero-карточка.
            // Она задаёт экрану правильное позиционирование:
            // это не просто подписка, а профессиональный режим приложения.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pro_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = stringResource(R.string.pro_subtitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = stringResource(R.string.pro_hero_supporting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Отдельная статус-плашка, чтобы пользователь сразу видел,
            // какой у него сейчас тариф.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pro_status_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Text(
                text = stringResource(R.string.pro_features_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Карточки преимуществ с иконками.
            // Так каждая функция выглядит как самостоятельная ценность,
            // а не как пункт обычного списка.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                featureItems.forEach { feature ->
                    ProFeatureCard(
                        icon = feature.icon,
                        title = feature.title,
                        description = feature.description
                    )
                }
            }

            // Информационный блок про временные условия подписки.
            // Формулировка спокойная: без агрессивного давления,
            // но честно объясняет, что текущая цена не обязательно финальная.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pro_temporary_price_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    Text(
                        text = stringResource(R.string.pro_temporary_price_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // Отдельный CTA-блок:
            // здесь собраны покупка, обновление статуса и служебная информация.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pro_cta_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Состояние загрузки продукта из RuStore SDK.
                    if (uiState.isProductLoading) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.billing_product_loading),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }

                    // SDK ответил корректно, но нужного продукта нет.
                    if (uiState.isProductUnavailable) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = stringResource(R.string.billing_product_unavailable),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }

                    // Ошибка загрузки продукта.
                    // Показываем её только если это не unavailable-case.
                    if (!uiState.isProductLoading &&
                        !uiState.isProductLoaded &&
                        !uiState.isProductUnavailable &&
                        uiState.errorMessage != null
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = stringResource(R.string.billing_product_load_failed),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }

                    // Общее информационное сообщение из ViewModel.
                    uiState.infoMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            Log.d("VoltHomeProScreen", "Check availability clicked")
                            viewModel.debugCheckAvailability()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("DEBUG: Check availability")
                    }

                    // Если есть продуктовая готовность, пользователь может покупать.
                    // Если нет — кнопка disabled, это и есть purchase gating.
                    Button(
                        onClick = {
                            Log.d(
                                "VoltHomeProScreen",
                                "Buy click → isPro=${userPlan.isPro}, loaded=${uiState.isProductLoaded}, loading=${uiState.isProductLoading}, unavailable=${uiState.isProductUnavailable}, stage=${uiState.stage}"
                            )

                            if (!userPlan.isPro) {
                                viewModel.buyPro()
                            }
                        },
                        enabled = buttonEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (userPlan.isPro) {
                                stringResource(R.string.pro_button_active)
                            } else {
                                stringResource(R.string.pro_button_buy)
                            }
                        )
                    }

                    // Если покупка недоступна, явно показываем это под кнопкой,
                    // чтобы disabled-состояние не выглядело как баг.
                    if (!userPlan.isPro && !buttonEnabled) {
                        Text(
                            text = stringResource(R.string.billing_purchase_not_available),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    TextButton(
                        onClick = {
                            Log.d("VoltHomeProScreen", "Refresh status clicked")
                            viewModel.refreshStatus()
                            viewModel.loadProProductIfNeeded()
                        },
                        enabled = !uiState.isLoading && !uiState.isProductLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.pro_restore_purchases))
                    }

                    Text(
                        text = stringResource(R.string.pro_payment_info),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Иконка вынесена в мягкую плашку.
            // Это даёт аккуратный акцент без визуального шума.
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class ProFeatureUiModel(
    val icon: ImageVector,
    val title: String,
    val description: String,
)