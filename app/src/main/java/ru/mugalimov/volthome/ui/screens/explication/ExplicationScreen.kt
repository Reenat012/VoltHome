package ru.mugalimov.volthome.ui.screens.explication

import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.ui.manual.LocalManualModeGuard
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.onboarding.OnboardingRuntimeEntryPoint
import ru.mugalimov.volthome.ui.onboarding.hints.ExplicationHints
import ru.mugalimov.volthome.ui.onboarding.hints.ManualHints
import ru.mugalimov.volthome.ui.onboarding.hints.PdfHints
import ru.mugalimov.volthome.ui.onboarding.hints.pickHighestPriorityAdvanced
import ru.mugalimov.volthome.ui.onboarding.model.ExplicationOnboardingFacts
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor
import ru.mugalimov.volthome.ui.screens.explication.export_pdf.exportExplicationPdf
import ru.mugalimov.volthome.ui.screens.explication.manual.DragGhostOverlay
import ru.mugalimov.volthome.ui.screens.explication.manual.MoveDeviceTargetsBar
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetContent
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.GroupScreenState

@RequiresApi(Build.VERSION_CODES.P)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplicationScreen(
    navController: NavHostController,
    viewModel: ExplicationViewModel = hiltViewModel()
) {

    // ✅ больше не нужен: кнопка теперь всегда видна
    // val showResetManualButton by viewModel.showResetManualButton.collectAsState(initial = false)

    val showResetManualConfirmDialog by viewModel.showResetManualConfirmDialog.collectAsState(
        initial = false
    )

    val selectedBreakdown by viewModel.selectedDeviceBreakdown.collectAsState()
    val sheetPayload by viewModel.infoSheetPayload.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current

    // Runtime-доступ к onboarding coordinator.
    val onboardingCoordinator = remember {
        EntryPointAccessors.fromApplication(
            ctx.applicationContext,
            OnboardingRuntimeEntryPoint::class.java
        ).onboardingCoordinator()
    }

    val manualSession by viewModel.manualSession.collectAsState(initial = null)
    val isManual = manualSession?.manualModeActive == true

    // Канонические факты для advanced onboarding.
    val onboardingFacts by viewModel.onboardingFacts.collectAsState()

    val unassignedIds = manualSession?.draftState?.unassignedDeviceIds.orEmpty()
    val unassignedDevices by viewModel.unassignedDevices.collectAsState()

//    // ✅ Подтягиваем "Нераспределённые" только в manual (и чистим при выходе)
//    LaunchedEffect(isManual, unassignedIds) {
//        if (isManual) {
//            viewModel.refreshUnassignedDevices(unassignedIds)
//        } else {
//            viewModel.clearUnassignedDevices()
//        }
//    }

    val moveUi by viewModel.moveDeviceUi.collectAsState(initial = null)

    // ✅ Drag-state: нужен для ghost overlay и для edge-autoscroll
    val dragState by viewModel.dragState.collectAsState()
    val dragStateLatest = rememberUpdatedState(dragState)

    // ✅ Guard доступен глобально из MainApp через CompositionLocal
    val manualGuard = LocalManualModeGuard.current

    // one-shot события от VM
    val event by viewModel.events.collectAsState(initial = null)

    val plan = LocalUserPlan.current
    val caps = plan.capabilities
    val canShowProSections = caps.professionalReportSections

    // ✅ PDF: для всех, но в manual инициировать нельзя без Save/Cancel/Stay (guard)
    LaunchedEffect(event) {
        if (event != ExplicationViewModel.UiEvent.ExportPdfRequested) return@LaunchedEffect

        val activity = ctx as? ComponentActivity
        if (activity == null) {
            viewModel.consumeEvent()
            return@LaunchedEffect
        }

        // Берём projectId строго из текущей manual/db-сессии экрана.
        // projectId должен быть доступен и в AUTO, и в MANUAL.
        val projectId = viewModel.currentProjectId().orEmpty()
        if (projectId.isBlank()) {
            Log.e("PDF_EXPORT", "Export aborted: projectId is blank")
            viewModel.consumeEvent()
            return@LaunchedEffect
        }

        viewModel.onPdfExportStarted()
        try {
            exportExplicationPdf(
                activity = activity,
                vm = viewModel,
                caps = caps,
                projectId = projectId,
                manualGuard = manualGuard
            )
        } finally {
            // Важно:
            // даже если guard отменил действие или export сорвался,
            // blocking state надо снять.
            viewModel.onPdfExportFinished()
            viewModel.consumeEvent()
        }
    }

    /**
     * Экранные blocking states для advanced hints.
     *
     * Важно:
     * - drag in progress;
     * - move/reorder state active;
     * - bottom sheet open;
     * - confirm dialog open;
     * - PDF/export flow active;
     * - любой modal overlay active.
     */
    val advancedFacts = ExplicationOnboardingFacts(
        isLoading = onboardingFacts.isLoading,
        isSuccess = onboardingFacts.isSuccess,
        groupsCount = onboardingFacts.groupsCount,
        manualModeActive = onboardingFacts.manualModeActive,
        unassignedCount = onboardingFacts.unassignedCount,
        pdfAvailable = onboardingFacts.pdfAvailable,
        dragInProgress = dragState.isActive,
        moveStateActive = moveUi != null,
        bottomSheetOpen = sheetPayload != null,
        confirmDialogOpen = showResetManualConfirmDialog,
        pdfExportFlowActive = onboardingFacts.pdfExportFlowActive,
        modalOverlayActive = (sheetPayload != null) || showResetManualConfirmDialog
    )

    /**
     * Единая orchestration point для advanced hints экспликации.
     *
     * Здесь нет ручных "if-ов по месту" в разных composable.
     * Только selector-и + priority resolution.
     */
    LaunchedEffect(advancedFacts) {
        val candidates = buildList {
            // ✅ Сначала action-oriented manual hints:
            // long-press и сохранение через кнопку "Ручной".
            addAll(ManualHints.forExplicationHints(advancedFacts))

            // ✅ Затем второстепенные advanced hints.
            ExplicationHints.forUnassigned(advancedFacts)?.let { add(it) }
            ExplicationHints.forOverview(advancedFacts)?.let { add(it) }
            PdfHints.forExplication(advancedFacts)?.let { add(it) }
        }

        val hint = pickHighestPriorityAdvanced(candidates)
        if (hint == null) {
            Log.d(
                "ONBOARD_EXPLICATION",
                "ORCH_SKIP reason=NO_HINT manual=${advancedFacts.manualModeActive} groups=${advancedFacts.groupsCount} unassigned=${advancedFacts.unassignedCount} blocking=${advancedFacts.hasBlockingState}"
            )
            return@LaunchedEffect
        }

        Log.d(
            "ONBOARD_EXPLICATION",
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
            "ONBOARD_EXPLICATION",
            "ORCH_END hintId=${hint.hintId.name} accepted=$accepted"
        )
    }

    when (val s = state) {
        is GroupScreenState.Loading -> {
            Log.e("STATE_TRACE", "UI -> Loading")
            LoadingState()
        }

        is GroupScreenState.Empty -> {
            ExplicationEmptyState(
                isManual = (s.mode == GroupScreenState.Empty.EmptyMode.MANUAL),
                title = s.title,
                message = s.message,
                onRecalc = { viewModel.recalcAndSaveGroups() }, // в MANUAL VM покажет snackbar/guard
                onResetManual = { viewModel.onResetManualOverridesClick() }
            )
        }

        is GroupScreenState.Error -> {
            Log.e("STATE_TRACE", "UI -> Error message='${s.message}'")
            ErrorState(
                message = s.message,
                onRetry = { viewModel.recalcAndSaveGroups() }
            )
        }

        is GroupScreenState.Success -> {
            // ✅ VM уже отдал "display groups" (AUTO или MANUAL)
            val displayGroups = s.groups

            val sections = remember(displayGroups) { displayGroups.groupBy { it.phase ?: Phase.A } }

            Log.e("STATE_TRACE", "UI -> Success groups=${s.groups.size}")


            // ---- Диагностика (по DoD) ----
            LaunchedEffect(isManual, displayGroups.size) {
                if (isManual) {
                    val summary = displayGroups
                        .sortedBy { it.groupNumber }
                        .joinToString { g -> "${g.groupId}#${g.groupNumber}#${(g.phase ?: Phase.A).name}(devs=${g.devices.size})" }

                    Log.d(
                        "MANUAL_UI",
                        "MANUAL displayGroups size=${displayGroups.size} summary=[$summary]"
                    )
                }
            }

            // Bounds целей drop (в root-координатах)
            val targetBounds =
                remember { mutableStateMapOf<ExplicationViewModel.DragTarget, Rect>() }

            // ✅ ВАЖНО: когда панель целей скрылась — чистим bounds,
            // иначе остаются "старые" прямоугольники и activeTarget может врать.
            LaunchedEffect(isManual, moveUi) {
                if (!isManual || moveUi == null) {
//                    Log.d("DRAG_BOUNDS", "CLEAR (isManual=$isManual moveUi=$moveUi) sizeBefore=${targetBounds.size}")
                    targetBounds.clear()
                }
            }

            fun resolveActiveTarget(pointerRoot: Offset): ExplicationViewModel.DragTarget? {
                val hit =
                    targetBounds.entries.firstOrNull { (_, rect) -> rect.contains(pointerRoot) }?.key
                if (hit != null) {
                    Log.d("DRAG_HIT", "HIT pointerRoot=$pointerRoot -> $hit")
                }
                return hit
            }

            // Имя перетаскиваемого устройства (для ghost).
            val draggedDeviceName = remember(dragState.draggingDeviceId, displayGroups) {
                val id = dragState.draggingDeviceId ?: return@remember null
                displayGroups.asSequence()
                    .flatMap { it.devices.asSequence() }
                    .firstOrNull { it.id == id }
                    ?.name
            }

            val bg = MaterialTheme.colorScheme.background
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()

            // ✅ Нужна одна детерминированная цель для подсказки про long-press.
            // Вычисляем её ВНЕ LazyColumn builder, в нормальном composable scope.
            val firstAnchoredGroupId = remember(displayGroups) {
                displayGroups
                    .sortedWith(
                        compareBy<CircuitGroup>(
                            { (it.phase ?: Phase.A).ordinal },
                            { it.groupNumber }
                        )
                    )
                    .firstOrNull()
                    ?.groupId
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
            ) {
                // Панель переноса устройства (оверлей сверху)
                if (isManual && moveUi != null) {
                    val draftGroups = manualSession?.draftState?.groups.orEmpty()

                    MoveDeviceTargetsBar(
                        title = "Перенос устройства",
                        fromGroupId = moveUi!!.fromGroupId,
                        groups = draftGroups,
                        onDismiss = { viewModel.dismissMoveDevice() },
                        onMoveToGroup = { targetGroupId -> viewModel.dropToGroup(targetGroupId) },
                        onMoveToNewGroup = { viewModel.dropToNewGroup() },
                        onMoveToUnassigned = { viewModel.dropToUnassigned() },

                        activeTarget = dragState.activeTarget,

                        onTargetBounds = { target, rect ->
                            targetBounds[target] = rect
//                            Log.d("DRAG_BOUNDS", "SET target=$target rectRoot=$rect total=${targetBounds.size} fromGroupId=${moveUi?.fromGroupId}")
                        },

                        // ✅ НОВОЕ: для edge-autoscroll
                        pointerRoot = dragState.pointerCurrentRoot,
                        isDragging = dragState.isActive,

                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(20f)
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    item {
                        ShieldOverviewCard(
                            incomer = s.incomer,
                            groups = displayGroups,
                            hasGroupRcds = s.hasGroupRcds,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onboardingAnchor(
                                    targetTag = OnboardingTargetTag.EXPLICATION_SHIELD_OVERVIEW,
                                    screenId = OnboardingScreen.EXPLICATION
                                ),
                            installedPowerW = s.installedPowerW,
                            calculatedPowerW = s.calculatedPowerW,
                            showProfessionalEvidence = canShowProSections,
                            onProfessionalLockedClick = { viewModel.onPdfExportActionsClick() },
                            onOpenInfoSheet = { payload -> viewModel.openInfoSheet(payload) },
                            onIncomerFieldClick = { field ->
                                viewModel.onIncomerFieldClick(
                                    field = field,
                                    incomer = s.incomer,
                                    hasGroupRcds = s.hasGroupRcds
                                )
                            },
                            onInstalledPowerClick = { viewModel.onInstalledPowerClick(it) },
                            onCalculatedPowerClick = { viewModel.onCalculatedPowerClick(it) }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    if (isManual) {
                        item {
                            ManualModeLegalBanner(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onboardingAnchor(
                                        targetTag = OnboardingTargetTag.EXPLICATION_MANUAL_LEGAL_BANNER,
                                        screenId = OnboardingScreen.EXPLICATION
                                    )
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }


                    item {
                        ResetManualOverridesButton(
                            onClick = { viewModel.onResetManualOverridesClick() }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // Нераспределённые — только в manual
                    if (isManual) {
                        item {
                            UnassignedDevicesBlock(
                                devices = unassignedDevices,
                                onAutoAssignClick = { viewModel.onAutoAssignUnassignedClick() },

                                // ✅ Drag из unassigned: fromGroupId виртуальный
                                onDeviceDragStart = { deviceId, itemStartRoot, pointerStartRoot ->
                                    viewModel.onDeviceLongPressed(
                                        deviceId,
                                        ExplicationViewModel.FROM_UNASSIGNED
                                    )

                                    viewModel.startDrag(
                                        deviceId = deviceId,
                                        fromGroupId = ExplicationViewModel.FROM_UNASSIGNED,
                                        itemStartRoot = itemStartRoot,
                                        pointerStartRoot = pointerStartRoot
                                    )
                                },
                                onDeviceDragMove = { pointerRoot ->
                                    viewModel.updateDrag(pointerRoot)
                                    val active = resolveActiveTarget(pointerRoot)
                                    viewModel.setActiveDragTarget(active)
                                },
                                onDeviceDragEnd = {
                                    Log.d(
                                        "DRAG_TRACE",
                                        "SCREEN onDeviceDragEnd activeTarget=${dragStateLatest.value.activeTarget}"
                                    )
                                    val latest = dragStateLatest.value
                                    when (val t = latest.activeTarget) {
                                        is ExplicationViewModel.DragTarget.Group -> viewModel.dropToGroup(
                                            t.groupId
                                        )

                                        ExplicationViewModel.DragTarget.Unassigned -> viewModel.dropToUnassigned()
                                        ExplicationViewModel.DragTarget.NewGroup -> viewModel.dropToNewGroup()
                                        null -> viewModel.dropCancel()
                                    }
                                },
                                onDeviceDragCancel = {
                                    Log.d("DRAG_TRACE", "SCREEN onDeviceDragCancel")
                                    viewModel.cancelDrag()
                                },
                                onDeviceClick = { deviceId -> viewModel.onDeviceClick(deviceId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onboardingAnchor(
                                        targetTag = OnboardingTargetTag.EXPLICATION_UNASSIGNED_BLOCK,
                                        screenId = OnboardingScreen.EXPLICATION
                                    )
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // Группы по фазам
                    Phase.values().forEach { ph ->
                        val list = sections[ph].orEmpty()
                        if (list.isNotEmpty()) {
                            item {
                                PhaseHeader(phase = ph)
                                Spacer(Modifier.height(8.dp))
                            }

                            items(
                                items = list,
                                // ✅ Коммит 6:
                                // Ключ должен быть СТАБИЛЬНЫМ и НЕ зависеть от фазы/контента.
                                // Иначе при переносе группы между фазами Compose считает item "новым",
                                // пересоздаёт карточку и сбрасывает expanded/remembered state.
                                key = { stableGroupKey(it) }
                            ) { g ->
                                GroupCardCompact(
                                    group = g,
                                    isManualMode = isManual,

                                    // ✅ Только одна карточка на всём экране
                                    // получает anchor на первом device chip.
                                    anchorFirstDeviceChip = (
                                            isManual &&
                                                    g.groupId == firstAnchoredGroupId
                                            ),

                                    onDeviceLongPress = { deviceId, fromGroupId ->
                                        viewModel.onDeviceLongPressed(deviceId, fromGroupId)
                                    },

                                    onDeviceDragStart = { deviceId, fromGroupId, itemStartRoot, pointerStartRoot ->
                                        // Сохраняем старое поведение: панель целей живёт от moveDeviceUi
                                        viewModel.onDeviceLongPressed(deviceId, fromGroupId)

                                        viewModel.startDrag(
                                            deviceId = deviceId,
                                            fromGroupId = fromGroupId,
                                            itemStartRoot = itemStartRoot,
                                            pointerStartRoot = pointerStartRoot
                                        )
                                    },
                                    onDeviceDragMove = { pointerRoot ->
                                        viewModel.updateDrag(pointerRoot)
                                        val active = resolveActiveTarget(pointerRoot)
                                        viewModel.setActiveDragTarget(active)
                                    },
                                    onDeviceDragEnd = {
                                        val latest = dragStateLatest.value
                                        when (val t = latest.activeTarget) {
                                            is ExplicationViewModel.DragTarget.Group -> viewModel.dropToGroup(
                                                t.groupId
                                            )

                                            ExplicationViewModel.DragTarget.Unassigned -> viewModel.dropToUnassigned()
                                            ExplicationViewModel.DragTarget.NewGroup -> viewModel.dropToNewGroup()
                                            null -> viewModel.dropCancel() // палец подняли “в никуда” — можно закрыть панель
                                        }
                                    },
                                    onDeviceDragCancel = {
                                        // ✅ КРИТИЧНО:
                                        // Cancel (особенно у края экрана) НЕ должен гасить панель целей,
                                        // иначе edge-scroll невозможно использовать.
                                        // Сбрасываем только dragState (ghost/activeTarget), панель остаётся.
                                        viewModel.cancelDrag()
                                    },

                                    onDeviceClick = { deviceId -> viewModel.onDeviceClick(deviceId) },
                                    selectedDeviceBreakdown = selectedBreakdown,
                                    onGroupPowerClick = { viewModel.onGroupPowerClick(it) },
                                    onGroupCurrentClick = { viewModel.onGroupCurrentClick(it) },
                                    onOpenInfoSheet = { payload -> viewModel.openInfoSheet(payload) }
                                )
                                Spacer(Modifier.height(12.dp))
                            }

                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }

                // Ghost overlay
                val ghostPos = dragState.ghostPositionRoot
                if (isManual && dragState.draggingDeviceId != null && ghostPos != null) {
                    DragGhostOverlay(
                        text = draggedDeviceName ?: "Устройство",
                        positionRoot = ghostPos,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .zIndex(30f)
                    )
                }

                // FAB: PDF
                FloatingActionButton(
                    onClick = { viewModel.onExportPdfClick() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = FloatingActionButtonDefaults.shape
                        )
                        .onboardingAnchor(
                            targetTag = OnboardingTargetTag.EXPLICATION_PDF_FAB,
                            screenId = OnboardingScreen.EXPLICATION
                        ),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = "Отчёт PDF",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // BottomSheet: Info
                if (sheetPayload != null) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                viewModel.closeInfoSheet()
                            }
                        },
                        sheetState = sheetState
                    ) {
                        InfoSheetContent(payload = sheetPayload!!)
                    }
                }

                if (showResetManualConfirmDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { viewModel.onResetManualOverridesDismiss() },
                        title = { Text("Сбросить ручные изменения?") },
                        text = { Text("Проект будет полностью пересчитан автоматически.") },
                        confirmButton = {
                            Button(onClick = { viewModel.onResetManualOverridesConfirm() }) {
                                Text("Подтвердить")
                            }
                        },
                        dismissButton = {
                            Button(onClick = { viewModel.onResetManualOverridesDismiss() }) {
                                Text("Отмена")
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Стабильный ключ для LazyColumn.
 *
 * ВАЖНО:
 * - В MANUAL появляются временные группы и меняются поля (roomName/breakerType/номиналы).
 * - Ключ НЕ должен зависеть от "контента", иначе Compose будет пересоздавать item,
 *   ломать раскрытия и состояния карточек.
 */
private fun stableGroupKey(g: CircuitGroup): String =
// ✅ Коммит 6:
// Одинаково для AUTO и MANUAL: только groupId.
    // В MANUAL temp id тоже ок, главное — стабильность внутри списка.
    "gid:${g.groupId}"

@Composable
private fun ExplicationEmptyState(
    isManual: Boolean,
    title: String,
    message: String,
    onRecalc: () -> Unit,
    onResetManual: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // ✅ Важно: Column должен иметь ширину, иначе fillMaxWidth у кнопок бессмысленный
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message)

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onRecalc() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isManual) "Понятно" else "Пересчитать")
            }

            // ✅ Теперь ВСЕГДА
            Spacer(modifier = Modifier.height(12.dp))
            ResetManualOverridesButton(
                onClick = onResetManual,
                modifier = Modifier
            )
        }
    }
}

@Composable
private fun ResetManualOverridesButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Text("Сбросить ручные изменения")
    }
}

@Composable
private fun ManualModeLegalBanner(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.large
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large
            )
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = "Ручной режим",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Чтобы перенести устройство - зажми его (long-press) и перетяни в выпадающее меню сверху, выбрав нужное окно.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Чтобы сохранить или отменить изменения - кликни на кнопку Ручной.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Изменения в этой зоне ты задаёшь вручную. Результат требует инженерной проверки и не заменяет контроль проекта по месту.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}