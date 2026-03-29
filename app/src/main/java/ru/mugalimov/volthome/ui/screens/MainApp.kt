package ru.mugalimov.volthome.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.data.billing.pending.PendingConfirmCoordinator
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.ownership.OwnershipOverridesCleaner
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.model.PlanCapabilities
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.domain.model.manual.ManualEditSession
import ru.mugalimov.volthome.domain.use_case.manual.CommitManualDraftToLocalDbUseCase
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

@Composable
fun MainApp(
    rootNavController: NavHostController,
    authVm: AuthViewModel,
) {
    val appNavController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // -----------------------------
    // ✅ Глобальные зависимости / шины
    // -----------------------------
    val context = LocalContext.current
    val appContext = context.applicationContext

    val pendingCoordinator = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BillingPendingEntryPoint::class.java
        ).pendingConfirmCoordinator()
    }

    // ✅ Guard: единая точка запрета действий в manual
    val manualGuard = remember {
        ManualModeGuard.fromApp(appContext)
    }

    LaunchedEffect(Unit) {
        // 🔥 cold start replay
        pendingCoordinator.tryReplay("cold_start")
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

    // ❗️Cancel больше НЕ делает пересчёт (ТЗ): поэтому cancelUseCase не нужен
    // val cancelUseCase = remember { commitEp.cancelManualAndAutoRecalcUseCase() }

    // ✅ Commit 6: все overrides чистим только через единый cleaner.
    val ownershipOverridesCleaner = remember { commitEp.ownershipOverridesCleaner() }

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
            flowOf<ManualEditSession?>(null)
        } else {
            manualRepo.observeSession(pid)
        }
            .distinctUntilChanged()
    }

    val manualSession = manualSessionFlow.collectAsState(initial = null).value

    val manualChipState = remember(manualSession) {
        when {
            manualSession?.manualModeActive != true -> ManualModeChipState.AUTO
            else -> {
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
    // ✅ Коммит 8: глобально слушаем сообщения от ManualModeAppBarViewModel
    // -----------------------------
    LaunchedEffect(manualAppBarVm) {
        manualAppBarVm.messages.collect { msg ->
            // ✅ сообщения от VM должны быть видимыми пользователю, иначе они бессмысленны
            snackbarHostState.showSnackbar(msg)
        }
    }

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

        val s = manualRepo.getActiveSession()
        val hasActiveSession = (s?.projectId == projectId) && (s.manualModeActive)

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
            if (isFeatureAllowed(feature, caps)) return@collect
            paywallFeature = feature
        }
    }

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
            ProFeature.PHASE_DND_TEASER -> {
                AlertDialog(
                    onDismissRequest = { paywallFeature = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = stringResource(R.string.paywall_manual_mode_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    text = {

                        Column {

                            Text(
                                text = stringResource(R.string.paywall_manual_mode_intro),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PaywallBulletItem(
                                text = stringResource(R.string.paywall_manual_mode_item1)
                            )

                            PaywallBulletItem(
                                text = stringResource(R.string.paywall_manual_mode_item2)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.paywall_manual_mode_project_scope),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.paywall_manual_mode_pro_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                paywallFeature = null
                                appNavController.navigate(Screens.SubscriptionScreen.route) { launchSingleTop = true }
                            }
                        ) {
                            Text(stringResource(R.string.paywall_open_pro))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { paywallFeature = null }) {
                            Text(stringResource(R.string.paywall_not_now))
                        }
                    }
                )
            }

            ProFeature.ADVANCED_DEVICE_EDITOR -> {
                AlertDialog(
                    onDismissRequest = { paywallFeature = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = stringResource(R.string.paywall_device_editor_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = stringResource(R.string.paywall_device_editor_intro),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PaywallBulletItem(
                                text = stringResource(R.string.paywall_device_editor_item1)
                            )

                            PaywallBulletItem(
                                text = stringResource(R.string.paywall_device_editor_item2)
                            )

                            PaywallBulletItem(
                                text = stringResource(R.string.paywall_device_editor_item3)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.paywall_device_editor_pro_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                paywallFeature = null
                                appNavController.navigate(Screens.SubscriptionScreen.route) {
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Text(stringResource(R.string.paywall_open_pro))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { paywallFeature = null }) {
                            Text(stringResource(R.string.paywall_not_now))
                        }
                    }
                )
            }

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
                                appNavController.navigate(Screens.SubscriptionScreen.route) { launchSingleTop = true }
                            }
                        ) { Text("Открыть PRO") }
                    },
                    dismissButton = { TextButton(onClick = { paywallFeature = null }) { Text("Понятно") } }
                )
            }

            else -> {
                AlertDialog(
                    onDismissRequest = { paywallFeature = null },
                    title = { Text("Купить PRO?") },
                    text = { Text("Эта функция доступна в ВольтХом PRO.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                paywallFeature = null
                                appNavController.navigate(Screens.SubscriptionScreen.route) { launchSingleTop = true }
                            }
                        ) { Text("Да") }
                    },
                    dismissButton = { TextButton(onClick = { paywallFeature = null }) { Text("Нет") } }
                )
            }
        }
    }

    // Подтягиваем профиль, когда авторизация успешна
    LaunchedEffect(authVm.state.collectAsState().value) {
        val s = authVm.state.value
        if (s is AuthViewModel.State.Success) {
            profileVm.refresh()

            // 🔥 Commit 3 — replay pending после логина
            pendingCoordinator.tryReplay("login")
        }
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

    CompositionLocalProvider(
        LocalUserPlan provides userPlan,
        LocalManualModeGuard provides manualGuard
    ) {
        Box(Modifier.fillMaxSize()) {
            ManualModeGuardDialog(guard = manualGuard)

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

                manualExitDialogVisible = manualExitDialogVisible,
                onManualExitDialogDismiss = { dismissManualExitDialog() },

                onManualSaveClick = {
                    scope.launch {
                        val projectId = activeProjectId.orEmpty()
                        val s = manualRepo.getActiveSession()

                        if (projectId.isBlank() || s == null || s.projectId != projectId) {
                            snackbarHostState.showSnackbar("Нет активного черновика для сохранения")
                            dismissManualExitDialog()
                            return@launch
                        }

                        try {
                            // ✅ Save usecase теперь сам:
                            // 1) коммитит draft,
                            // 2) чистит overrides через cleaner,
                            // 3) фиксирует manualLock=true.
                            commitUseCase.execute(
                                CommitManualDraftToLocalDbUseCase.Params(
                                    projectId = projectId,
                                    draft = s.draftState
                                )
                            )

                            // После успешного save просто выходим из manual
                            manualRepo.exitManualMode(projectId)

                            val proceed = pendingProceed
                            dismissManualExitDialog()
                            proceed?.invoke()
                        } catch (t: Throwable) {
                            Log.e("MANUAL_SAVE", "MAINAPP Save failed pid=$projectId", t)
                            snackbarHostState.showSnackbar("Ошибка сохранения: ${t.message ?: "неизвестно"}")
                        }
                    }
                },

                onManualCancelClick = {
                    scope.launch {
                        val projectId = activeProjectId.orEmpty()
                        if (projectId.isBlank()) {
                            snackbarHostState.showSnackbar("Не выбран проект")
                            dismissManualExitDialog()
                            return@launch
                        }

                        val session = manualRepo.getActiveSession()
                        if (session == null || session.projectId != projectId) {
                            snackbarHostState.showSnackbar("Нет активного ручного режима")
                            dismissManualExitDialog()
                            return@launch
                        }

                        try {
                            // ✅ Cancel НЕ делает auto-recalc и НЕ пишет auto-группы.
                            Log.w("MANUAL_CANCEL", "MAINAPP Cancel START (NO_RECALC) pid=$projectId")

                            // ✅ Все overrides чистим только через единый cleaner.
                            val clearStats = ownershipOverridesCleaner.clearAll(projectId)
                            Log.w(
                                "MANUAL_CANCEL",
                                "MAINAPP Cancel cleaner done pid=$projectId totalDeleted=${clearStats.totalDeleted}"
                            )

                            // Выходим из manual: UI вернётся к последнему сохранённому состоянию БД
                            manualRepo.exitManualMode(projectId)

                            val proceed = pendingProceed
                            dismissManualExitDialog()
                            proceed?.invoke()

                            snackbarHostState.showSnackbar("Ручные изменения отменены")
                        } catch (t: Throwable) {
                            Log.e("MANUAL_CANCEL", "Cancel EXCEPTION pid=$projectId", t)
                            snackbarHostState.showSnackbar("Ошибка отмены: ${t.message ?: "неизвестно"}")
                        }
                    }
                },

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

                // ✅ Коммит 8: эти переходы тоже обязаны проходить через Save/Cancel/Stay
                onOpenSettings = {
                    val proceed = {
                        appNavController.navigate(Screens.SettingsScreen.route) { launchSingleTop = true }
                    }
                    if (manualChipState == ManualModeChipState.AUTO) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },
                onOpenProfile = {
                    val proceed = {
                        appNavController.navigate(Screens.ProfileScreen.route) { launchSingleTop = true }
                    }
                    if (manualChipState == ManualModeChipState.AUTO) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },
                onOpenSubscription = {
                    val proceed = {
                        appNavController.navigate(Screens.SubscriptionScreen.route) { launchSingleTop = true }
                    }
                    if (manualChipState == ManualModeChipState.AUTO) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },
                onOpenAbout = {
                    val proceed = {
                        rootNavController.navigate(Screens.AboutScreen.route) { launchSingleTop = true }
                    }
                    if (manualChipState == ManualModeChipState.AUTO) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },

                // ✅ Коммит 8: rename/delete тоже не должны обходить ручной режим
                onRenameProject = { id, newName ->
                    val proceed = { projectsVm.renameProject(id, newName) }
                    if (manualChipState == ManualModeChipState.AUTO) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },
                onDeleteProject = { id ->
                    val proceed = { projectsVm.deleteProject(id) }
                    if (manualChipState == ManualModeChipState.AUTO) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },

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
        ProFeature.PRO_REPORT -> caps.pdfExport
        ProFeature.CALC_EXPLANATIONS -> caps.professionalReportSections
        ProFeature.CALC_WARNINGS -> caps.professionalReportSections
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

    // ✅ Commit 6: единая точка очистки всех overrides
    fun ownershipOverridesCleaner(): OwnershipOverridesCleaner
}

@Composable
private fun PaywallBulletItem(text: String) {

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {

        Icon(
            imageVector = Icons.Outlined.DragIndicator,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BillingPendingEntryPoint {
    fun pendingConfirmCoordinator(): PendingConfirmCoordinator
}