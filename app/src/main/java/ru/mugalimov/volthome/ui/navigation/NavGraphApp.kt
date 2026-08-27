package ru.mugalimov.volthome.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ru.mugalimov.volthome.ui.screens.about.SettingsScreen
import ru.mugalimov.volthome.ui.screens.algoritm_about.AlgorithmExplanationScreen
import ru.mugalimov.volthome.ui.screens.explication.ExplicationScreen
import ru.mugalimov.volthome.ui.screens.loads.PhaseLoadScreen
import ru.mugalimov.volthome.ui.screens.onboarding.OnboardingScreen
import ru.mugalimov.volthome.ui.screens.panel.PanelVisualizationScreen
import ru.mugalimov.volthome.ui.screens.profile.ProfileScreen
import ru.mugalimov.volthome.ui.screens.project_wizard.ProjectWizardScreen
import ru.mugalimov.volthome.ui.screens.reconfiguration.ProjectReconfigurationScreen
import ru.mugalimov.volthome.ui.screens.report_preview.ReportPreviewScreen
import ru.mugalimov.volthome.ui.screens.room.RoomDetailScreen
import ru.mugalimov.volthome.ui.screens.rooms.RoomsScreen
import ru.mugalimov.volthome.ui.screens.rooms.AddRoomScreen
import ru.mugalimov.volthome.ui.screens.subscription.VoltHomeProScreen
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.core.analytics.AnalyticsRuntimeEntryPoint
import ru.mugalimov.volthome.core.analytics.PaywallSource
import dagger.hilt.android.EntryPointAccessors
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.paywall.PaywallBus
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProjectsViewModel
import ru.mugalimov.volthome.ui.viewmodel.RoomDetailViewModel

/**
 * Внутренний граф основного приложения.
 */
