package ru.mugalimov.volthome.ui.screens.explication

import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import ru.mugalimov.volthome.ui.manual.ForbiddenAction
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
    // ВАЖНО: НЕЛЬЗЯ склеивать baseGroups и draft по groupId.
// groupId может "переиспользоваться"/меняться после пересчётов/реинсертов,
// из-за чего UI может стать пустым (draft есть, а match по id — нет).
// Поэтому используем стабильный ключ, который не зависит от БД id.
    data class DraftKey(
        val groupNumber: Int,
        val roomId: Long
    )

    val selectedBreakdown by viewModel.selectedDeviceBreakdown.collectAsState()
    val sheetPayload by viewModel.infoSheetPayload.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current

    val manualSession by viewModel.manualSession.collectAsState(initial = null)
    val isManual = manualSession?.manualModeActive == true

    // ✅ Auto-recalc запускаем только в AUTO.
    // В manual пересчёт запрещён: иначе можно затереть черновик/ручные правки.
    LaunchedEffect(isManual) {
        if (!isManual) viewModel.recalcAndSaveGroups()
    }

    val unassignedIds = manualSession?.draftState?.unassignedDeviceIds.orEmpty()
    val unassignedDevices by viewModel.unassignedDevices.collectAsState()

    // ✅ Подтягиваем "Нераспределённые" только в manual (и чистим при выходе)
    LaunchedEffect(isManual, unassignedIds) {
        if (isManual) {
            viewModel.refreshUnassignedDevices(unassignedIds)
        } else {
            viewModel.clearUnassignedDevices()
        }
    }

    val moveUi by viewModel.moveDeviceUi.collectAsState(initial = null)

    // ✅ Drag-state (Коммит 3): нужен для ghost overlay
    val dragState by viewModel.dragState.collectAsState()

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
            manualGuard = manualGuard // ✅ EXPORT_PDF блокируется единым Guard-диалогом
        )

        viewModel.consumeEvent()
    }

    when (val s = state) {
        is GroupScreenState.Loading -> LoadingState()

        is GroupScreenState.Error -> ErrorState(
            message = s.message,
            onRetry = { viewModel.recalcAndSaveGroups() }
        )

        is GroupScreenState.Success -> {
            val baseGroups = s.groups
            val draft = manualSession?.draftState

            // Временная диагностика: поможет увидеть, что baseGroups и draft живут с разными groupId.
// Удалить после проверки.
            if (isManual && draft != null) {
                Log.d("MANUAL_UI", "baseGroups ids=${baseGroups.map { it.groupId }}")
                Log.d("MANUAL_UI", "draft ids=${draft.groups.map { it.groupId }}")
            }

            val displayGroups = remember(isManual, baseGroups, draft, unassignedDevices) {
                if (!isManual || draft == null) {
                    baseGroups
                } else {
                    // Карта устройств по id берём из того, что у нас уже есть в UI
                    val deviceById = buildMap<Long, ru.mugalimov.volthome.domain.model.Device> {
                        baseGroups.asSequence()
                            .flatMap { it.devices.asSequence() }
                            .forEach { put(it.id, it) }
                        unassignedDevices.forEach { put(it.id, it) }
                    }

// Собираем draft по стабильному ключу.
// Если в проекте groupNumber уникален глобально — roomId можно было бы убрать,
// но roomId добавляем для надёжности (меньше шанс коллизий).
                    val draftByKey =
                        draft.groups.associateBy { DraftKey(it.groupNumber, it.roomId) }

                    baseGroups.mapNotNull { g ->
                        val dg = draftByKey[DraftKey(g.groupNumber, g.roomId)]
                            ?: return@mapNotNull null // ✅ если группы нет в draft — не показываем

                        // Пересобираем список устройств строго по draft.deviceIds
                        val newDevices = dg.deviceIds.mapNotNull { deviceById[it] }

                        g.copy(
                            groupId = dg.groupId,   // ✅ КРИТИЧНО: из draft, иначе fromGroupId=0 и перенос = NoOp
                            phase = dg.phase,       // ✅ фаза из draft — источник истины в manual
                            devices = newDevices
                        )
                    }
                    // ⚠️ Если draft умеет создавать новые группы, которых нет в baseGroups:
                    // их нужно дополнительно добавить сюда (через конструктор CircuitGroup или mapper).
                }
            }

            val sections = remember(displayGroups) { displayGroups.groupBy { it.phase ?: Phase.A } }

            // Bounds целей drop (в root-координатах)
            val targetBounds =
                remember { mutableStateMapOf<ExplicationViewModel.DragTarget, Rect>() }

            // ✅ ВАЖНО: когда панель целей скрылась — чистим bounds,
// иначе остаются "старые" прямоугольники и activeTarget может врать.
            LaunchedEffect(isManual, moveUi) {
                if (!isManual || moveUi == null) {
                    Log.d("DRAG_BOUNDS", "CLEAR (isManual=$isManual moveUi=$moveUi) sizeBefore=${targetBounds.size}")
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
            // Берём из текущих групп в UI (CircuitGroup.devices).
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
            ) {
                // Панель переноса устройства (оверлей сверху)
                // Панель переноса устройства (НАСТОЯЩИЙ overlay поверх экрана)
                if (isManual && moveUi != null) {
                    val draftGroups = manualSession?.draftState?.groups.orEmpty()

                    MoveDeviceTargetsBar(
                        title = "Перенос устройства",
                        fromGroupId = moveUi!!.fromGroupId,
                        groups = draftGroups,
                        onDismiss = { viewModel.dismissMoveDevice() },

                        // ✅ tap по цели = drop (и так было)
                        onMoveToGroup = { targetGroupId ->
                            viewModel.dropToGroup(targetGroupId)
                        },
                        onMoveToNewGroup = {
                            viewModel.dropToNewGroup()
                        },
                        onMoveToUnassigned = {
                            viewModel.dropToUnassigned()
                        },

                        // ✅ подсветка activeTarget
                        activeTarget = dragState.activeTarget,

                        // ✅ сбор bounds целей
                        onTargetBounds = { target, rect ->
                            targetBounds[target] = rect
                            Log.d(
                                "DRAG_BOUNDS",
                                "SET target=$target rectRoot=$rect total=${targetBounds.size} fromGroupId=${moveUi?.fromGroupId}"
                            )
                        },

                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(20f)
                    )
                }


                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // ❌ УБРАЛИ Spacer. Он и был причиной "всё уезжает вниз".

                    // Карточка “Щит в целом”
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

                    // ✅ Коммит 5: контейнер "Нераспределённые" — только в manual,
                    // между щитом и фазой A
                    if (isManual) {
                        item {
                            UnassignedDevicesBlock(
                                devices = unassignedDevices,
                                onAutoAssignClick = { viewModel.onAutoAssignUnassignedClick() },
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
                                key = { stableGroupKey(ph, it) }
                            ) { g ->
                                GroupCardCompact(
                                    group = g,
                                    isManualMode = isManual,

                                    // ✅ Старое: включаем текущий move-mode (панель целей)
                                    onDeviceLongPress = { deviceId, fromGroupId ->
                                        viewModel.onDeviceLongPressed(
                                            deviceId = deviceId,
                                            fromGroupId = fromGroupId
                                        )
                                    },

                                    // ✅ Новое: drag-цепочка событий
                                    onDeviceDragStart = { deviceId, fromGroupId, itemStartRoot, pointerStartRoot ->
                                        // ВАЖНО: в этом коммите сохраняем текущее UI-поведение:
                                        // панель целей как раньше живёт от moveDeviceUi.
                                        viewModel.onDeviceLongPressed(
                                            deviceId = deviceId,
                                            fromGroupId = fromGroupId
                                        )

                                        Log.d(
                                            "DRAG",
                                            "START screen deviceId=$deviceId fromGroupId=$fromGroupId itemStartRoot=$itemStartRoot pointerStartRoot=$pointerStartRoot"
                                        )

                                        // Новый контракт: старт drag-state
                                        viewModel.startDrag(
                                            deviceId = deviceId,
                                            fromGroupId = fromGroupId,
                                            itemStartRoot = itemStartRoot,
                                            pointerStartRoot = pointerStartRoot
                                        )
                                    },
                                    onDeviceDragMove = { pointerRoot: Offset ->
                                        viewModel.updateDrag(pointerRoot)

                                        // ✅ определяем активную цель и подсвечиваем её
                                        val active = resolveActiveTarget(pointerRoot)
                                        Log.d(
                                            "DRAG_HIT",
                                            "MOVE pointerRoot=$pointerRoot active=$active targets=${targetBounds.size} moveUi=${moveUi != null}"
                                        )
                                        if (active == null && targetBounds.isNotEmpty()) {
                                            // Временно: покажем один любой прямоугольник для ориентира
                                            val any = targetBounds.entries.first()
                                            Log.d("DRAG_HIT", "NO_HIT sampleTarget=${any.key} rectRoot=${any.value}")
                                        }
                                        viewModel.setActiveDragTarget(active)
                                    },
                                    onDeviceDragEnd = {
                                        Log.d("DRAG_DROP", "END activeTarget=${dragState.activeTarget} targets=${targetBounds.size}")

                                        when (val t = dragState.activeTarget) {
                                            is ExplicationViewModel.DragTarget.Group -> viewModel.dropToGroup(t.groupId)
                                            ExplicationViewModel.DragTarget.Unassigned -> viewModel.dropToUnassigned()
                                            ExplicationViewModel.DragTarget.NewGroup -> viewModel.dropToNewGroup()
                                            null -> viewModel.dropCancel()
                                        }
                                    },
                                    onDeviceDragCancel = {
                                        viewModel.dropCancel()
                                    },

                                    onDeviceClick = { deviceId ->
                                        viewModel.onDeviceClick(deviceId)
                                    },
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

                // ✅ Ghost overlay (Коммит 3):
                // Рисуем только в manual и только когда drag активен.
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

                // FAB: PDF (всегда доступно)
                FloatingActionButton(
                    onClick = {
                        // ✅ Всегда просим экспорт через VM.
                        // Guard сработает внутри exportExplicationPdf (через параметр manualGuard).
                        viewModel.onExportPdfClick()
                    },
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

private fun stableGroupKey(phase: Phase, g: CircuitGroup): String =
    "ph-${phase.name}__grp-${g.groupNumber}-${g.roomName}-${g.breakerType}${g.circuitBreaker}"