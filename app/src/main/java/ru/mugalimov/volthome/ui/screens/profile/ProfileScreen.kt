package ru.mugalimov.volthome.ui.screens.profile

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authVm: AuthViewModel,
    onBack: () -> Unit = {}
) {
    val profileVm: ProfileViewModel = hiltViewModel()
    val profileState by profileVm.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { profileVm.refresh() }

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

        when (val s = profileState) {
            is ProfileViewModel.UiState.Loading -> {
                Text("Загружаем профиль…")
            }

            is ProfileViewModel.UiState.Error -> {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is ProfileViewModel.UiState.Data -> {
                val me = s.me

                Text(
                    "Имя: ${me.displayName}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))

                Text("E-mail: ${me.email ?: "—"}")

                if (me.plan.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))

                    val untilStr = me.planUntilEpochSeconds
                        ?.takeIf { it > 0 }
                        ?.let {
                            java.time.Instant.ofEpochSecond(it)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                                .toString()
                        }
                        ?: ""

                    Text("Подписка: ${me.plan}${if (untilStr.isNotEmpty()) " (до $untilStr)" else ""}")
                }

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { scope.launch { authVm.signOut() } }
                ) { Text("Выйти") }
            }
        }
    }
}