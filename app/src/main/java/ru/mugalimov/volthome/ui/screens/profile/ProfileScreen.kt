package ru.mugalimov.volthome.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProfileViewModel
import ru.mugalimov.volthome.ui.components.VhErrorState
import ru.mugalimov.volthome.ui.components.VhLoadingState
import ru.mugalimov.volthome.ui.components.VhTopBar
import ru.mugalimov.volthome.ui.components.VhSecondaryButton
import ru.mugalimov.volthome.ui.components.VhStatusBadge
import ru.mugalimov.volthome.ui.components.VhInlineNotice

private const val DEFAULT_AVATAR_ASSET = "file:///android_asset/report_pdf/img/logo.png"
private const val DEFAULT_DISPLAY_NAME = "Пользователь"
private const val DEFAULT_EMAIL_PLACEHOLDER = "—"
private const val DEFAULT_PLAN = "free"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authVm: AuthViewModel,
    onBack: () -> Unit = {}
) {
    val profileVm: ProfileViewModel = hiltViewModel()
    val profileState by profileVm.state.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { VhTopBar(title = "Профиль", onBack = onBack) }
    ) { inner ->
        when (val state = profileState) {
            is ProfileViewModel.UiState.Loading -> {
                VhLoadingState(modifier = Modifier.padding(inner), message = "Загружаем профиль…")
            }

            is ProfileViewModel.UiState.Error -> {
                VhErrorState(
                    modifier = Modifier.padding(inner),
                    message = state.message,
                    onRetry = profileVm::refresh
                )
            }

            is ProfileViewModel.UiState.Data -> {
                val me = state.me

                // Защищаем UI от пустых значений локального профиля.
                val safeDisplayName = me.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_DISPLAY_NAME

                val safeEmail = me.email
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_EMAIL_PLACEHOLDER

                val safeAvatarUrl = me.avatarUrl
                    ?.takeIf { it.isNotBlank() }

                val safePlan = me.plan
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_PLAN
                val isGuest = safeEmail == DEFAULT_EMAIL_PLACEHOLDER

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val avatarModifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)

                                AsyncImage(
                                    model = safeAvatarUrl ?: DEFAULT_AVATAR_ASSET,
                                    contentDescription = "Аватар пользователя",
                                    contentScale = if (safeAvatarUrl == null) {
                                        ContentScale.Fit
                                    } else {
                                        ContentScale.Crop
                                    },
                                    modifier = avatarModifier
                                )

                                Spacer(Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isGuest) "Гостевой режим" else safeDisplayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Text(
                                        text = if (isGuest) {
                                            "Работа без обязательной авторизации"
                                        } else {
                                            safeEmail
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    PlanChip(
                                        plan = safePlan,
                                        untilEpochSeconds = me.planUntilEpochSeconds
                                    )
                                }
                            }
                        }
                    }

                    item {
                        VhInlineNotice(
                            title = "Данные хранятся на устройстве",
                            description = "Проекты и расчёты доступны без собственного сервера ВольтХом.",
                            icon = Icons.Rounded.Storage
                        )
                    }

                    item {
                        VhSecondaryButton(
                            text = if (isGuest) "Войти через Яндекс ID" else "Выйти из аккаунта",
                            onClick = {
                                scope.launch { authVm.signOut() }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanChip(
    plan: String,
    untilEpochSeconds: Long?
) {
    // Дополнительная защита: даже если кто-то позже вызовет PlanChip напрямую с пустым plan,
    // чип не уронит экран и покажет безопасный Free.
    val normalizedPlan = plan
        .takeIf { it.isNotBlank() }
        ?: DEFAULT_PLAN

    val planTitle = when (normalizedPlan.lowercase()) {
        "free" -> "Free"
        "pro" -> "PRO"
        else -> normalizedPlan
    }

    val untilStr = untilEpochSeconds
        ?.takeIf { it > 0 }
        ?.let {
            Instant.ofEpochSecond(it)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        }

    val label = buildString {
        append(planTitle)

        if (!untilStr.isNullOrBlank()) {
            append(" • до ")
            append(untilStr)
        }
    }

    VhStatusBadge(
        text = label.uppercase(),
        emphasized = normalizedPlan.equals("pro", ignoreCase = true)
    )
}
