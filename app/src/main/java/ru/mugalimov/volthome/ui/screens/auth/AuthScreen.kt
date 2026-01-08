package ru.mugalimov.volthome.ui.screens.auth

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.legal.LegalUrls
import ru.mugalimov.volthome.ui.screens.welcome.openDocument
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import kotlin.math.floor
import androidx.compose.ui.graphics.Color

private object AuthUiDimens {
    val ScreenHorizontalPadding = 24.dp

    // Структурные отступы панели (не "воздух", а layout)
    val TopPadding = 20.dp
    val HeaderToContent = 16.dp
    val TitleSubtitleGap = 4.dp

    // Внутри блока согласий
    val ConsentsSpacing = 8.dp
    val AfterConsentsToLinks = 6.dp

    // Ссылки -> CTA (сильнее разделяем документы и кнопку)
    val LinksToButton = 20.dp

    // “Сухие” радиусы вместо пухлых
    val PanelCorner = 10.dp
    val ProviderCorner = 12.dp

    // Типографика вторичных блоков (согласия/юридика)
    const val ConsentTextAlpha = 0.82f
    const val LegalTextAlpha = 0.62f
    const val LegalSeparatorAlpha = 0.45f

    // Прочее
    val ErrorTop = 10.dp
    val BottomPadding = 20.dp

    // Фон-среда (микроконтраст, сетка, шум)
    const val GridAlpha = 0.05f
    const val GridBoldAlpha = 0.08f
    const val NoiseAlpha = 0.035f
    val GridStep = 28.dp
    val GridBoldStep = 140.dp
}

