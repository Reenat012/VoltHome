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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhColors
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
    val t = VhColors.tokens

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Информация",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = t.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = t.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = t.bg,
                    titleContentColor = t.textPrimary,
                    navigationIconContentColor = t.textSecondary,
                    actionIconContentColor = t.textSecondary
                )
            )
        },
        containerColor = t.bg
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
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = t.surface,
            contentColor = t.textPrimary
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
                    modifier = Modifier.align(Alignment.End),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = t.surfaceAlt,
                        contentColor = t.textPrimary,
                        disabledContainerColor = t.surface,
                        disabledContentColor = t.textDisabled
                    )
                ) { Text("Понятно") }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/* ───────────────────── UI-компоненты ───────────────────── */

@Composable
private fun SectionHeader(title: String) {
    val t = VhColors.tokens
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = t.primary
        )
        Spacer(Modifier.height(8.dp))
        Divider(
            color = t.divider,
            thickness = 1.dp
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** Универсальная плитка настроек. Для CTA используйте prominent = true. */
@Composable
private fun SettingsTile(
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    prominent: Boolean = false
) {
    val t = VhColors.tokens

    // В Settings не должно быть “фиолетовой CTA” — только один акцент (primary) и спокойные поверхности.
    val bg = if (prominent) t.primarySurface else t.surfaceAlt
    val border = if (prominent) t.primary else t.divider

    val titleColor = t.textPrimary
    val subtitleColor = t.textSecondary
    val iconTint = if (prominent) t.primary else t.textSecondary

    val shape = if (prominent) RoundedCornerShape(20.dp) else RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = bg,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
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
                    style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
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
        text = "VoltHome © 2025 • $version",
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