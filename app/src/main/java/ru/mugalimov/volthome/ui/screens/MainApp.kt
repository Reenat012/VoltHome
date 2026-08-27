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
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.GridView
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.BuildConfig
import ru.mugalimov.volthome.core.analytics.AnalyticsEvent
import ru.mugalimov.volthome.core.analytics.AnalyticsRuntimeEntryPoint
import ru.mugalimov.volthome.core.analytics.PaywallSource
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
import ru.mugalimov.volthome.ui.model.ManualModeControlAvailability
import ru.mugalimov.volthome.ui.model.ManualModeUiPolicy
import ru.mugalimov.volthome.ui.model.ProjectUi
import ru.mugalimov.volthome.ui.model.UserProfileUi
import ru.mugalimov.volthome.ui.navigation.MainBottomNavBar
import ru.mugalimov.volthome.ui.navigation.NavGraphApp
import ru.mugalimov.volthome.ui.navigation.Screens
import ru.mugalimov.volthome.ui.onboarding.CoachMarkHost
import ru.mugalimov.volthome.ui.onboarding.OnboardingRuntimeEntryPoint
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.paywall.PaywallEntryPoint
import ru.mugalimov.volthome.ui.screens.start_drawer.AppScaffoldWithDrawer
import ru.mugalimov.volthome.ui.screens.start_drawer.ManualModeChipState
import ru.mugalimov.volthome.ui.utilities.ManualDraftResetNotifier
import ru.mugalimov.volthome.ui.viewmodel.AuthViewModel
import ru.mugalimov.volthome.ui.viewmodel.ManualModeAppBarViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProfileViewModel
import ru.mugalimov.volthome.ui.viewmodel.ProjectsViewModel
import ru.mugalimov.volthome.ui.viewmodel.SubscriptionViewModel
import ru.mugalimov.volthome.ui.viewmodel.UserPlanViewModel
import ru.mugalimov.volthome.ui.onboarding.hints.BaseHints
import ru.mugalimov.volthome.ui.onboarding.model.ProjectsOnboardingFacts

