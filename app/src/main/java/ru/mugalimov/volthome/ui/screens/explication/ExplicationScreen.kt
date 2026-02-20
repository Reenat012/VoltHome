package ru.mugalimov.volthome.ui.screens.explication

import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.ui.manual.LocalManualModeGuard
import ru.mugalimov.volthome.ui.model.LocalUserPlan
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


    val selectedBreakdown by viewModel.selectedDeviceBreakdown.collectAsState()
    val sheetPayload by viewModel.infoSheetPayload.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current

    val manualSession by viewModel.manualSession.collectAsState(initial = null)
    val isManual = manualSession?.manualModeActive == true

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

        exportExplicationPdf(
            activity = activity,
            vm = viewModel,
            caps = caps,
            manualGuard = manualGuard
        )

        viewModel.consumeEvent()
    }

    when (val s = state) {
        is GroupScreenState.Loading -> {
            Log.e("STATE_TRACE", "UI -> Loading")
            LoadingState()
        }

        is GroupScreenState.Error -> {
            Log.e("STATE_TRACE", "UI -> Error message='${s.message}'")
            ErrorState(
                message = s.message,
                onRetry = { viewModel.recalcAndSaveGroups() }
            )
        }

        is GroupScreenState.Success -> {
            val baseGroups = s.groups
            Log.e("STATE_TRACE", "UI -> Success groups=${s.groups.size}")

            // ✅ MANUAL-группы теперь собираются в VM (без склейки с БД)
            val manualDisplayGroups by viewModel.manualDisplayGroups.collectAsState()

            val displayGroups =
                if (!isManual) {
                    // ✅ AUTO: как было — группы из БД/авто-расчёта
                    baseGroups
                } else {
                    // ✅ MANUAL: строго от draft, без участия baseGroups
                    // На Коммите 0 устройства здесь будут пустые — это нормально.
                    manualDisplayGroups
                }

            // ---- Диагностика (по DoD) ----
            LaunchedEffect(isManual, displayGroups.size) {
                if (isManual) {
                    val summary = displayGroups
                        .sortedBy { it.groupNumber }
                        .joinToString { g -> "${g.groupId}#${g.groupNumber}#${(g.phase ?: Phase.A).name}(devs=${g.devices.size})" }

                    Log.d("MANUAL_UI", "MANUAL displayGroups size=${displayGroups.size} summary=[$summary]")
                }
            }

            val sections = remember(displayGroups) { displayGroups.groupBy { it.phase ?: Phase.A } }

            // Bounds целей drop (в root-координатах)
            val targetBounds = remember { mutableStateMapOf<ExplicationViewModel.DragTarget, Rect>() }

            // ✅ ВАЖНО: когда панель целей скрылась — чистим bounds,
            // иначе остаются "старые" прямоугольники и activeTarget может врать.
            LaunchedEffect(isManual, moveUi) {
                if (!isManual || moveUi == null) {
//                    Log.d("DRAG_BOUNDS", "CLEAR (isManual=$isManual moveUi=$moveUi) sizeBefore=${targetBounds.size}")
                    targetBounds.clear()
                }
            }

            fun resolveActiveTarget(pointerRoot: Offset): ExplicationViewModel.DragTarget? {
                val hit = targetBounds.entries.firstOrNull { (_, rect) -> rect.contains(pointerRoot) }?.key
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

            androidx.compose.foundation.layout.Box(
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
                            modifier = Modifier.fillMaxWidth(),
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

                    // Нераспределённые — только в manual
                    if (isManual) {
                        item {
                            UnassignedDevicesBlock(
                                devices = unassignedDevices,
                                onAutoAssignClick = { viewModel.onAutoAssignUnassignedClick() },

                                // ✅ Drag из unassigned: fromGroupId виртуальный
                                onDeviceDragStart = { deviceId, itemStartRoot, pointerStartRoot ->
                                    // 1) включаем панель целей
                                    viewModel.onDeviceLongPressed(deviceId, ExplicationViewModel.FROM_UNASSIGNED)

                                    // 2) запускаем dragState
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
                                    Log.d("DRAG_TRACE", "SCREEN onDeviceDragEnd activeTarget=${dragStateLatest.value.activeTarget}")
                                    val latest = dragStateLatest.value
                                    when (val t = latest.activeTarget) {
                                        is ExplicationViewModel.DragTarget.Group -> viewModel.dropToGroup(t.groupId)
                                        ExplicationViewModel.DragTarget.Unassigned -> viewModel.dropToUnassigned()
                                        ExplicationViewModel.DragTarget.NewGroup -> viewModel.dropToNewGroup()
                                        null -> viewModel.dropCancel()
                                    }
                                },
                                onDeviceDragCancel = {
                                    Log.d("DRAG_TRACE", "SCREEN onDeviceDragCancel")
                                    viewModel.cancelDrag()
                                },

                                // (опционально) клик
                                onDeviceClick = { deviceId -> viewModel.onDeviceClick(deviceId) },

                                modifier = Modifier.fillMaxWidth()
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
                                            is ExplicationViewModel.DragTarget.Group -> viewModel.dropToGroup(t.groupId)
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