@Composable
fun AuthScreen(
    sdk: YandexAuthSdk,
    onSuccess: () -> Unit
) {
    val vm: AuthViewModel = hiltViewModel()

    val state by vm.state.collectAsState()
    val consentState by vm.consentState.collectAsState()
    val context = LocalContext.current

    val launcher =
        rememberLauncherForActivityResult(contract = sdk.contract) { result: YandexAuthResult ->
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
        }

    LaunchedEffect(Unit) { vm.bootstrap() }
    LaunchedEffect(state) { if (state is AuthViewModel.State.Success) onSuccess() }

    // options создаём централизованно (через репозиторий внутри VM)
    val loginOptions = remember { vm.loginOptions() }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .techBackground()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = AuthUiDimens.ScreenHorizontalPadding,
                        end = AuthUiDimens.ScreenHorizontalPadding,
                        top = AuthUiDimens.TopPadding,
                        bottom = AuthUiDimens.BottomPadding
                    ),
                horizontalAlignment = Alignment.Start
            ) {

                // Панельный layout: нет "воздуха сверху", есть структурный отступ уже в padding Column

                // Заголовок — якорь
                Text(
                    text = "VoltHome",
                    style = MaterialTheme.typography.titleLarge.copy(
                        letterSpacing = 0.sp,
                        lineHeight = 22.sp
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(AuthUiDimens.TitleSubtitleGap))

                // Подзаголовок — вторичный
                Text(
                    text = "Расчёт электрики",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 0.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AuthUiDimens.ConsentTextAlpha),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(AuthUiDimens.HeaderToContent))

                // Чекбоксы — рабочая зона. Без карточек.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                AuthUiDimens.PanelCorner
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(AuthUiDimens.ConsentsSpacing)
                ) {
                    ConsentRow(
                        checked = consentState.termsAccepted,
                        text = "Я принимаю условия использования",
                        onToggle = { vm.onTermsAcceptanceChanged(!consentState.termsAccepted) }
                    )

                    ConsentRow(
                        checked = consentState.pdConsentAccepted,
                        text = "Я даю согласие на обработку персональных данных",
                        onToggle = { vm.onPdConsentAcceptanceChanged(!consentState.pdConsentAccepted) }
                    )

                    Spacer(Modifier.height(AuthUiDimens.AfterConsentsToLinks))

                    // Одна строка ссылок — визуально относится к чекбоксам
                    LegalInlineLinks(
                        modifier = Modifier.fillMaxWidth(),
                        onAgreement = {
                            context.openDocument(
                                webUrl = LegalUrls.AGREEMENT,
                                localAssetPath = "documents/user_agreement.html"
                            )
                        },
                        onPdConsent = {
                            context.openDocument(
                                webUrl = LegalUrls.PD_CONSENT,
                                localAssetPath = "documents/pd_consent.html"
                            )
                        },
                        onPrivacy = {
                            context.openDocument(
                                webUrl = LegalUrls.PRIVACY,
                                localAssetPath = "documents/privacy_policy.html"
                            )
                        }
                    )
                }





                Spacer(Modifier.height(AuthUiDimens.LinksToButton))

                // Главный CTA (приглушен НЕ цветом, а весом)
                YandexSignInButton(
                    enabled = consentState.canContinue,
                    loading = consentState.isLoading
                ) {
                    vm.startLogin()
                    launcher.launch(loginOptions)
                }

                (state as? AuthViewModel.State.Error)?.let { err ->
                    Spacer(Modifier.height(AuthUiDimens.ErrorTop))
                    Text(
                        text = err.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }


            }
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    text: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(
                    width = 1.dp,
                    color = if (checked)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
                .background(
                    color = if (checked)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                )
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 0.sp,
                lineHeight = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AuthUiDimens.ConsentTextAlpha),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LegalInlineLinks(
    modifier: Modifier = Modifier,
    onAgreement: () -> Unit,
    onPdConsent: () -> Unit,
    onPrivacy: () -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AuthUiDimens.LegalTextAlpha)
    val separatorColor =
        MaterialTheme.colorScheme.onSurface.copy(alpha = AuthUiDimens.LegalSeparatorAlpha)

    val text = buildAnnotatedString {
        fun linkStyle() = SpanStyle(
            color = linkColor,
            // underline убран: юридика не должна быть главным визуальным сигналом
            textDecoration = TextDecoration.None
        )

        fun separatorStyle() = SpanStyle(
            color = separatorColor
        )

        pushStringAnnotation(tag = "AGREEMENT", annotation = "AGREEMENT")
        withStyle(linkStyle()) { append("Пользовательское соглашение") }
        pop()

        withStyle(separatorStyle()) { append(" · ") }

        pushStringAnnotation(tag = "PD", annotation = "PD")
        withStyle(linkStyle()) { append("Согласие на обработку ПДн") }
        pop()

        withStyle(separatorStyle()) { append(" · ") }

        pushStringAnnotation(tag = "PRIVACY", annotation = "PRIVACY")
        withStyle(linkStyle()) { append("Политика конфиденциальности") }
        pop()
    }

    ClickableText(
        text = text,
        style = TextStyle(
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
            fontWeight = MaterialTheme.typography.labelSmall.fontWeight,
            lineHeight = 14.sp,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            color = linkColor
        ),
        modifier = modifier,
        onClick = { offset ->
            when {
                text.getStringAnnotations("AGREEMENT", offset, offset).isNotEmpty() -> onAgreement()
                text.getStringAnnotations("PD", offset, offset).isNotEmpty() -> onPdConsent()
                text.getStringAnnotations("PRIVACY", offset, offset).isNotEmpty() -> onPrivacy()
            }
        }
    )
}

