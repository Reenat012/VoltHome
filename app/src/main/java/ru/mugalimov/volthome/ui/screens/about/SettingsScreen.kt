import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.legal.LegalUrls
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.screens.onboarding.OnboardingScreen
import ru.mugalimov.volthome.ui.screens.welcome.openDocument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onShowOnboarding: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Настройки",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        SettingsContent(
            modifier = Modifier.padding(innerPadding),
            onShowOnboarding = onShowOnboarding
        )
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    onShowOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val legalItems = listOf(
        LegalItem(
            icon = Icons.Filled.Description,
            title = "Пользовательское соглашение",
            subtitle = "Условия использования приложения",
            url = LegalUrls.AGREEMENT
        ),
        LegalItem(
            icon = Icons.Filled.PrivacyTip,
            title = "Политика конфиденциальности",
            subtitle = "Как мы обрабатываем ваши данные",
            url = LegalUrls.PRIVACY
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Заголовок раздела
        item {
            Text(
                "Правовая информация",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Карточка с юридическими документами
        // Карточки с юридическими документами (отдельные карточки)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                LegalDocumentItem(
                    item = legalItems[0], // Пользовательское соглашение
                    onClick = {
                        context.openDocument(
                            webUrl = legalItems[0].url,
                            localAssetPath = "documents/user_agreement.html"
                        )
                    }
                )
            }
        }

// Отступ между карточками
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                LegalDocumentItem(
                    item = legalItems[1], // Политика конфиденциальности
                    onClick = {
                        context.openDocument(
                            webUrl = legalItems[1].url,
                            localAssetPath = "documents/privacy_policy.html"
                        )
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Заголовок раздела
        item {
            Text(
                "Информация о приложении",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }


        // Информация о приложении
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                ListItem(
                    headlineContent = { Text("О приложении") },
                    supportingContent = {
                        Text("Версия 1.0.0 • Сборка 123\nКликни чтобы снова запустить анимированную инструкцию ")
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        // Запуск анимации
                        onShowOnboarding()
                    }
                )
            }
        }
    }
}

@Composable
private fun LegalDocumentItem(item: LegalItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal
            )
        },
        supportingContent = {
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                item.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

private data class LegalItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val url: String
)