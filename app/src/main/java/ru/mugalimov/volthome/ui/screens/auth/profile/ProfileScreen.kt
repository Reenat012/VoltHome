package ru.mugalimov.volthome.ui.screens.auth.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit = {}
) {
    val authVm: AuthViewModel = hiltViewModel()
    val authState by authVm.state.collectAsState()

    val profileVm: ProfileViewModel = hiltViewModel()
    val profileState by profileVm.state.collectAsState()

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        authVm.bootstrap()
        profileVm.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        TopAppBar(
            title = { Text("Профиль") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        Spacer(Modifier.height(12.dp))

        when (val s = authState) {
            is AuthViewModel.State.Success -> {
                // Данные пользователя приходят с твоего сервера (/profile/me)
                when (val ps = profileState) {
                    is ProfileViewModel.UiState.Loading -> {
                        Text("Загружаем профиль…")
                        Spacer(Modifier.height(8.dp))
                    }
                    is ProfileViewModel.UiState.Error -> {
                        Text(ps.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { profileVm.refresh() }) {
                            Text("Повторить")
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    is ProfileViewModel.UiState.Data -> {
                        val me = ps.me
                        Text(
                            "Имя: ${me.displayName}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        me.email?.let { Text("Email: $it") }

                        val planLine = buildString {
                            append("Тариф: ${me.plan}")
                            me.planUntilEpochSeconds?.let { until ->
                                val dateStr = SimpleDateFormat(
                                    "dd.MM.yyyy",
                                    Locale.getDefault()
                                ).format(Date(until * 1000))
                                append(" (до $dateStr)")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(planLine)

                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { profileVm.refresh() }) {
                            Text("Обновить профиль")
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // Краткий статус сессии без раскрытия токена/деталей безопасности
                Text(
                    "Статус: вошёл через серверную сессию",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))

                Button(onClick = { scope.launch { authVm.signOut() } }) {
                    Text("Выйти")
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "После выхода попадёшь на экран входа. Локальные данные (комнаты, устройства) сохраняются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is AuthViewModel.State.Loading -> {
                Text("Проверяем сессию…")
            }

            is AuthViewModel.State.Error,
            AuthViewModel.State.Idle -> {
                Text(
                    "Ты не вошёл в аккаунт. Перейди на экран входа.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}