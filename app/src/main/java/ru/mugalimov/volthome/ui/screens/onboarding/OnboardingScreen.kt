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

    // Контекст для доступа к ресурсам
    val context = LocalContext.current

    // Область видимости для корутин (для анимации)
    val coroutineScope = rememberCoroutineScope()

//     Состояния для управления анимациями
//    val animationStates = remember(animationResources) {
//        animationResources.map { mutableStateOf(AnimationState()) }
//    }

    // Инициализируем состояния для каждой анимации с разными скоростями
    val animationStates = remember {
        animationResources.mapIndexed { index, _ ->
            mutableStateOf(
                AnimationState(
                    // Замедляем ТОЛЬКО вторую анимацию (индекс 1)
                    speed = if (index == 1) 0.2f else 1.0f
                )
            )
        }
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
                    state = animationStates[pageIndex],
                    onAnimationEnd = {
                        // Автопрокрутка при завершении анимации (кроме последней)
                        if (pageIndex < animationResources.lastIndex) {
                            coroutineScope.launch {
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

    // Обработка событий при смене страницы
    LaunchedEffect(pagerState.currentPage) {
        // Перезапускаем анимацию при переходе на страницу
        animationStates[pagerState.currentPage].value = AnimationState(
            isPlaying = true,
            restartOnPlay = true
        )

        // Завершение онбординга после последней анимации
        if (pagerState.currentPage == animationResources.lastIndex) {
            // Даем время для завершения анимации
            delay(1500)
            onComplete()
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