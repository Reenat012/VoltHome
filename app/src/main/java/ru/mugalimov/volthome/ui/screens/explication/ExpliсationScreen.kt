package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.phaseCurrents
import ru.mugalimov.volthome.ui.screens.explication.Incomer.IncomerCard
import ru.mugalimov.volthome.ui.viewmodel.ExplicationViewModel
import ru.mugalimov.volthome.ui.viewmodel.GroupScreenState

@Composable
fun ExplicationScreen(viewModel: ExplicationViewModel = hiltViewModel()) {
    // Гарантируем одноразовый старт расчёта
    LaunchedEffect(Unit) { viewModel.calculateGroups() }

    val state by viewModel.uiState.collectAsState()

    when (val s = state) {
        is GroupScreenState.Loading -> LoadingState()
        is GroupScreenState.Error -> ErrorState(
            message = s.message,
            onRetry = { viewModel.calculateGroups() }
        )
        is GroupScreenState.Success -> {
            val groups = s.groups
            val bg = MaterialTheme.colorScheme.background

            // Мемоизируем тяжёлые агрегаты на список групп
            val perPhase = remember(groups) { phaseCurrents(groups) }
            val aI = perPhase.getOrZero(Phase.A)
            val bI = perPhase.getOrZero(Phase.B)
            val cI = perPhase.getOrZero(Phase.C)

            val sections = remember(groups) { groups.groupBy { it.phase ?: Phase.A } }
            val hasGroupRcds = remember(groups) { groups.any { it.rcdRequired } }

            LazyColumn(
                modifier = Modifier.background(bg),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Вводной аппарат
                item {
                    ShieldOverviewCard(
                        incomer = s.incomer,
                        groups = groups,
                        hasGroupRcds = s.hasGroupRcds,   // пока хардкод; когда появится в VM — подставь из стейта
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
        }
    }
}

// Более устойчивый ключ: учитываем фазу
private fun stableGroupKey(phase: Phase, g: CircuitGroup): String =
    "ph-${phase.name}__grp-${g.groupNumber}-${g.roomName}-${g.breakerType}${g.circuitBreaker}"
