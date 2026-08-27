package ru.mugalimov.volthome.ui.screens.subscription

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.core.analytics.AnalyticsEvent
import ru.mugalimov.volthome.core.analytics.AnalyticsRuntimeEntryPoint
import ru.mugalimov.volthome.core.analytics.PaywallSource
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.domain.model.UserPlan
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.viewmodel.SubscriptionViewModel
import ru.mugalimov.volthome.ui.viewmodel.DemoProjectViewModel

@Composable
fun VoltHomeProScreen(
    viewModel: SubscriptionViewModel = hiltViewModel(),
    demoViewModel: DemoProjectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val demoState by demoViewModel.state.collectAsStateWithLifecycle()
    val userPlan: UserPlan = LocalUserPlan.current
    val t = VhColors.tokens
    val context = LocalContext.current
    val appContext = context.applicationContext
    var showSubscriptionManagementDialog by remember { mutableStateOf(false) }
    var subscriptionManagementError by remember { mutableStateOf(false) }
    val analyticsRuntime = remember(appContext) {
        EntryPointAccessors.fromApplication(
            appContext,
            AnalyticsRuntimeEntryPoint::class.java
        )
    }
    val analytics = analyticsRuntime.analyticsTracker()
    val purchaseAnalyticsContext = analyticsRuntime.purchaseAnalyticsContext()

    // При первом открытии сверяем локальный тариф с RuStore
    // и загружаем продукт для возможной покупки.
    LaunchedEffect(Unit) {
        purchaseAnalyticsContext
            .markPaywallShownIfNeeded(PaywallSource.PRO_SCREEN)
            ?.let { source ->
                analytics.track(AnalyticsEvent.PaywallShown(source))
            }
        Log.d("VoltHomeProScreen", "LaunchedEffect → init")
        viewModel.refreshStatus()
        viewModel.loadProProductIfNeeded()
    }

    if (showSubscriptionManagementDialog) {
        AlertDialog(
            onDismissRequest = { showSubscriptionManagementDialog = false },
            title = {
                Text(stringResource(R.string.pro_manage_subscription_dialog_title))
            },
            text = {
                Text(stringResource(R.string.pro_manage_subscription_dialog_text))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubscriptionManagementDialog = false
                        subscriptionManagementError = !openRuStoreSubscriptions(context)
                    }
                ) {
                    Text(stringResource(R.string.pro_manage_subscription_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubscriptionManagementDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        // Insets уже обработаны родительским контейнером,
        // поэтому здесь их обнуляем, чтобы не получить двойной верхний отступ.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = t.bg
    ) { padding ->
        val featureItems = listOf(
            ProFeatureUiModel(
                icon = Icons.Outlined.Cable,
                title = "Расчёт кабельных линий",
                description = "Учитывайте длину, материал, способ прокладки, температуру, группировку и допустимое падение напряжения."
            ),
            ProFeatureUiModel(
                icon = Icons.Outlined.AccountTree,
                title = "Визуализация и смета щита",
                description = "Компонуйте аппараты на DIN-рейках, выбирайте модели и контролируйте стоимость проекта."
            ),
            ProFeatureUiModel(
                icon = Icons.Outlined.Tune,
                title = "Каталог совместимых аппаратов",
                description = "Подбирайте производителя и модель или фиксируйте собственную закупочную цену."
            ),
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
        val productPrice = uiState.productPriceLabel
            ?: stringResource(R.string.pro_price_fallback)
        val monthlyPrice = stringResource(R.string.pro_price_monthly, productPrice)

        // Кнопка покупки живёт не по старому isLoading,
        // а по продуктовой готовности и purchase-related stage.
        val buttonEnabled =
            !userPlan.isPro &&
                    uiState.isProductLoaded &&
                    !uiState.isProductLoading &&
                    !uiState.isRestoring &&
                    uiState.stage != SubscriptionViewModel.BillingStage.PURCHASING &&
                    uiState.stage != SubscriptionViewModel.BillingStage.RESTORING

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
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = t.primarySurface
                ),
                border = BorderStroke(1.dp, t.primary.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = t.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, t.primary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "PRO · ИНЖЕНЕРНЫЙ РЕЖИМ",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = t.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.pro_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = t.textPrimary
                    )

                    Text(
                        text = stringResource(R.string.pro_subtitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = t.textPrimary
                    )

                    Text(
                        text = stringResource(R.string.pro_hero_supporting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.textSecondary
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (userPlan.isPro) t.success else t.primary
                    )
                }
            }

            // Отдельная статус-плашка, чтобы пользователь сразу видел,
            // какой у него сейчас тариф.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = t.surfaceAlt,
                border = BorderStroke(1.dp, t.divider)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pro_status_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = t.textPrimary
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.textSecondary
                    )
                }
            }

            PlanComparisonCard(monthlyPrice = monthlyPrice)

            DemoProjectCard(
                isLoading = demoState.isLoading,
                errorMessage = demoState.errorMessage,
                onOpen = demoViewModel::open
            )

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

            // Цена показывается до запуска billing flow, чтобы условия покупки
            // были понятны пользователю заранее.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = t.primarySurface,
                border = BorderStroke(1.dp, t.primary.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (userPlan.isPro) {
                        Text(
                            text = monthlyPrice,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = t.textPrimary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.pro_trial_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = t.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.pro_trial_then_price, productPrice),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = t.primary
                        )
                    }

                    Text(
                        text = stringResource(R.string.pro_price_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary
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

                    // Состояние ручного восстановления покупок.
                    if (uiState.isRestoring) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.billing_restore_loading),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
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

//                    TextButton(
//                        onClick = {
//                            Log.d("VoltHomeProScreen", "Check availability clicked")
//                            viewModel.debugCheckAvailability()
//                        },
//                        modifier = Modifier.align(Alignment.CenterHorizontally)
//                    ) {
//                        Text("DEBUG: Check availability")
//                    }

                    // Если есть продуктовая готовность, пользователь может покупать.
                    // Если нет — кнопка disabled, это и есть purchase gating.
                    if (userPlan.isPro) {
                        OutlinedButton(
                            onClick = {
                                subscriptionManagementError = false
                                showSubscriptionManagementDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.pro_manage_subscription))
                        }

                        Text(
                            text = stringResource(R.string.pro_manage_subscription_note),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Button(
                            onClick = {
                                Log.d(
                                    "VoltHomeProScreen",
                                    "Buy click → isPro=${userPlan.isPro}, loaded=${uiState.isProductLoaded}, loading=${uiState.isProductLoading}, unavailable=${uiState.isProductUnavailable}, stage=${uiState.stage}"
                                )
                                viewModel.buyPro()
                            },
                            enabled = buttonEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.pro_button_start_trial))
                        }
                    }

                    if (subscriptionManagementError) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = stringResource(R.string.pro_manage_subscription_error),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
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
                            Log.d("VoltHomeProScreen", "Restore purchases clicked")
                            viewModel.restorePurchases()
                        },
                        enabled = uiState.isRestoreAvailable &&
                                !uiState.isLoading &&
                                !uiState.isProductLoading &&
                                !uiState.isRestoring,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.pro_restore_purchases))
                    }

                    TextButton(
                        onClick = {
                            Log.d("VoltHomeProScreen", "Refresh status clicked")
                            viewModel.refreshStatus()
                            viewModel.loadProProductIfNeeded()
                        },
                        enabled = !uiState.isLoading &&
                                !uiState.isProductLoading &&
                                !uiState.isRestoring,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.pro_refresh_status))
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

