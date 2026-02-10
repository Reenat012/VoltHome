package ru.mugalimov.volthome.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.model.PlanCapabilities
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.manual.ManualEditSession
import ru.mugalimov.volthome.ui.manual.ForbiddenAction
import ru.mugalimov.volthome.ui.manual.LocalManualModeGuard
import ru.mugalimov.volthome.ui.manual.ManualModeGuard
import ru.mugalimov.volthome.ui.manual.ManualModeGuardDialog
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.model.ProjectUi
import ru.mugalimov.volthome.ui.model.UserProfileUi
import ru.mugalimov.volthome.ui.navigation.MainBottomNavBar
import ru.mugalimov.volthome.ui.navigation.NavGraphApp
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.paywall.PaywallEntryPoint
import ru.mugalimov.volthome.ui.screens.start_drawer.AppScaffoldWithDrawer
import ru.mugalimov.volthome.ui.screens.start_drawer.ManualModeChipState
import ru.mugalimov.volthome.ui.utilities.ManualDraftResetNotifier
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.ManualModeAppBarViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProfileViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProjectsViewModel
import ru.mugalimov.volthome.ui.viewmodel.UserPlanViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.use_case.manual.CommitManualDraftToLocalDbUseCase
import ru.mugalimov.volthome.domain.use_case.manual.CancelManualAndAutoRecalcUseCase

