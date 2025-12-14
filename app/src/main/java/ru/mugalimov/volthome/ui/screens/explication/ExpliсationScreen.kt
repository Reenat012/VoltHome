package ru.mugalimov.volthome.ui.screens.explication

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.R
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.ProFeature
import ru.mugalimov.volthome.ui.components.ProLocked
import ru.mugalimov.volthome.ui.model.LocalUserPlan
import ru.mugalimov.volthome.ui.screens.explication.export_pdf.exportExplicationPdf
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.GroupScreenState

@Composable
fun ExplicationScreen(viewModel: ExplicationViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.recalcAndSaveGroups() }

    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current

    // события от VM (one-shot)
    val event by viewModel.events.collectAsState(initial = null)

    val isPro = LocalUserPlan.current.isPro

    LaunchedEffect(event, isPro) {
        if (event == ExplicationViewModel.UiEvent.ExportPdfRequested) {
            (ctx as? ComponentActivity)?.let { exportExplicationPdf(it, viewModel, isPro) }
            viewModel.consumeEvent()
        }
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
                            modifier = Modifier.fillMaxSize()
                        )
                        Spacer(Modifier.height(16.dp))
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
                                    onDeviceClick = { deviceId -> viewModel.onDeviceClick(deviceId) }
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }

                val isPro = LocalUserPlan.current.isPro

                ProLocked(
                    isPro = isPro,
                    feature = ProFeature.PDF_EXPORT,
                    onLockedClick = { viewModel.onExportPdfClick() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 16.dp, bottom = 16.dp),
                    shape = FloatingActionButtonDefaults.shape, // чтобы overlay совпал с формой FAB
                    showLockIcon = false // ✅ убираем замок, кнопка просто "серая"
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.onExportPdfClick() },
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation( // ✅ убираем тень полностью
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.pdf_svgrepo_com),
                            contentDescription = "Экспорт PDF",
                            modifier = Modifier.size(36.dp),
                            tint = Color.Unspecified
                        )
                    }
                }
            }
        }
    }
}

private fun stableGroupKey(phase: Phase, g: CircuitGroup): String =
    "ph-${phase.name}__grp-${g.groupNumber}-${g.roomName}-${g.breakerType}${g.circuitBreaker}"