private fun openRuStoreSubscriptions(context: Context): Boolean = try {
    context.startActivity(
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("rustore://profile/subscriptions")
        )
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}

@Composable
private fun PlanComparisonCard(monthlyPrice: String) {
    val t = VhColors.tokens
    val rows = listOf(
        Triple("Расчёт нагрузок, групп и защиты", "Доступно", "Доступно"),
        Triple("Количество проектов", "До 3", "Без лимита"),
        Triple("Визуализация и смета щита", "Превью", "Полный доступ"),
        Triple("Ручная структура щита", "—", "Доступно"),
        Triple("Расширенный PDF", "Превью", "Экспорт")
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = t.surfaceAlt),
        border = BorderStroke(1.dp, t.divider)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.pro_comparison_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = t.textPrimary
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1.35f))
                Text(
                    text = "FREE",
                    modifier = Modifier.weight(0.72f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = t.textSecondary
                )
                Text(
                    text = "PRO",
                    modifier = Modifier.weight(0.93f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = t.primary
                )
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = t.divider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.first,
                        modifier = Modifier.weight(1.35f),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textPrimary
                    )
                    Text(
                        text = row.second,
                        modifier = Modifier.weight(0.72f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = t.textSecondary
                    )
                    Text(
                        text = row.third,
                        modifier = Modifier.weight(0.93f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = t.primary
                    )
                }
            }
            Text(
                text = monthlyPrice,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = t.primary
            )
        }
    }
}

@Composable
private fun DemoProjectCard(
    isLoading: Boolean,
    errorMessage: String?,
    onOpen: () -> Unit,
) {
    val t = VhColors.tokens
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = t.primarySurface),
        border = BorderStroke(1.dp, t.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.pro_demo_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = t.textPrimary
            )
            Text(
                text = stringResource(R.string.pro_demo_description),
                style = MaterialTheme.typography.bodyMedium,
                color = t.textSecondary
            )
            OutlinedButton(
                onClick = onOpen,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (isLoading) {
                        stringResource(R.string.pro_demo_loading)
                    } else {
                        stringResource(R.string.pro_demo_button)
                    }
                )
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = stringResource(R.string.pro_demo_note),
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
        }
    }
}

@Composable
private fun ProFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val t = VhColors.tokens
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = t.surfaceAlt
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, t.divider)
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
                color = t.primarySurface
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = t.primary
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
                    fontWeight = FontWeight.SemiBold,
                    color = t.textPrimary
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textSecondary
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
