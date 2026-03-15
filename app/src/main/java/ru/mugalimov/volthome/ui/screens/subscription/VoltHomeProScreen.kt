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

    LaunchedEffect(Unit) {
        Log.d("VoltHomeProScreen", "LaunchedEffect → refreshStatus()")
        viewModel.refreshStatus()
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

        val buttonEnabled = !userPlan.isPro && !uiState.isLoading

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
            // здесь собраны покупка, восстановление и служебная информация.
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

                    uiState.errorMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

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

                    Button(
                        onClick = {
                            Log.d(
                                "VoltHomeProScreen",
                                "Buy button clicked, userPlan.isPro=${userPlan.isPro}, isLoading=${uiState.isLoading}"
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

                    TextButton(
                        onClick = {
                            Log.d("VoltHomeProScreen", "Refresh status clicked")
                            viewModel.refreshStatus()
                        },
                        enabled = !uiState.isLoading,
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