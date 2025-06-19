package ru.mugalimov.volthome.ui.screens.onboarding


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    animationResources: List<String>
) {
    // Состояние пейджера для управления карасульею
    val pagerState = rememberPagerState(pageCount = { animationResources.size })

    // Область видимости для корутин (для анимации)
    val coroutineScope = rememberCoroutineScope()

    // 1. Состояние для контроля воспроизведения анимаций
    val animationPlayStates = remember {
        animationResources.map { mutableStateOf(true) }
    }

    // Автозапуск первой анимации
    LaunchedEffect(Unit) {
        animationPlayStates[0].value = true
    }

//    // Флаг для управления автопрокруткой
//    val autoScrollEnabled by remember { mutableStateOf(true) }

    // Основной контейнер экрана
    Box(modifier = Modifier.fillMaxSize()) {

        /** Горизонтальный пейджер (карусель)
         * count - количество страниц
         * state - состояние прокрутки
         * userScrollEnabled - разрешаем ручную прокрутку свайпом
         **/
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageContent = { pageIndex ->
                // Компонент векторной анимации для текущего шага
                LottieAnimationItem(
                    animationPath = animationResources[pageIndex],
                    isPlaying = animationPlayStates[pageIndex].value,
                    onAnimationEnd = {
                        // Для не-последних страниц: переходим дальше с задержкой
                        if (pageIndex < animationResources.lastIndex) {
                            coroutineScope.launch {
                                delay(300) // Задержка для финального кадра
                                pagerState.animateScrollToPage(pageIndex + 1)
                            }
                        }
                    }
                )
            }
        )

        CustomPageIndicator(
            pageCount = animationResources.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        // Кнопка пропустить вызывает onComplete при нажатии
        TextButton(
            onClick = onComplete,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Text("Пропустить")
        }

        // Кнопка "Далее" (только не на последней странице)
        if (pagerState.currentPage < animationResources.lastIndex) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Text("Далее")
            }
        }
    }

    // Обработчик смены страниц
    LaunchedEffect(pagerState.currentPage) {
        // Выключаем все анимации
        animationPlayStates.forEach { it.value = false }

        // Включаем только текущую
        animationPlayStates[pagerState.currentPage].value = true

        // Автозавершение для последней страницы
        if (pagerState.currentPage == animationResources.lastIndex) {
            coroutineScope.launch {
                delay(2500) // Ждём завершения анимации
                onComplete()
            }
        }
    }
}

@Composable
fun CustomPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}