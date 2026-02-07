package ru.mugalimov.volthome.ui.screens.explication

import android.os.Build
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.ui.manual.ForbiddenAction
import ru.mugalimov.volthome.ui.manual.LocalManualModeGuard
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.screens.explication.export_pdf.exportExplicationPdf
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
    LaunchedEffect(Unit) { viewModel.recalcAndSaveGroups() }

    val selectedBreakdown by viewModel.selectedDeviceBreakdown.collectAsState()
    val sheetPayload by viewModel.infoSheetPayload.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current

    val manualSession by viewModel.manualSession.collectAsState(initial = null)
    val isManual = manualSession?.manualModeActive == true

    val unassignedIds = manualSession?.draftState?.unassignedDeviceIds.orEmpty()
    val unassignedDevices by viewModel.unassignedDevices.collectAsState()

    LaunchedEffect(isManual, unassignedIds) {
        if (isManual) {
            viewModel.refreshUnassignedDevices(unassignedIds)
        } else {
            viewModel.clearUnassignedDevices()
        }
    }

    val moveUi by viewModel.moveDeviceUi.collectAsState(initial = null)

    val manualGuard = LocalManualModeGuard.current

    // one-shot события от VM
    val event by viewModel.events.collectAsState(initial = null)

    val plan = LocalUserPlan.current
    val caps = plan.capabilities
    val canShowProSections = caps.professionalReportSections

    // PDF: доступно всем, различается профиль отчёта внутри HTML-сборки
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
            caps = caps
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
            val groups = s.groups
            val bg = MaterialTheme.colorScheme.background
            val sections = remember(groups) { groups.groupBy { it.phase ?: Phase.A } }

            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()

            // высота верхней панели целей (чтобы контент не уезжал под неё)
            val moveBarHeight = 112.dp

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
                        onMoveToGroup = { targetGroupId ->
                            viewModel.onMoveDeviceTargetGroupSelected(
                                deviceId = moveUi!!.deviceId,
                                targetGroupId = targetGroupId
                            )
                        },
                        onMoveToNewGroup = {
                            viewModel.onMoveDeviceToNewGroupSelected(
                                deviceId = moveUi!!.deviceId
                            )
                        },
                        onMoveToUnassigned = {
                            viewModel.onMoveDeviceToUnassignedSelected(
                                deviceId = moveUi!!.deviceId
                            )
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // ✅ если панель переноса активна — резервируем сверху место, иначе она перекроет контент
                    if (isManual && moveUi != null) {
                        item { Spacer(Modifier.height(moveBarHeight)) }
                    }

                    // Шапка: вход в manual / статус
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isManual) {
                                TextButton(onClick = { viewModel.onEnterManualModeClick() }) {
                                    Text("Ручной режим")
                                }
                            } else {
                                Text(
                                    text = "Ручной режим активен",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Карточка “Щит в целом”
                    item {
                        ShieldOverviewCard(
                            incomer = s.incomer,
                            groups = groups,
                            hasGroupRcds = s.hasGroupRcds,
                            modifier = Modifier.fillMaxWidth(),
                            installedPowerW = s.installedPowerW,
                            calculatedPowerW = s.calculatedPowerW,
                            showProfessionalEvidence = canShowProSections,
                            onProfessionalLockedClick = {
                                viewModel.onPdfExportActionsClick()
                            },
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

                    // ✅ Коммит 5: контейнер "Нераспределённые" — только в manual, между щитом и фазой A
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
                                    onDeviceLongPress = { deviceId, fromGroupId ->
                                        viewModel.onDeviceLongPressed(
                                            deviceId = deviceId,
                                            fromGroupId = fromGroupId
                                        )
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

                // FAB: PDF (всегда доступно)
                FloatingActionButton(
                    onClick = {
                        manualGuard.request(
                            action = ForbiddenAction.EXPORT_PDF,
                            onProceed = { viewModel.onExportPdfClick() },
                            onSave = {
                                // Коммит 9: Save manual-сессии
                            },
                            onCancel = {
                                // Коммит 9: Cancel manual-сессии
                            }
                        )
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