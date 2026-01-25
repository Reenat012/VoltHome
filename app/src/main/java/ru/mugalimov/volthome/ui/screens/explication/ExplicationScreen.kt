package ru.mugalimov.volthome.ui.screens.explication

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.screens.explication.export_pdf.exportExplicationPdf
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

    // события от VM (one-shot)
    val event by viewModel.events.collectAsState(initial = null)

    val plan = LocalUserPlan.current
    val caps = plan.capabilities
    val canShowProSections = caps.professionalReportSections

    // ✅ НОВЫЙ КОНТРАКТ:
    // - PDF можно всегда (FREE + PRO)
    // - разница только в профиле отчёта (FREE/PRO) внутри buildExplicationReportHtml (caps.reportProfile())
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    item {
                        ShieldOverviewCard(
                            incomer = s.incomer,
                            groups = groups,
                            hasGroupRcds = s.hasGroupRcds,
                            modifier = Modifier.fillMaxSize(),
                            installedPowerW = s.installedPowerW,
                            calculatedPowerW = s.calculatedPowerW,
                            showProfessionalEvidence = canShowProSections,
                            // ✅ если это был “замок PRO”, логичнее вести в PRO-действия/пейволл,
                            // а не в “скачать PDF” (который теперь доступен всем).
                            onProfessionalLockedClick = {
                                viewModel.onPdfExportActionsClick()
                            },
                            onOpenInfoSheet = { payload ->
                                viewModel.openInfoSheet(payload)
                            },
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

                    Phase.values().forEach { ph ->
                        val list = sections[ph].orEmpty()
                        if (list.isNotEmpty()) {
                            item {
                                PhaseHeader(phase = ph)
                                Spacer(Modifier.height(8.dp))
                            }
                            items(
                                list,
                                key = { stableGroupKey(ph, it) }
                            ) { g ->
                                GroupCardCompact(
                                    group = g,
                                    onDeviceClick = { deviceId -> viewModel.onDeviceClick(deviceId) },
                                    selectedDeviceBreakdown = selectedBreakdown,
                                    onGroupPowerClick = { viewModel.onGroupPowerClick(it) },
                                    onGroupCurrentClick = { viewModel.onGroupCurrentClick(it) },
                                    onOpenInfoSheet = { payload ->
                                        viewModel.openInfoSheet(payload)
                                    }
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }

                FloatingActionButton(
                    // ✅ теперь это всегда “скачать/распечатать PDF”, без превью
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