@Composable
fun MainApp(
    rootNavController: NavHostController,
    authVm: AuthViewModel,
) {
    val appNavController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // При каждом запуске сверяем локальный PRO с активной подпиской RuStore.
    // Временная ошибка RuStore не сбрасывает сохранённое локальное право.
    val subscriptionVm: SubscriptionViewModel = hiltViewModel()
    LaunchedEffect(subscriptionVm) {
        // Локальное право доступно сразу. Сетевую/DRM-инициализацию RuStore
        // не запускаем одновременно с построением первого интерактивного экрана.
        if (!BuildConfig.DEBUG) {
            delay(2_500)
            subscriptionVm.refreshStatus()
        }
    }

    // -----------------------------
    // ✅ Глобальные зависимости / шины
    // -----------------------------
    val context = LocalContext.current
    val appContext = context.applicationContext

    val analyticsRuntime = remember(appContext) {
        EntryPointAccessors.fromApplication(
            appContext,
            AnalyticsRuntimeEntryPoint::class.java
        )
    }
    val analytics = analyticsRuntime.analyticsTracker()
    val purchaseAnalyticsContext = analyticsRuntime.purchaseAnalyticsContext()

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

    // -----------------------------
    // ✅ Onboarding runtime singletons
    // -----------------------------
    val onboardingEp = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnboardingRuntimeEntryPoint::class.java
        )
    }
    val onboardingCoordinator = remember { onboardingEp.onboardingCoordinator() }
    val uiAnchorRegistry = remember { onboardingEp.uiAnchorRegistry() }

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

    // ✅ Канонические facts для Projects onboarding.
    val projectsOnboardingFacts =
        projectsVm.onboardingFacts.collectAsState(initial = ProjectsOnboardingFacts()).value

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

    val manualAppBarVm: ManualModeAppBarViewModel = hiltViewModel()
    val hasManualOverrides = manualAppBarVm.hasManualOverrides.collectAsState().value

    val manualChipState = remember(manualSession, hasManualOverrides) {
        when {
            manualSession?.manualModeActive == true -> {
                val isDirty = try {
                    manualSession.draftState != manualSession.baseState
                } catch (_: Throwable) {
                    false
                }
                if (isDirty) ManualModeChipState.DIRTY else ManualModeChipState.MANUAL
            }
            hasManualOverrides -> ManualModeChipState.SAVED_MANUAL
            else -> ManualModeChipState.AUTO
        }
    }
    val manualEditorActive = manualChipState == ManualModeChipState.MANUAL ||
        manualChipState == ManualModeChipState.DIRTY

    // ✅ ручной режим (AppBar, глобально на проект)
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

        // ✅ Проверяем только сессию текущего проекта.
        val s = manualRepo.getSession(projectId)
        val hasActiveSession = s?.manualModeActive == true

        if (manualDraftResetNotifier.consumeResetIfNeeded(projectId, hasActiveSession)) {
            snackbarHostState.showSnackbar("Черновик ручного режима был сброшен")
        }
    }

    // -----------------------------
    // ✅ Paywall logic (capabilities-aware)
    // -----------------------------
    var paywallFeature by remember { mutableStateOf<ProFeature?>(null) }
    var paywallSource by remember { mutableStateOf(PaywallSource.PRO_SCREEN) }
    var paywallRequestId by remember { mutableStateOf<Long?>(null) }

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
        paywallBus.events.collect { request ->
            if (isFeatureAllowed(request.feature, caps)) return@collect
            purchaseAnalyticsContext.begin(request.source)
            paywallSource = request.source
            paywallFeature = request.feature
            paywallRequestId = request.id
        }
    }

    LaunchedEffect(paywallRequestId) {
        if (paywallFeature == null || paywallRequestId == null) return@LaunchedEffect
        purchaseAnalyticsContext
            .markPaywallShownIfNeeded(paywallSource)
            ?.let { source ->
                analytics.track(AnalyticsEvent.PaywallShown(source))
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
            ProFeature.PANEL_VISUALIZATION -> {
                AlertDialog(
                    onDismissRequest = { paywallFeature = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Визуализация щита",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "Фронтальная компоновка щита доступна в ВольтХом PRO.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PaywallBulletItem("аппараты раскладываются по DIN-рейкам")
                            PaywallBulletItem("видны фазы, занятые модули и резерв")
                            PaywallBulletItem("по нажатию открываются параметры группы и защиты")
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
                            Text("Открыть PRO")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { paywallFeature = null }) {
                            Text("Не сейчас")
                        }
                    }
                )
            }

            ProFeature.SINGLE_LINE_DIAGRAM -> {
                AlertDialog(
                    onDismissRequest = { paywallFeature = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AccountTree,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Однолинейная схема",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "Когда щит уже собран в экспликации, пользователю всё равно нужно быстро понять его структуру: где ввод, какие фазы, какие группы, какие автоматы, кабели и нагрузки.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PaywallBulletItem(
                                text = "не нужно вручную переносить данные из экспликации в отдельную схему"
                            )

                            PaywallBulletItem(
                                text = "структура щита видна целиком: ввод → защита → фазы → группы"
                            )

                            PaywallBulletItem(
                                text = "PDF удобно показать заказчику, коллеге или использовать для самопроверки"
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Однолинейная схема в ВольтХом PRO собирает это автоматически из уже введённых данных проекта.",
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
                            Text("Открыть схему в PRO")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { paywallFeature = null }) {
                            Text("Не сейчас")
                        }
                    }
                )
            }

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

            ProFeature.CABLE_LINE_CALCULATION -> {
                AlertDialog(
                    onDismissRequest = { paywallFeature = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Cable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Расчёт кабельных линий",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "В бесплатной версии виден предварительный профиль кабеля. PRO учитывает фактические условия каждой трассы.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PaywallBulletItem("длина, материал и изоляция проводника")
                            PaywallBulletItem("способ прокладки, температура и группировка")
                            PaywallBulletItem("проверка допустимого тока Iz и падения напряжения")
                            PaywallBulletItem("результаты в линиях, схеме и PDF-отчёте")
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Попробуйте ВольтХом PRO бесплатно в течение 7 дней.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
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
                        ) { Text("Попробовать PRO") }
                    },
                    dismissButton = {
                        TextButton(onClick = { paywallFeature = null }) {
                            Text("Не сейчас")
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

    // Подтягиваем локальный профиль, когда локальная сессия готова.
    val authState by authVm.state.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthViewModel.State.Success) {
            profileVm.refresh()
        }
    }

    val profileFlow: Flow<UserProfileUi?> = remember(profileVm) {
        profileVm.state.map { st ->
            when (st) {
                is ProfileViewModel.UiState.Data -> {
                    val me = st.me
                    UserProfileUi(
                        name = me.displayName
                            ?.takeIf { it.isNotBlank() }
                            ?: "Пользователь",
                        email = me.email
                            ?.takeIf { it.isNotBlank() },
                        avatarUrl = me.avatarUrl
                            ?.takeIf { it.isNotBlank() },
                        subscriptionStatus = me.plan
                            ?.takeIf { it.isNotBlank() }
                            ?: "free"
                    )
                }

                else -> null
            }
        }
    }

    val bottomRoutes = remember {
        setOf(
            Screens.RoomsList.route,
            Screens.LoadsScreen.route,
            Screens.ExplicationScreen.route,
            Screens.PanelVisualizationScreen.route
        )
    }
    val navBackStackEntry = appNavController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route
    val manualModeControlAvailability = remember(currentRoute, manualChipState) {
        ManualModeUiPolicy.availability(
            route = currentRoute,
            manualModeActive = manualChipState != ManualModeChipState.AUTO
        )
    }

    // -----------------------------
    // ✅ Текущий экран для onboarding host
    // -----------------------------
    val currentOnboardingScreen = remember(currentRoute, drawerState.currentValue) {
        when {
            // Drawer трактуем как контекст projects.
            drawerState.currentValue == DrawerValue.Open -> OnboardingScreen.PROJECTS

            currentRoute == Screens.RoomsList.route -> OnboardingScreen.ROOMS
            currentRoute == Screens.LoadsScreen.route -> OnboardingScreen.LOADS
            currentRoute == Screens.ExplicationScreen.route -> OnboardingScreen.EXPLICATION
            currentRoute == Screens.PanelVisualizationScreen.route ->
                OnboardingScreen.PANEL_VISUALIZATION
            currentRoute == Screens.AddRoom.route -> OnboardingScreen.ADD_ROOM_SHEET

            currentRoute?.startsWith("room_detail/") == true -> OnboardingScreen.ROOM_DETAILS

            else -> null
        }
    }

    // -----------------------------
    // ✅ Единственная orchestration point для Projects onboarding
    // -----------------------------
    LaunchedEffect(currentOnboardingScreen, projectsOnboardingFacts) {
        Log.d(
            "ONBOARD_PROJECTS",
            buildString {
                append("ORCH_BEGIN")
                append(" currentScreen=").append(currentOnboardingScreen?.name ?: "null")
                append(" projectsCount=").append(projectsOnboardingFacts.projectsCount)
                append(" isLoading=").append(projectsOnboardingFacts.isLoading)
                append(" drawerOpen=").append(drawerState.currentValue == DrawerValue.Open)
            }
        )

        if (currentOnboardingScreen != OnboardingScreen.PROJECTS) {
            Log.d(
                "ONBOARD_PROJECTS",
                "ORCH_SKIP reason=SCREEN_NOT_PROJECTS currentScreen=${currentOnboardingScreen?.name ?: "null"}"
            )
            return@LaunchedEffect
        }

        val hint = BaseHints.forProjects(projectsOnboardingFacts)
        if (hint == null) {
            Log.d(
                "ONBOARD_PROJECTS",
                "ORCH_SKIP reason=NO_HINT projectsCount=${projectsOnboardingFacts.projectsCount}"
            )
            return@LaunchedEffect
        }

        Log.d(
            "ONBOARD_PROJECTS",
            "ORCH_CANDIDATE hintId=${hint.hintId.name} priority=${hint.priority} targetTag=${hint.targetTag?.rawTag ?: "null"}"
        )

        val accepted = onboardingCoordinator.tryShow(
            hintId = hint.hintId,
            screen = hint.screen,
            targetTag = hint.targetTag,
            title = hint.title,
            body = hint.body
        )

        Log.d(
            "ONBOARD_PROJECTS",
            "ORCH_END hintId=${hint.hintId.name} accepted=$accepted"
        )
    }

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

                // ✅ Пробрасываем текущий экран вниз,
                // чтобы чип "Ручной" мог стать anchor именно на Экспликации.
                manualChipOnboardingScreen = currentOnboardingScreen,
                manualModeControlAvailability = manualModeControlAvailability,

                manualExitDialogVisible = manualExitDialogVisible,
                onManualExitDialogDismiss = { dismissManualExitDialog() },

                onManualSaveClick = {
                    scope.launch {
                        val projectId = activeProjectId.orEmpty()
                        val s = if (projectId.isBlank()) null else manualRepo.getSession(projectId)

                        // ✅ Сохраняем только scoped session текущего проекта.
                        if (projectId.isBlank() || s?.manualModeActive != true) {
                            snackbarHostState.showSnackbar("Нет активного черновика для сохранения")
                            dismissManualExitDialog()
                            return@launch
                        }

                        try {
                            commitUseCase.execute(
                                CommitManualDraftToLocalDbUseCase.Params(
                                    projectId = projectId,
                                    draft = s.draftState
                                )
                            )

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

                        // ✅ Только scoped session текущего проекта.
                        val session = manualRepo.getSession(projectId)
                        if (session?.manualModeActive != true) {
                            snackbarHostState.showSnackbar("Нет активного ручного режима")
                            dismissManualExitDialog()
                            return@launch
                        }

                        try {
                            Log.w("MANUAL_CANCEL", "MAINAPP Cancel START (NO_RECALC) pid=$projectId")

                            val clearStats = ownershipOverridesCleaner.clearAll(projectId)
                            Log.w(
                                "MANUAL_CANCEL",
                                "MAINAPP Cancel cleaner done pid=$projectId totalDeleted=${clearStats.totalDeleted}"
                            )

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
                    when {
                        manualChipState == ManualModeChipState.MANUAL ||
                            manualChipState == ManualModeChipState.DIRTY -> {
                            requestManualExit(proceedAfterExit = null)
                        }
                        manualModeControlAvailability == ManualModeControlAvailability.EDITABLE -> {
                            manualAppBarVm.onManualModeClick()
                        }
                        else -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Ручная корректировка доступна на экранах «Нагрузки» и «Щит»"
                                )
                            }
                        }
                    }
                },
                manualChipState = manualChipState,

                onLogout = {
                    val proceed = { authVm.signOut() }
                    if (!manualEditorActive) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },

                onSelectProject = { id ->
                    val proceed = {
                        projectsVm.selectProject(id)
                        appNavController.navigate(Screens.RoomsList.route) {
                            popUpTo(appNavController.graph.findStartDestination().id) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                    if (!manualEditorActive) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },

                onCreateProject = {
                    val proceed = {
                        appNavController.navigate(Screens.ProjectWizard.route) {
                            launchSingleTop = true
                        }
                    }
                    if (!manualEditorActive) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },

                // ✅ Коммит 8: эти переходы тоже обязаны проходить через Save/Cancel/Stay
                onOpenSettings = {
                    val proceed = {
                        appNavController.navigate(Screens.SettingsScreen.route) { launchSingleTop = true }
                    }
                    if (!manualEditorActive) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },
                onOpenProfile = {
                    val proceed = {
                        appNavController.navigate(Screens.ProfileScreen.route) { launchSingleTop = true }
                    }
                    if (!manualEditorActive) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },
                onOpenSubscription = {
                    val proceed = {
                        purchaseAnalyticsContext.begin(PaywallSource.PRO_MENU)
                        appNavController.navigate(Screens.SubscriptionScreen.route) { launchSingleTop = true }
                    }
                    if (!manualEditorActive) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },
                onOpenAbout = {
                    val proceed = {
                        rootNavController.navigate(Screens.AboutScreen.route) { launchSingleTop = true }
                    }
                    if (!manualEditorActive) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },

                // ✅ Коммит 8: rename/delete тоже не должны обходить ручной режим
                onRenameProject = { id, newName ->
                    val proceed = { projectsVm.renameProject(id, newName) }
                    if (!manualEditorActive) proceed()
                    else requestManualExit(proceedAfterExit = proceed)
                },
                onDeleteProject = { id ->
                    val proceed = { projectsVm.deleteProject(id) }
                    if (!manualEditorActive) proceed()
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
                    showOnboarding = {
                        appNavController.navigate(Screens.OnBoardingScreen.route) {
                            launchSingleTop = true
                        }
                    },
                    authVm = authVm,
                    paywallBus = paywallBus,
                    projectName = appBarTitle
                )
            }

            // ✅ Единый host coachmark-ов.
            // Он только рендерит active hint и ничего не решает сам.
            CoachMarkHost(
                coordinator = onboardingCoordinator,
                anchorRegistry = uiAnchorRegistry,
                currentScreen = currentOnboardingScreen,
                modifier = Modifier.fillMaxSize()
            )
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
        ProFeature.SINGLE_LINE_DIAGRAM -> caps.pdfExport
        ProFeature.PANEL_VISUALIZATION -> caps.panelVisualization
        ProFeature.CABLE_LINE_CALCULATION -> caps.cableLineCalculation
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
