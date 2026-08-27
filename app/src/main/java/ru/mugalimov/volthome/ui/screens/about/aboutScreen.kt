import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.core.theme.UiStatus
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.legal.LegalUrls
import ru.mugalimov.volthome.ui.components.VhInlineNotice
import ru.mugalimov.volthome.ui.components.VhListRow
import ru.mugalimov.volthome.ui.components.VhSectionHeader
import ru.mugalimov.volthome.ui.components.VhTopBar
import ru.mugalimov.volthome.ui.screens.welcome.openDocument

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val t = VhColors.tokens

    Scaffold(
        topBar = { VhTopBar(title = "О ВольтХом", subtitle = "Инженерный инструмент", onBack = onBack) },
        containerColor = t.bg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = t.primarySurface,
                    border = BorderStroke(1.dp, t.primary.copy(alpha = 0.38f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(18.dp), color = t.primary, modifier = Modifier.size(58.dp)) {
                            Icon(
                                Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = t.textOnAccent,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("ВольтХом", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Расчёт и компоновка электрического щита", style = MaterialTheme.typography.bodyMedium, color = t.textSecondary)
                            Text(
                                "Версия ${BuildConfig.VERSION_NAME} · сборка ${BuildConfig.VERSION_CODE}",
                                style = MaterialTheme.typography.labelMedium,
                                color = t.primary
                            )
                        }
                    }
                }
            }

            item {
                VhInlineNotice(
                    title = "Локальная работа",
                    description = "Проекты не отправляются на сервер ВольтХом. Системное резервное копирование данных приложения отключено.",
                    status = UiStatus.INFO
                )
            }

            item { VhSectionHeader("Правовая информация", description = "Действующие публичные редакции · версия 3.0") }
            item {
                VhListRow(
                    title = "Пользовательское соглашение",
                    subtitle = "Условия использования инженерных расчётов",
                    icon = Icons.Rounded.Description,
                    onClick = { context.openDocument(LegalUrls.AGREEMENT, "documents/user_agreement.html") }
                )
            }
            item {
                VhListRow(
                    title = "Политика конфиденциальности",
                    subtitle = "Локальные данные, аналитика и внешние сервисы",
                    icon = Icons.Rounded.PrivacyTip,
                    onClick = { context.openDocument(LegalUrls.PRIVACY, "documents/privacy_policy.html") }
                )
            }
            item {
                VhListRow(
                    title = "Согласие для входа через Яндекс ID",
                    subtitle = "Запрашивается только при добровольной авторизации",
                    icon = Icons.Rounded.Fingerprint,
                    onClick = { context.openDocument(LegalUrls.PD_CONSENT, "documents/pd_consent.html") }
                )
            }

            item { VhSectionHeader("Правообладатель") }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = t.surfaceAlt,
                    border = BorderStroke(1.dp, t.divider)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("ИП Мугалимова Нагиля Ерадыльевна", style = MaterialTheme.typography.titleSmall)
                        Text("Архангельская область, г. Мирный, ул. Советская, д. 7, кв. 213", style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
                        Text("nnurkatova@list.ru", style = MaterialTheme.typography.bodySmall, color = t.primary)
                        Text("Реестр операторов ПД: 56-26-022342", style = MaterialTheme.typography.labelSmall, color = t.textMuted)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
