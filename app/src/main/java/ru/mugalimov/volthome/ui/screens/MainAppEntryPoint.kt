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