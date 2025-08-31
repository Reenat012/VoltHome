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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import ru.mugalimov.volthome.legal.LegalUrls
import ru.mugalimov.volthome.ui.screens.algoritm_about.AlgorithmExplanationContent
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
                            Icons.AutoMirrored.Filled.ArrowBack,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    onShowOnboarding: () -> Unit
) {
    var showAlgoSheet by remember { mutableStateOf(false) }
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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Заголовок раздела
        item {
            SectionTitle("Правовая информация")
        }

        // Пользовательское соглашение
        item {
            SettingsTile(
                icon = legalItems[0].icon,
                title = legalItems[0].title,
                subtitle = legalItems[0].subtitle,
                onClick = {
                    context.openDocument(
                        webUrl = legalItems[0].url,
                        localAssetPath = "documents/user_agreement.html"
                    )
                }
            )
        }

        // Политика конфиденциальности
        item {
            SettingsTile(
                icon = legalItems[1].icon,
                title = legalItems[1].title,
                subtitle = legalItems[1].subtitle,
                onClick = {
                    context.openDocument(
                        webUrl = legalItems[1].url,
                        localAssetPath = "documents/privacy_policy.html"
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Заголовок раздела
        item {
            SectionTitle("Информация о приложении")
        }

        // О приложении
        item {
            SettingsTile(
                icon = Icons.Filled.Info,
                title = "О приложении",
                subtitle = "Версия 1.4\nРазработчик: Ринат Мугалимов",
                onClick = {
                    // поведение не меняем — оставь пустым или добавь свой обработчик позже
                }
            )
        }

        // Как работает алгоритм
        item {
            SettingsTile(
                icon = Icons.Filled.AutoStories,
                title = "Как работает алгоритм",
                subtitle = "Пояснение шагов расчёта и балансировки фаз",
                onClick = { showAlgoSheet = true }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }

    // Лист с описанием алгоритма
    if (showAlgoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAlgoSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AlgorithmExplanationContent()
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { showAlgoSheet = false },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Понятно") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/* ───────────────────── UI-компоненты ───────────────────── */

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/** Унифицированная «плитка» без ListItem — никаких внутренних линий/теней */
@Composable
private fun SettingsTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ───────────────────── Модель данных ───────────────────── */

private data class LegalItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val url: String
)