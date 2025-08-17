package ru.mugalimov.volthome.ui.screens.explication

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import ru.mugalimov.volthome.domain.use_case.phaseCurrents
import ru.mugalimov.volthome.ui.screens.explication.export_pdf.exportExplicationPdf
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.GroupScreenState

@Composable
fun ExplicationScreen(viewModel: ExplicationViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.calculateGroups() }

    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current

    when (val s = state) {
        is GroupScreenState.Loading -> LoadingState()
        is GroupScreenState.Error -> ErrorState(
            message = s.message,
            onRetry = { viewModel.calculateGroups() }
        )
        is GroupScreenState.Success -> {
            val groups = s.groups
            val bg = MaterialTheme.colorScheme.background

            // Агрегаты
            val perPhase = remember(groups) { phaseCurrents(groups) }
            val sections = remember(groups) { groups.groupBy { it.phase ?: Phase.A } }

            // Обертка для FAB поверх контента
            Box(modifier = Modifier
                .fillMaxSize()
                .background(bg)
            ) {
                // Твой основной контент
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Вводной аппарат
                    item {
                        ShieldOverviewCard(
                            incomer = s.incomer,
                            groups = groups,
                            hasGroupRcds = s.hasGroupRcds,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    // Секции по фазам
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
                                GroupCardCompact(group = g)
                                Spacer(Modifier.height(12.dp))
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { (ctx as? ComponentActivity)?.let { exportExplicationPdf(it, viewModel) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 16.dp, bottom = 16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.pdf_svgrepo_com),
                        contentDescription = "Экспорт PDF",
                        modifier = Modifier.size(36.dp),   // подгони размер, если нужно (20–28dp)
                        tint = Color.Unspecified
                    )
                }
            }
        }
    }
}

// Более устойчивый ключ: учитываем фазу
private fun stableGroupKey(phase: Phase, g: CircuitGroup): String =
    "ph-${phase.name}__grp-${g.groupNumber}-${g.roomName}-${g.breakerType}${g.circuitBreaker}"