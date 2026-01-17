package ru.mugalimov.volthome.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.domain.model.PlanCapabilities
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.model.ProjectUi
import ru.mugalimov.volthome.ui.model.UserProfileUi
import ru.mugalimov.volthome.ui.navigation.MainBottomNavBar
import ru.mugalimov.volthome.ui.navigation.NavGraphApp
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.paywall.PaywallEntryPoint
import ru.mugalimov.volthome.ui.screens.debug.DebugProPanel
import ru.mugalimov.volthome.ui.screens.start_drawer.AppScaffoldWithDrawer
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProfileViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProjectsViewModel
import ru.mugalimov.volthome.ui.viewmodel.UserPlanViewModel

@Composable
fun MainApp(
    rootNavController: NavHostController,
    authVm: AuthViewModel
) {
    val appNavController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // --- проекты
    val projectsVm: ProjectsViewModel = hiltViewModel()
    val projectsFlow: Flow<List<ProjectUi>> = projectsVm.projectsUi
    val appBarTitle = projectsVm.activeProjectTitle.collectAsState().value

    // --- профиль
    val profileVm: ProfileViewModel = hiltViewModel()

    // --- тариф (free/pro)
    val userPlanVm: UserPlanViewModel = hiltViewModel()
    val userPlan = userPlanVm.plan.collectAsState().value

    // -----------------------------
    // ✅ Глобальный paywall (capabilities-aware)
    // -----------------------------
    val context = LocalContext.current
    val paywallBus = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PaywallEntryPoint::class.java
        ).paywallBus()
    }

    var paywallFeature by remember { mutableStateOf<ProFeature?>(null) }

    LaunchedEffect(paywallBus, userPlan) {
        val caps = userPlan.capabilities
        paywallBus.events.collect { feature ->
            if (isFeatureAllowed(feature, caps)) {
                // already allowed — ignore
                return@collect
            }
            paywallFeature = feature
        }
    }

    // ✅ Guard: если capabilities обновились так, что текущая фича стала доступна — закрываем диалог
    LaunchedEffect(userPlan, paywallFeature) {
        val feature = paywallFeature ?: return@LaunchedEffect
        if (isFeatureAllowed(feature, userPlan.capabilities)) {
            paywallFeature = null
        }
    }

    if (paywallFeature != null) {
        val feature = paywallFeature!!

        when (feature) {
            ProFeature.PHASE_DND_TEASER -> {
                AlertDialog(
                    onDismissRequest = { paywallFeature = null },
                    title = { Text("Ручное управление фазами") },
                    text = {
                        Text(
                            "В PRO можно вручную переносить группы между фазами.\n\n" +
                                    "Это вмешательство в распределение: после каждого переноса сразу меняются токи по фазам и ΔI.\n\n" +
                                    "Используйте ручной режим, чтобы быстро выровнять баланс под реальный сценарий нагрузок."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                paywallFeature = null
                                appNavController.navigate(Screens.SubscriptionScreen.route) {
                                    launchSingleTop = true
                                }
                            }
                        ) { Text("Открыть PRO") }
                    },
                    dismissButton = {
                        TextButton(onClick = { paywallFeature = null }) { Text("Понятно") }
                    }
                )
            }

            else -> {
                AlertDialog(
                    onDismissRequest = { paywallFeature = null },
                    title = { Text("Купить PRO?") },
                    text = { Text("Эта функция доступна только в VoltHome PRO.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                paywallFeature = null
                                appNavController.navigate(Screens.SubscriptionScreen.route) {
                                    launchSingleTop = true
                                }
                            }
                        ) { Text("Да") }
                    },
                    dismissButton = {
                        TextButton(onClick = { paywallFeature = null }) { Text("Нет") }
                    }
                )
            }
        }
    }

    // Подтягиваем профиль, когда авторизация успешна
    LaunchedEffect(authVm.state.collectAsState().value) {
        val s = authVm.state.value
        if (s is AuthViewModel.State.Success) profileVm.refresh()
    }

    val profileFlow: Flow<UserProfileUi?> =
        profileVm.state.map { st ->
            when (st) {
                is ProfileViewModel.UiState.Data -> {
                    val me = st.me
                    UserProfileUi(
                        name = me.displayName.ifBlank { "Пользователь" },
                        email = me.email,
                        avatarUrl = me.avatarUrl,
                        subscriptionStatus = me.plan
                    )
                }

                else -> null
            }
        }

    val bottomRoutes = remember {
        setOf(
            Screens.RoomsList.route,
            Screens.LoadsScreen.route,
            Screens.ExplicationScreen.route
        )
    }
    val navBackStackEntry = appNavController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    // Пробрасываем тариф в UI через CompositionLocal
    CompositionLocalProvider(LocalUserPlan provides userPlan) {
        // ✅ КЛЮЧ: DebugProPanel должен быть внутри BoxScope, иначе align не существует
        Box(Modifier.fillMaxSize()) {

            AppScaffoldWithDrawer(
                title = appBarTitle,
                profileFlow = profileFlow,
                projectsFlow = projectsFlow,
                drawerState = drawerState,
                onLogout = { authVm.signOut() },
                onSelectProject = { id ->
                    projectsVm.selectProject(id)
                    appNavController.navigate(Screens.RoomsList.route) {
                        popUpTo(appNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onCreateProject = {
                    projectsVm.createNewProject()
                    appNavController.navigate(Screens.RoomsList.route) {
                        popUpTo(appNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenSettings = {
                    appNavController.navigate(Screens.SettingsScreen.route) { launchSingleTop = true }
                },
                onOpenProfile = {
                    appNavController.navigate(Screens.ProfileScreen.route) { launchSingleTop = true }
                },
                onOpenSubscription = {
                    appNavController.navigate(Screens.SubscriptionScreen.route) { launchSingleTop = true }
                },
                onOpenAbout = {
                    rootNavController.navigate(Screens.AboutScreen.route) { launchSingleTop = true }
                },
                onRenameProject = { id, newName -> projectsVm.renameProject(id, newName) },
                onDeleteProject = { id -> projectsVm.deleteProject(id) },
                bottomBar = {
                    if (currentRoute in bottomRoutes) {
                        MainBottomNavBar(navController = appNavController)
                    }
                }
            ) {
                NavGraphApp(
                    navController = appNavController,
                    modifier = Modifier.fillMaxSize(),
                    padding = PaddingValues(),
                    showOnboarding = { /* no-op */ },
                    authVm = authVm
                )
            }

            // ✅ Debug-only PRO switch (поверх UI)
            if (BuildConfig.DEBUG) {
                DebugProPanel(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 8.dp)
                )
            }
        }
    }
}

private fun isFeatureAllowed(feature: ProFeature, caps: PlanCapabilities): Boolean {
    return when (feature) {
        ProFeature.PROJECTS_LIMIT -> caps.unlimitedProjects
        ProFeature.PHASE_DND_TEASER -> caps.phaseDragAndDrop
        ProFeature.ADVANCED_DEVICE_EDITOR -> caps.extendedDeviceEditor

        // ✅ export actions для отчёта (save/share/export PDF) — только при pdfExport
        ProFeature.PRO_REPORT -> caps.pdfExport

        // ✅ шаги/обоснования/предупреждения (проф. секции отчёта)
        ProFeature.CALC_EXPLANATIONS -> caps.professionalReportSections
        ProFeature.CALC_WARNINGS -> caps.professionalReportSections
    }
}