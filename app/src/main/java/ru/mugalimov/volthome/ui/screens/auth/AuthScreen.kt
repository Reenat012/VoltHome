package ru.mugalimov.volthome.ui.screens.auth

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.legal.LegalUrls
import ru.mugalimov.volthome.ui.screens.welcome.openDocument
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    sdk: YandexAuthSdk,
    onSuccess: () -> Unit
) {
    val vm: AuthViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Контракт Яндекс ID
    val launcher = rememberLauncherForActivityResult(contract = sdk.contract) { result: YandexAuthResult ->
        val tag = "YA_AUTH"
        when (result) {
            is YandexAuthResult.Success -> Log.d(tag, "Auth result = Success")
            is YandexAuthResult.Failure -> Log.d(tag, "Auth result = Failure: ${result.exception.javaClass.simpleName}")
            YandexAuthResult.Cancelled -> Log.d(tag, "Auth result = Cancelled")
        }
        vm.handleResult(result)
    }

    LaunchedEffect(Unit) { vm.bootstrap() }
    LaunchedEffect(state) { if (state is AuthViewModel.State.Success) onSuccess() }

    var accepted by remember { mutableStateOf(false) }
    val isLoading = state is AuthViewModel.State.Loading

    // ---------- ОДНОЦВЕТНЫЙ ФОН ----------
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = Color.White // 🔹 Совпадает с фоном логотипа
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                // ---------- ЛОГОТИП ----------
                Image(
                    painter = rememberAsyncImagePainter("file:///android_asset/report_pdf/img/logo.png"),
                    contentDescription = "Логотип VoltHome",
                    modifier = Modifier
                        .fillMaxWidth(0.36f)
                        .aspectRatio(1f)
                        .padding(top = 24.dp, bottom = 0.dp)
                )

                // ---------- ПОДЗАГОЛОВОК ----------
                Text(
                    text = "Вход в VoltHome",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                // ---------- КАРТОЧКА СОГЛАСИЯ ----------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = accepted,
                                onCheckedChange = { accepted = it },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Я принимаю условия использования",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        DocumentLink(
                            icon = Icons.Default.Description,
                            text = "Пользовательское соглашение"
                        ) {
                            context.openDocument(
                                webUrl = LegalUrls.AGREEMENT,
                                localAssetPath = "documents/user_agreement.html"
                            )
                        }

                        DocumentLink(
                            icon = Icons.Default.PrivacyTip,
                            text = "Политика конфиденциальности"
                        ) {
                            context.openDocument(
                                webUrl = LegalUrls.PRIVACY,
                                localAssetPath = "documents/privacy_policy.html"
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---------- КНОПКА ВХОДА ----------
                YandexSignInButton(
                    enabled = accepted && !isLoading,
                    loading = isLoading
                ) {
                    vm.startLogin()
                    launcher.launch(YandexAuthLoginOptions())
                }

                // ---------- ОШИБКА ----------
                (state as? AuthViewModel.State.Error)?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Кнопка «Войти с Яндекс ID» со спиннером и иконкой, с плавными состояниями. */
@Composable
private fun YandexSignInButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(),
        label = "btnContainer"
    )
    val content by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(),
        label = "btnContent"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = content,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 10.dp)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ya_symbol),
                    contentDescription = "Яндекс ID",
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 10.dp)
                )
            }

            Text(
                text = if (loading) "Входим…" else "Войти с Яндекс ID",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** Ссылка на документ с иконкой. */
@Composable
private fun DocumentLink(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}