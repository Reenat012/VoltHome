package ru.mugalimov.volthome.ui.screens.about

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.legal.LegalUrls
import ru.mugalimov.volthome.ui.screens.algoritm_about.AlgorithmExplanationContent
import ru.mugalimov.volthome.ui.screens.welcome.openDocument
import ru.mugalimov.volthome.ui.components.VhSectionHeader
import ru.mugalimov.volthome.ui.components.VhTopBar
import ru.mugalimov.volthome.ui.components.VhListRow
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.ui.viewmodel.PrivacySettingsViewModel

/* ───────────── Константы ───────────── */
private const val TELEGRAM_URL = "https://t.me/volthomeapp"

/* ───────────────────── Экран ───────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onShowOnboarding: () -> Unit,
    onOpenAlgorithm: () -> Unit,
    privacyViewModel: PrivacySettingsViewModel = hiltViewModel()
) {
    val t = VhColors.tokens
    val analyticsEnabled by privacyViewModel.analyticsEnabled.collectAsState()

    Scaffold(
        topBar = { VhTopBar(title = "Справка и документы", onBack = onBack) },
        containerColor = t.bg
    ) { innerPadding ->
        SettingsContent(
            modifier = Modifier.padding(innerPadding),
            onShowOnboarding = onShowOnboarding,
            onOpenAlgorithm = onOpenAlgorithm,
            analyticsEnabled = analyticsEnabled,
            onAnalyticsChanged = privacyViewModel::setAnalyticsEnabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    onShowOnboarding: () -> Unit,
    onOpenAlgorithm: () -> Unit,
    analyticsEnabled: Boolean,
    onAnalyticsChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val t = VhColors.tokens

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
        ),
        LegalItem(
            icon = Icons.Filled.PrivacyTip,
            title = "Согласие для Яндекс ID",
            subtitle = "Действует только при добровольном входе",
            url = LegalUrls.PD_CONSENT
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }

        item { SectionHeader("Методика и помощь") }

        item {
            SettingsTile(
                icon = Icons.Filled.AutoStories,
                title = "Методика расчёта",
                subtitle = "Этапы расчёта, группировки и балансировки фаз",
                onClick = onOpenAlgorithm,
                prominent = true
            )
        }

        item {
            SettingsTile(
                icon = Icons.Filled.Replay,
                title = "Повторить знакомство",
                subtitle = "Снова показать основные возможности приложения",
                onClick = onShowOnboarding
            )
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }

        item { SectionHeader("Документы и данные") }

        item {
            Surface(
                color = t.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAnalyticsChanged(!analyticsEnabled) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Анонимная аналитика",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Диагностика стабильности без данных проектов",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textSecondary
                        )
                    }
                    Switch(
                        checked = analyticsEnabled,
                        onCheckedChange = onAnalyticsChanged
                    )
                }
            }
        }

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
        item {
            SettingsTile(
                icon = legalItems[2].icon,
                title = legalItems[2].title,
                subtitle = legalItems[2].subtitle,
                onClick = {
                    context.openDocument(
                        webUrl = legalItems[2].url,
                        localAssetPath = "documents/pd_consent.html"
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }

        item { SectionHeader("Помощь") }

        item {
            SettingsTile(
                icon = Icons.AutoMirrored.Filled.Send,
                title = "Канал ВольтХом",
                subtitle = "Новости, алгоритмы и обратная связь",
                onClick = { context.openExternalUrl(TELEGRAM_URL) }
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        /* ── Футер ── */
        item {
            FooterVersion()
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

}

/* ───────────────────── UI-компоненты ───────────────────── */

@Composable
private fun SectionHeader(title: String) {
    VhSectionHeader(title = title)
}

/** Универсальная плитка настроек. Для CTA используйте prominent = true. */
@Composable
private fun SettingsTile(
    icon: ImageVector? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    prominent: Boolean = false
) {
    VhListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick
    )
}

@Composable
private fun FooterVersion() {
    val context = LocalContext.current
    val t = VhColors.tokens
    val version = remember {
        runCatching {
            val pm: PackageManager = context.packageManager
            val pInfo = pm.getPackageInfo(context.packageName, 0)
            val name = pInfo.versionName ?: "—"
            val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            "v$name • build $build"
        }.getOrElse { "v—" }
    }
    Text(
        text = "ВольтХом © 2026 • $version",
        style = MaterialTheme.typography.bodyMedium,
        color = t.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center
    )
}

/* ───────────────────── Модель данных ───────────────────── */

private data class LegalItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val url: String
)

/* ───────────────────── Утилита открытия URL ───────────────────── */

private fun Context.openExternalUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
