package ru.mugalimov.volthome.ui.screens

import MainScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.mugalimov.volthome.ui.components.ErrorScreen
import ru.mugalimov.volthome.ui.components.FullScreenLoader
import ru.mugalimov.volthome.ui.screens.onboarding.OnboardingScreen
import ru.mugalimov.volthome.ui.viewmodel.OnboardingViewModel
import ru.mugalimov.volthome.ui.viewmodel.OnboardingViewModelFactory

@Composable
fun MainAppEntryPoint() {
    val viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModelFactory(LocalContext.current)
    )
    val uiState by viewModel.uiState.collectAsState()

    // Список анимаций для онбординга (пути в папке assets)
    val onboardingAnimations = remember {
        listOf(
            "lottie/1-2.json",
            "lottie/1-3.json"
        )
    }

    when {
        uiState.isLoading -> FullScreenLoader()
        uiState.error != null -> ErrorScreen(uiState.error!!)
        uiState.showOnboarding -> OnboardingScreen(
            onComplete = viewModel::completeOnboarding,
            animationResources = onboardingAnimations
        )
        else -> MainScreen()
    }
}

