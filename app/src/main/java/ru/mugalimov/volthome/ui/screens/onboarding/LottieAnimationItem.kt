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
    animationPath: String,
    isPlaying: Boolean,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(animationPath)
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isPlaying,
        restartOnPlay = false,
        speed = 1f,
        iterations = 1
    )

    // Отслеживаем завершение анимации
    LaunchedEffect(progress) {
        if (progress >= 0.999f) { // Учитываем возможные ошибки округления
            onAnimationEnd()
        }
    }

    LottieAnimation(
        composition = composition,
        progress = progress,
        modifier = modifier.fillMaxSize()
    )
}
