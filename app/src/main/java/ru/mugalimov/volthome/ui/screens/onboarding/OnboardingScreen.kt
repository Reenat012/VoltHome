package ru.mugalimov.volthome.ui.screens.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.ui.components.VhPrimaryButton

/**
 * Короткое знакомство с продуктом. Текст остаётся нативным Compose-контентом,
 * а PNG используется только как иллюстрация — это сохраняет доступность и
 * корректную вёрстку на экранах разного размера.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    finalActionLabel: String = "Создать первый проект",
    modifier: Modifier = Modifier
) {
    val pages = onboardingPages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex
    val t = VhColors.tokens

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.bg)
            .safeDrawingPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text("Назад", color = t.textSecondary)
                }
            }

            TextButton(
                onClick = onComplete,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text("Пропустить", color = t.textSecondary)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) { pageIndex ->
            OnboardingPage(
                page = pages[pageIndex],
                modifier = Modifier.fillMaxSize()
            )
        }

        PageIndicator(
            pageCount = pages.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp, bottom = 20.dp)
        )

        VhPrimaryButton(
            text = if (isLastPage) finalActionLabel else "Далее",
            onClick = {
                if (isLastPage) {
                    onComplete()
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun OnboardingPage(
    page: OnboardingPageModel,
    modifier: Modifier = Modifier
) {
    val t = VhColors.tokens

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(page.imageRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp)
        )

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = t.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = t.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    val t = VhColors.tokens

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) t.primary else t.divider
                    )
            )
        }
    }
}

private data class OnboardingPageModel(
    @param:DrawableRes val imageRes: Int,
    val title: String,
    val description: String
)

private val onboardingPages = listOf(
    OnboardingPageModel(
        imageRes = R.drawable.onboarding_rooms,
        title = "Соберите объект по помещениям",
        description = "Добавьте комнаты и электроприборы — ВольтХом рассчитает нагрузки и сформирует структуру проекта."
    ),
    OnboardingPageModel(
        imageRes = R.drawable.onboarding_protection,
        title = "Получите группы и защиту",
        description = "Приложение сформирует линии, предложит автоматы и УЗО и объяснит принятые решения."
    ),
    OnboardingPageModel(
        imageRes = R.drawable.onboarding_panel,
        title = "Соберите и проверьте щит до монтажа",
        description = "Расположите аппараты на DIN-рейках, выберите совместимые модели и оцените ориентировочную стоимость."
    )
)
