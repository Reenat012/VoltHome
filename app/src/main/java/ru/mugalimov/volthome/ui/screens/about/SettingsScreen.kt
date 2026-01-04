package ru.mugalimov.volthome.ui.screens.about

import androidx.compose.ui.res.painterResource
import ru.mugalimov.volthome.R
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.legal.LegalUrls
import ru.mugalimov.volthome.ui.screens.algoritm_about.AlgorithmExplanationContent
import ru.mugalimov.volthome.ui.screens.welcome.openDocument

/* ───────────── Константы ───────────── */
private const val TELEGRAM_URL = "https://t.me/volthomeapp"

/* ───────────────────── Экран ───────────────────── */

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
                        "Информация",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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

        /* ── Правовая информация ── */
        item { SectionHeader("Правовая информация") }

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

        item { Spacer(modifier = Modifier.height(20.dp)) }

        /* ── Информация о приложении ── */
        item { SectionHeader("Информация о приложении") }

        item {
            SettingsTile(
                icon = Icons.Filled.AutoStories,
                title = "Как работает алгоритм",
                subtitle = "Пояснение шагов расчёта и балансировки фаз",
                onClick = { showAlgoSheet = true }
            )
        }

//        // Выделенная (фиолетовая) CTA-плитка Telegram
//        item {
//            SettingsTile(
//                iconPainter = painterResource(R.drawable.telegram_communication_chat_interaction_network_connection), // можно заменить на Campaign при наличии icons-extended
//                title = "Telegram-канал VoltHome",
//                subtitle = "Новости, обновления и советы по электрике",
//                onClick = { context.openExternalUrl(TELEGRAM_URL) },
//                prominent = true
//            )
//        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        /* ── Футер ── */
        item {
            FooterVersion()
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    /* ── BottomSheet: «Как работает алгоритм» ── */
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
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Divider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.5.dp
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** Универсальная плитка настроек. Для CTA используйте prominent = true. */
@Composable
private fun SettingsTile(
    icon: ImageVector? = null,        // ← теперь необязательно
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    prominent: Boolean = false
) {
    val bg = if (prominent) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh

    val titleColor = if (prominent) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    val subtitleColor = if (prominent) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
    else MaterialTheme.colorScheme.onSurfaceVariant

    val iconTint = if (prominent) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.primary

    val shape = if (prominent) RoundedCornerShape(20.dp) else RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = bg,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (prominent) 16.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                iconPainter != null -> {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(if (prominent) 28.dp else 40.dp),
                        tint = iconTint
                    )
                }
                icon != null -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (prominent) 28.dp else 40.dp),
                        tint = iconTint
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    style = if (prominent) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Medium,
                    color = titleColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor
                )
            }
        }
    }
}

@Composable
private fun FooterVersion() {
    val context = LocalContext.current
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
        text = "VoltHome © 2025 • $version",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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