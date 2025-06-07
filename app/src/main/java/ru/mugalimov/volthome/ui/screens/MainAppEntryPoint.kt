import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.mugalimov.volthome.ui.components.ErrorScreen
import ru.mugalimov.volthome.ui.components.FullScreenLoader
import ru.mugalimov.volthome.ui.screens.onboarding.OnboardingScreen
import ru.mugalimov.volthome.ui.viewmodel.OnboardingViewModel
import ru.mugalimov.volthome.ui.viewmodel.OnboardingViewModelFactory


// Добавляем корневые маршруты
sealed class RootScreens(val route: String) {
    object Loading : RootScreens("loading")
    object Onboarding : RootScreens("onboarding")
    object Welcome : RootScreens("welcome")
    object MainApp : RootScreens("main_app")
    object Error : RootScreens("error/{message}") {
        fun createRoute(message: String) = "error/$message"
    }
}

@Composable
fun MainAppEntryPoint() {
    val viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModelFactory(LocalContext.current)
    )
    val uiState by viewModel.uiState.collectAsState()

    val rootNavController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    // Сохраняем состояние первого запуска между рекомпозициями
    var isFirstLaunch by rememberSaveable { mutableStateOf(prefs.getBoolean("first_launch", true)) }

    // Список анимаций для онбординга
    val onboardingAnimations = remember {
        listOf("lottie/1-2.json", "lottie/1-3.json")
    }

    // Управление начальным экраном
    LaunchedEffect(uiState, isFirstLaunch) {
        when {
            uiState.isLoading -> {
                if (rootNavController.currentDestination?.route != RootScreens.Loading.route) {
                    rootNavController.navigate(RootScreens.Loading.route) {
                        popUpTo(0)
                    }
                }
            }
            uiState.error != null -> {
                rootNavController.navigate(RootScreens.Error.createRoute(uiState.error!!)) {
                    popUpTo(0)
                }
            }
            uiState.showOnboarding -> {
                rootNavController.navigate(RootScreens.Onboarding.route) {
                    popUpTo(0)
                }
            }
            isFirstLaunch -> {
                rootNavController.navigate(RootScreens.Welcome.route) {
                    popUpTo(0)
                }
            }
            else -> {
                rootNavController.navigate(RootScreens.MainApp.route) {
                    popUpTo(0)
                }
            }
        }
    }

    // Корневой NavHost
    NavHost(
        navController = rootNavController,
        startDestination = RootScreens.Loading.route
    ) {
        composable(RootScreens.Loading.route) {
            FullScreenLoader()
        }

        composable(RootScreens.Error.route) { backStackEntry ->
            val message = backStackEntry.arguments?.getString("message") ?: "Неизвестная ошибка"
            ErrorScreen(message)
        }

        composable(RootScreens.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    viewModel.completeOnboarding()
                    // После онбординга сразу переходим к проверке соглашения
                    if (isFirstLaunch) {
                        rootNavController.navigate(RootScreens.Welcome.route) {
                            popUpTo(0)
                        }
                    } else {
                        rootNavController.navigate(RootScreens.MainApp.route) {
                            popUpTo(0)
                        }
                    }
                },
                animationResources = onboardingAnimations
            )
        }

        composable(RootScreens.Welcome.route) {
            WelcomeScreen(
                onContinue = {
                    prefs.edit().putBoolean("first_launch", false).apply()
                    isFirstLaunch = false
                    rootNavController.navigate(RootScreens.MainApp.route) {
                        popUpTo(0)
                    }
                }
            )
        }

        composable(RootScreens.MainApp.route) {
            MainApp(rootNavController = rootNavController)
        }
    }
}