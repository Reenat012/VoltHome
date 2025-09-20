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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authVm: AuthViewModel,
    onBack: () -> Unit = {}
) {
    val authState by authVm.state.collectAsState()
    val scope = rememberCoroutineScope()

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
                // У нас есть только session; полей user нет — показываем общий статус.
                Text(
                    "Статус: авторизован (серверная сессия активна)",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch { authVm.signOut() }
                    }
                ) { Text("Выйти") }
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