@Composable
fun MainApp(
    rootNavController: NavHostController,
    authVm: AuthViewModel
) {
    val appNavController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // -----------------------------
    // ✅ Глобальные зависимости / шины
    // -----------------------------
    val context = LocalContext.current
    val appContext = context.applicationContext

    // ✅ Guard: единая точка запрета действий в manual
    val manualGuard = remember {
        ManualModeGuard.fromApp(appContext)
    }

    // ✅ Paywall bus (глобально)
    val paywallBus = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PaywallEntryPoint::class.java
        ).paywallBus()
    }

    // ✅ Snackbar host (глобально, один на всё приложение)
    val snackbarHostState = remember { SnackbarHostState() }

    // ✅ EntryPoint для kill-process UX (notifier + manual repo)
    val resetEp = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ManualDraftResetEntryPoint::class.java
        )
    }

    val scope = rememberCoroutineScope()

    // ✅ EntryPoint для usecase (Save/Cancel)
    val commitEp = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ManualCommitEntryPoint::class.java
        )
    }
    val commitUseCase = remember { commitEp.commitManualDraftToLocalDbUseCase() }
    val cancelUseCase = remember { commitEp.cancelManualAndAutoRecalcUseCase() }

    // ВАЖНО: manualRepo должен быть объявлен ДО первого использования
    val manualDraftResetNotifier = remember { resetEp.manualDraftResetNotifier() }
    val manualRepo: ManualEditSessionRepository = remember { resetEp.manualRepo() }

    // -----------------------------
    // --- проекты
    // -----------------------------
    val projectsVm: ProjectsViewModel = hiltViewModel()
    val projectsFlow: Flow<List<ProjectUi>> = projectsVm.projectsUi
    val appBarTitle = projectsVm.activeProjectTitle.collectAsState().value

    // activeProjectId нужен для one-shot проверки kill-process UX
    val activeProjectId = projectsVm.activeProjectId.collectAsState(initial = null).value

    // -----------------------------
    // ✅ Ручной режим и "грязность" — считаем по сессии.
    // Это нужно только для визуального индикатора в AppBar.
    // -----------------------------
    val manualSessionFlow: Flow<ManualEditSession?> = remember(activeProjectId, manualRepo) {
        val pid = activeProjectId
        if (pid.isNullOrBlank()) {
            // ВАЖНО: указываем тип, иначе Kotlin может начать “плыть” по типам
            flowOf<ManualEditSession?>(null)
        } else {
            manualRepo.observeSession(pid)
        }
            // На всякий случай — чтобы лишний раз не дёргать Compose
            .distinctUntilChanged()
    }

    val manualSession = manualSessionFlow.collectAsState(initial = null).value

    val manualChipState = remember(manualSession) {
        when {
            manualSession?.manualModeActive != true -> ManualModeChipState.AUTO
            else -> {
                // ✅ DIRTY: draft отличается от baseState
                // Если equals неадекватный — будет MANUAL, но не упадём.
                val isDirty = try {
                    manualSession.draftState != manualSession.baseState
                } catch (_: Throwable) {
                    false
                }
                if (isDirty) ManualModeChipState.DIRTY else ManualModeChipState.MANUAL
            }
        }
    }

    // ✅ ручной режим (AppBar, глобально на проект)
    val manualAppBarVm: ManualModeAppBarViewModel = hiltViewModel()
    val isManualMode = manualAppBarVm.isManualMode.collectAsState().value
    // (isManualMode сейчас может быть не использован напрямую — оставляю как было)

    // -----------------------------
    // --- профиль
    // -----------------------------
    val profileVm: ProfileViewModel = hiltViewModel()

    // --- тариф (free/pro)
    val userPlanVm: UserPlanViewModel = hiltViewModel()
    val userPlan = userPlanVm.plan.collectAsState().value

    // -----------------------------
    // ✅ Kill-process UX: marker + one-shot snackbar
    // -----------------------------
    LaunchedEffect(activeProjectId) {
        val projectId = activeProjectId ?: return@LaunchedEffect

        // Проверяем: есть ли активная manual-сессия именно для этого проекта.
        // Важно: getActiveSession() может быть null после kill-process, и это и есть нужный сигнал.
        val s = manualRepo.getActiveSession()
        val hasActiveSession = (s?.projectId == projectId) && (s.manualModeActive)

        // Если маркер стоит, но сессии нет => черновик пропал -> показываем snackbar ровно один раз.
        if (manualDraftResetNotifier.consumeResetIfNeeded(projectId, hasActiveSession)) {
            snackbarHostState.showSnackbar("Черновик ручного режима был сброшен")
        }
    }

    // -----------------------------
    // ✅ Paywall logic (capabilities-aware)
    // -----------------------------
    var paywallFeature by remember { mutableStateOf<ProFeature?>(null) }

    // ✅ Единый диалог Save/Cancel/Stay для manual + отложенное действие (pendingProceed)
    var manualExitDialogVisible by remember { mutableStateOf(false) }
    var pendingProceed by remember { mutableStateOf<(() -> Unit)?>(null) }

    // ✅ Запрос на выход из manual (показываем диалог, опционально запоминаем действие после выхода)
    fun requestManualExit(proceedAfterExit: (() -> Unit)? = null) {
        pendingProceed = proceedAfterExit
        manualExitDialogVisible = true
    }

    fun dismissManualExitDialog() {
        manualExitDialogVisible = false
        pendingProceed = null
    }

    LaunchedEffect(paywallBus, userPlan) {
        val caps = userPlan.capabilities
        paywallBus.events.collect { feature ->
            if (isFeatureAllowed(feature, caps)) {
                // Уже доступно — игнорируем
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

    // -----------------------------
    // ✅ Paywall dialogs
    // -----------------------------
    if (paywallFeature != null) {
        val feature = paywallFeature!!

        when (feature) {
            // ✅ Коммит 5: объясняем механику (как это работает), а не "PRO ради PRO"
            ProFeature.PHASE_DND_TEASER -> {
                AlertDialog(
                    onDismissRequest = { paywallFeature = null },
                    title = { Text("Ручное управление фазами") },
                    text = {
                        Text(
                            "Как это работает:\n" +
                                    "1) Включите «Ручной режим».\n" +
                                    "2) Зажмите значок ⠿ у группы.\n" +
                                    "3) Перетащите и отпустите на фазе A/B/C сверху.\n\n" +
                                    "После каждого переноса сразу меняются токи по фазам и ΔI.\n\n" +
                                    "Функция доступна в PRO."
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

            // ✅ Коммит 3: feature-specific объясняющая модалка (не тупой paywall)
            ProFeature.DECISION_DETAILS -> {
                AlertDialog(
                    onDismissRequest = { paywallFeature = null },
                    title = { Text("Аудит решения по фазам") },
                    text = {
                        Text(
                            "В PRO открывается «Аудит решения» — не повтор A/B, а проверяемость распределения.\n\n" +
                                    "Что там есть:\n" +
                                    "• история шагов алгоритма по группе;\n" +
                                    "• список перемещений между фазами (если алгоритм уточнял результат);\n" +
                                    "• пояснение устойчивости: почему итог не случайный и закрепился.\n\n" +
                                    "Это полезно, когда вы проверяете нестандартные сценарии и хотите понимать, " +
                                    "было ли вмешательство алгоритма после базовой раскладки.\n\n" +
                                    "В бесплатной версии доступно краткое объяснение (A/B). «Аудит решения» доступен в PRO."
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

    // Пробрасываем тариф и guard в UI через CompositionLocal
    CompositionLocalProvider(
        LocalUserPlan provides userPlan,
        LocalManualModeGuard provides manualGuard
    ) {
        Box(Modifier.fillMaxSize()) {
            // ✅ Единый guard-диалог для всех "опасных" действий в manual
            ManualModeGuardDialog(guard = manualGuard)

            // ✅ Глобальный snackbar host (one-shot уведомления)
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )

            AppScaffoldWithDrawer(
                title = appBarTitle,
                profileFlow = profileFlow,
                projectsFlow = projectsFlow,
                drawerState = drawerState,

                // ✅ Диалог Save/Cancel/Stay на верхнем уровне
                manualExitDialogVisible = manualExitDialogVisible,
                onManualExitDialogDismiss = {
                    // ✅ Stay: просто закрываем диалог
                    dismissManualExitDialog()
                },
                onManualSaveClick = {
                    // ✅ Save: commit draft -> (важно) очистить overrides -> exit manual -> выполнить pendingProceed
                    scope.launch {
                        val projectId = activeProjectId.orEmpty()
                        val s = manualSession

                        if (projectId.isBlank() || s == null || s.projectId != projectId) {
                            snackbarHostState.showSnackbar("Нет активного черновика для сохранения")
                            dismissManualExitDialog()
                            return@launch
                        }

                        try {
                            // 1) Коммитим draft в локальную БД
                            commitUseCase.execute(
                                CommitManualDraftToLocalDbUseCase.Params(
                                    projectId = projectId,
                                    draft = s.draftState
                                )
                            )

                            // 2) КРИТИЧНО: после ручного Save overrides больше не должны перекрывать фазы в AUTO.
                            // Иначе AUTO сразу "откатит" к прошлым сохранениям.
                            commitEp.groupPhaseOverrideDao().deleteByProject(projectId)

                            // 3) Выходим из manual (и чистим marker внутри repo)
                            manualRepo.exitManualMode(projectId)

                            // 4) Закрываем диалог и выполняем отложенное действие
                            val proceed = pendingProceed
                            dismissManualExitDialog()
                            proceed?.invoke()
                        } catch (t: Throwable) {
                            // ✅ Ошибка Save: manual НЕ выключаем
                            snackbarHostState.showSnackbar("Ошибка сохранения: ${t.message ?: "неизвестно"}")
                        }
                    }
                },
                onManualCancelClick = {
                    // ✅ Cancel: auto-recalc -> commit to DB -> exit manual -> выполнить pendingProceed
                    scope.launch {
                        val projectId = activeProjectId.orEmpty()
                        if (projectId.isBlank()) {
                            snackbarHostState.showSnackbar("Не выбран проект")
                            dismissManualExitDialog()
                            return@launch
                        }

                        try {
                            when (val res = cancelUseCase.execute(
                                CancelManualAndAutoRecalcUseCase.Params(projectId = projectId)
                            )) {
                                is GroupingResult.Error -> {
                                    // ✅ Ошибка Cancel: manual НЕ выключаем
                                    snackbarHostState.showSnackbar("Ошибка пересчёта: ${res.message}")
                                    return@launch
                                }
                                is GroupingResult.Success -> {
                                    // ок
                                }
                            }

                            // 2) Выходим из manual (и чистим marker внутри repo)
                            manualRepo.exitManualMode(projectId)

                            // 3) Закрываем диалог и выполняем отложенное действие
                            val proceed = pendingProceed
                            dismissManualExitDialog()
                            proceed?.invoke()
                        } catch (t: Throwable) {
                            snackbarHostState.showSnackbar("Ошибка отмены: ${t.message ?: "неизвестно"}")
                        }
                    }
                },

                // ✅ Чип ручного режима (справа в AppBar)
                onManualModeClick = {
                    if (manualChipState == ManualModeChipState.AUTO) {
                        manualAppBarVm.onManualModeClick()
                    } else {
                        requestManualExit(proceedAfterExit = null)
                    }
                },
                manualChipState = manualChipState,

                onLogout = {
                    val proceed = { authVm.signOut() }
                    if (manualChipState == ManualModeChipState.AUTO) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },

                onSelectProject = { id ->
                    val proceed = {
                        projectsVm.selectProject(id)
                        appNavController.navigate(Screens.RoomsList.route) {
                            popUpTo(appNavController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    if (manualChipState == ManualModeChipState.AUTO) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },

                onCreateProject = {
                    val proceed = {
                        projectsVm.createNewProject()
                        appNavController.navigate(Screens.RoomsList.route) {
                            popUpTo(appNavController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    if (manualChipState == ManualModeChipState.AUTO) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
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
        }
    }
}

private fun isFeatureAllowed(feature: ProFeature, caps: PlanCapabilities): Boolean {
    return when (feature) {
        ProFeature.PROJECTS_LIMIT -> caps.unlimitedProjects
        ProFeature.PHASE_DND_TEASER -> caps.phaseDragAndDrop
        ProFeature.ADVANCED_DEVICE_EDITOR -> caps.extendedDeviceEditor

        // export actions для отчёта (save/share/действия PDF) — только при pdfExport
        ProFeature.PRO_REPORT -> caps.pdfExport

        // шаги/обоснования/предупреждения (проф. секции отчёта)
        ProFeature.CALC_EXPLANATIONS -> caps.professionalReportSections
        ProFeature.CALC_WARNINGS -> caps.professionalReportSections

        // техподробности выбора фазы (уровень C)
        ProFeature.DECISION_DETAILS -> caps.professionalReportSections
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ManualDraftResetEntryPoint {
    fun manualDraftResetNotifier(): ManualDraftResetNotifier
    fun manualRepo(): ManualEditSessionRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ManualCommitEntryPoint {
    fun commitManualDraftToLocalDbUseCase(): CommitManualDraftToLocalDbUseCase
    fun cancelManualAndAutoRecalcUseCase(): CancelManualAndAutoRecalcUseCase

    // ✅ нужно, чтобы после Save чистить overrides и не получать "откат"
    fun groupPhaseOverrideDao(): GroupPhaseOverrideDao
}