@Composable
fun NavGraphApp(
    navController: NavHostController,
    modifier: Modifier,
    padding: PaddingValues,
    showOnboarding: () -> Unit,
    authVm: AuthViewModel,
    paywallBus: PaywallBus,
    projectName: String
) {
    val appContext = LocalContext.current.applicationContext
    val purchaseAnalyticsContext = remember(appContext) {
        EntryPointAccessors.fromApplication(
            appContext,
            AnalyticsRuntimeEntryPoint::class.java
        ).purchaseAnalyticsContext()
    }
    // Одноразовый reset стека на входе в граф.
    var didReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!didReset) {
            runCatching {
                navController.navigate(Screens.RoomsList.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            didReset = true
        }
    }

    // Реакция на смену активного проекта.
    val projectsVm: ProjectsViewModel = hiltViewModel()
    val activeProjectId by projectsVm.activeProjectId.collectAsState(initial = null)
    val projectsFacts by projectsVm.onboardingFacts.collectAsState()
    var didAutoOpenWizard by remember { mutableStateOf(false) }

    // Роуты нижней навигации, на которых можно оставаться при смене проекта.
    val bottomRoutes = remember {
        setOf(
            Screens.RoomsList.route,
            Screens.LoadsScreen.route,
            Screens.ExplicationScreen.route,
            Screens.PanelVisualizationScreen.route,
            Screens.ProjectWizard.route
        )
    }

    // После первого онбординга пустое приложение сразу продолжает сценарий
    // созданием реального проекта, а не показывает пустой список комнат.
    LaunchedEffect(didReset, projectsFacts) {
        if (
            didReset &&
            !didAutoOpenWizard &&
            !projectsFacts.isLoading &&
            projectsFacts.projectsCount == 0 &&
            navController.currentDestination?.route == Screens.RoomsList.route
        ) {
            didAutoOpenWizard = true
            navController.navigate(Screens.ProjectWizard.route) { launchSingleTop = true }
        }
    }

    LaunchedEffect(activeProjectId) {
        if (activeProjectId != null) {
            projectsVm.synchronizeActiveProjectSettings(activeProjectId!!)
            val current = navController.currentDestination?.route
            if (current !in bottomRoutes) {
                val popped = navController.popBackStack(
                    route = Screens.RoomsList.route,
                    inclusive = false
                )

                if (!popped) {
                    navController.navigate(Screens.RoomsList.route) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screens.RoomsList.route,
        modifier = modifier
    ) {
        composable(route = Screens.RoomsList.route) {
            RoomsScreen(
                onAddRoom = {
                    navController.navigate(Screens.AddRoom.route) {
                        launchSingleTop = true
                    }
                },
                onClickRoom = { roomId ->
                    navController.navigate(Screens.RoomDetailScreen.createRoute(roomId)) {
                        launchSingleTop = true
                    }
                },
                onReconfigureProject = {
                    navController.navigate(Screens.ProjectReconfiguration.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(route = Screens.ProjectReconfiguration.route) {
            ProjectReconfigurationScreen(onBack = { navController.popBackStack() })
        }

        composable(route = Screens.AddRoom.route) {
            AddRoomScreen(
                onBack = { navController.popBackStack() },
                onCreated = { roomId ->
                    navController.navigate(Screens.RoomDetailScreen.createRoute(roomId)) {
                        popUpTo(Screens.AddRoom.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(route = Screens.ProjectWizard.route) {
            fun open(route: String) {
                navController.navigate(route) {
                    popUpTo(Screens.ProjectWizard.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
            ProjectWizardScreen(
                onClose = { navController.popBackStack() },
                onOpenRooms = { open(Screens.RoomsList.route) },
                onOpenLines = { open(Screens.ExplicationScreen.route) },
                onOpenPanel = { open(Screens.PanelVisualizationScreen.route) }
            )
        }

        composable(route = Screens.LoadsScreen.route) {
            PhaseLoadScreen()
        }

        composable(route = Screens.ExplicationScreen.route) {
            ExplicationScreen(
                navController = navController,
                projectName = projectName
            )
        }

        composable(route = Screens.PanelVisualizationScreen.route) {
            val canOpenPanelVisualization =
                LocalUserPlan.current.capabilities.panelVisualization
            PanelVisualizationScreen(
                fullAccess = canOpenPanelVisualization,
                onUnlockClick = {
                    paywallBus.request(
                        feature = ProFeature.PANEL_VISUALIZATION,
                        source = PaywallSource.BOARD_PREVIEW
                    )
                },
                onManualUnlockClick = {
                    paywallBus.request(
                        feature = ProFeature.PANEL_VISUALIZATION,
                        source = PaywallSource.MANUAL_BOARD
                    )
                }
            )
        }

        composable(
            route = Screens.RoomDetailScreen.route,
            arguments = listOf(navArgument("roomId") { type = NavType.LongType })
        ) {
            val vm: RoomDetailViewModel = hiltViewModel(it)
            RoomDetailScreen(
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screens.SettingsScreen.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onShowOnboarding = showOnboarding,
                onOpenAlgorithm = {
                    navController.navigate(Screens.AlgorithmExplanationScreen.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screens.OnBoardingScreen.route) {
            OnboardingScreen(
                onComplete = { navController.popBackStack() },
                finalActionLabel = "Вернуться в приложение"
            )
        }

        composable(Screens.AlgorithmExplanationScreen.route) {
            AlgorithmExplanationScreen(navController)
        }

        composable(Screens.PhaseLoadScreen.route) {
            PhaseLoadScreen()
        }

        composable(Screens.ProfileScreen.route) {
            ProfileScreen(
                authVm = authVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screens.SubscriptionScreen.route) {
            VoltHomeProScreen()
        }

        composable(route = Screens.ReportPreview.route) {
            val prevEntry = navController.previousBackStackEntry
            if (prevEntry == null) {
                ReportPreviewScreen(
                    html = "",
                    showProHint = !LocalUserPlan.current.capabilities.pdfExport,
                    onUnlockClick = {
                        purchaseAnalyticsContext.begin(PaywallSource.REPORT_PREVIEW)
                        navController.navigate(Screens.SubscriptionScreen.route)
                    }
                )
                return@composable
            }

            var fixedHtml by remember(prevEntry) { mutableStateOf<String?>(null) }
            if (fixedHtml == null) {
                fixedHtml = prevEntry.savedStateHandle
                    .get<String>(ReportPreviewNav.HTML_KEY)
                    .orEmpty()
                    .takeIf { it.isNotBlank() }

                prevEntry.savedStateHandle.remove<String>(ReportPreviewNav.HTML_KEY)
            }

            ReportPreviewScreen(
                html = fixedHtml.orEmpty(),
                showProHint = !LocalUserPlan.current.capabilities.pdfExport,
                onUnlockClick = {
                    purchaseAnalyticsContext.begin(PaywallSource.REPORT_PREVIEW)
                    navController.navigate(Screens.SubscriptionScreen.route)
                }
            )
        }
    }
}
