package ru.mugalimov.volthome.ui.screens.auth

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import androidx.compose.runtime.collectAsState

@Composable
fun AuthScreen(
    sdk: YandexAuthSdk,
    onSuccess: () -> Unit
) {
    val vm: AuthViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    var isLaunching by remember { mutableStateOf(false) }

    // ВАЖНО: используем контракт SDK (3.1.3) — БЕЗ deeplink-колбэков
    val launcher = rememberLauncherForActivityResult(
        contract = sdk.contract
    ) { result: YandexAuthResult ->
        val tag = "YA_AUTH"
        when (result) {
            is YandexAuthResult.Success -> Log.d(tag, "Auth result = Success")
            is YandexAuthResult.Failure -> Log.d(
                tag,
                "Auth result = Failure: ${result.exception.javaClass.simpleName}"
            )
            YandexAuthResult.Cancelled -> Log.d(tag, "Auth result = Cancelled")
        }
        vm.handleResult(result)
        isLaunching = false
    }

    LaunchedEffect(Unit) {
        vm.bootstrap()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text("Вход в VoltHome", style = MaterialTheme.typography.headlineSmall)

            when (val s = state) {
                is AuthViewModel.State.Idle -> {
                    Button(onClick = {
                        Log.d("YA_AUTH", "Launch login via sdk.contract")
                        vm.startLogin()
                        isLaunching = true
                        launcher.launch(YandexAuthLoginOptions())
                    }) { Text("Войти с Яндекс ID") }
                }

                is AuthViewModel.State.Loading -> {
                    CircularProgressIndicator()
                }

                is AuthViewModel.State.Error -> {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = {
                        Log.d("YA_AUTH", "Retry login")
                        vm.startLogin()
                        isLaunching = true
                        launcher.launch(YandexAuthLoginOptions())
                    }) { Text("Повторить") }
                }

                is AuthViewModel.State.Success -> onSuccess()
            }
        }

        if (isLaunching) {
            CircularProgressIndicator()
        }
    }
}