package ru.mugalimov.volthome.ui.screens.onboarding

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * 6. Компонент для отображения анимации Lottie
 */
@Composable
fun LottieAnimationItem(
    animationPath: String,     // Путь к JSON-файлу в assets
    state: MutableState<AnimationState>,
    onAnimationEnd: () -> Unit  // Коллбек при завершении анимации
) {
    // Загрузка композиции анимации
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(animationPath)
    )

    // Управление прогрессом анимации
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = state.value.isPlaying,
        speed = state.value.speed,
        restartOnPlay = state.value.restartOnPlay,
        iterations = 1
    )

    // Отслеживаем завершение анимации через LaunchedEffect
    LaunchedEffect(progress) {
        if (progress == 1f) { // 1f = анимация завершена
            onAnimationEnd()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Отображение анимации
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize()
        )

        // Кнопка перезапуска анимации
        IconButton(
            onClick = {
                state.value = state.value.copy(
                    isPlaying = true,
                    restartOnPlay = true
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = "Restart animation"
            )
        }
    }
}