@Composable
private fun YandexSignInButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val effectiveEnabled = enabled && !loading

    // Provider-gateway: нейтральная "плитка" + акцент-полоска.
    val container by animateColorAsState(
        targetValue = if (effectiveEnabled)
            MaterialTheme.colorScheme.surface
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(),
        label = "providerContainer"
    )
    val border by animateColorAsState(
        targetValue = if (effectiveEnabled)
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        animationSpec = spring(),
        label = "providerBorder"
    )
    val accent by animateColorAsState(
        targetValue = if (effectiveEnabled)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
        else
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        animationSpec = spring(),
        label = "providerAccent"
    )
    val titleColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface.copy(alpha = if (effectiveEnabled) 0.92f else 0.70f),
        animationSpec = spring(),
        label = "providerTitle"
    )
    val subtitleColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface.copy(alpha = if (effectiveEnabled) 0.60f else 0.45f),
        animationSpec = spring(),
        label = "providerSubtitle"
    )

    Surface(
        color = container,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AuthUiDimens.ProviderCorner),
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = effectiveEnabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Акцент-полоска: "шлюз/провайдер", а не "наша кнопка"
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .background(
                        accent,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                    )
            )

            Spacer(Modifier.width(12.dp))

            // Лого — отдельный объект (не "иконка внутри кнопки")
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ya_symbol),
                    contentDescription = "Яндекс ID",
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Яндекс ID",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                        lineHeight = 18.sp
                    ),
                    color = titleColor
                )
                Text(
                    text = if (loading) "Выполняется вход…" else "Официальный безопасный вход",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.sp,
                        lineHeight = 14.sp
                    ),
                    color = subtitleColor
                )
            }

            if (loading) {
                CircularProgressIndicator(
                    color = titleColor,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                // Стрелка как “переход в провайдер”, а не “кнопка”
                Text(
                    text = "→",
                    style = MaterialTheme.typography.titleMedium,
                    color = subtitleColor
                )
            }
        }
    }
}

@Composable
private fun Modifier.techBackground(): Modifier {
    // MaterialTheme — composable, поэтому берём цвета здесь.
    val scheme = MaterialTheme.colorScheme
    val gridColor = scheme.onSurface.copy(alpha = AuthUiDimens.GridAlpha)
    val gridBoldColor = scheme.onSurface.copy(alpha = AuthUiDimens.GridBoldAlpha)
    val noiseColor = scheme.onSurface.copy(alpha = AuthUiDimens.NoiseAlpha)

    return this.techBackground(
        gridColor = gridColor,
        gridBoldColor = gridBoldColor,
        noiseColor = noiseColor
    )
}

private fun Modifier.techBackground(
    gridColor: Color,
    gridBoldColor: Color,
    noiseColor: Color
): Modifier = this.drawWithCache {
    val stepPx = AuthUiDimens.GridStep.toPx()
    val boldStepPx = AuthUiDimens.GridBoldStep.toPx()

    // Дешёвый детерминированный “шум” без random(): псевдо-шахматка по сетке.
// Это не красиво, а “техническая текстура”.
    fun noiseAt(x: Int, y: Int): Boolean = ((x * 73856093) xor (y * 19349663)) and 7 == 0

    onDrawBehind {
        // 1) Тонкая сетка
        run {
            val cols = floor(size.width / stepPx).toInt()
            val rows = floor(size.height / stepPx).toInt()

            for (c in 0..cols) {
                val x = c * stepPx
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }
            for (r in 0..rows) {
                val y = r * stepPx
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }
        }

        // 2) “Жирные” линии реже — создают ощущение панелей/плоскостей
        run {
            val cols = floor(size.width / boldStepPx).toInt()
            val rows = floor(size.height / boldStepPx).toInt()

            for (c in 0..cols) {
                val x = c * boldStepPx
                drawLine(
                    color = gridBoldColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }
            for (r in 0..rows) {
                val y = r * boldStepPx
                drawLine(
                    color = gridBoldColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }
        }

        // 3) Шум (очень слабый): редкие точки/пиксели по шагу
        run {
            val nStep = 10f
            val cols = floor(size.width / nStep).toInt()
            val rows = floor(size.height / nStep).toInt()

            for (c in 0..cols) {
                for (r in 0..rows) {
                    if (noiseAt(c, r)) {
                        val x = c * nStep
                        val y = r * nStep
                        drawRect(
                            color = noiseColor,
                            topLeft = Offset(x, y),
                            size = Size(1.5f, 1.5f)
                        )
                    }
                }
            }
        }
    }
}

