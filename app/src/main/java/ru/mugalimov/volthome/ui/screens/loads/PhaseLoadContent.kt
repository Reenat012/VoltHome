package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

@Composable
fun PhaseLoadContent(
    phaseLoads: List<PhaseLoadItem>,
    modifier: Modifier = Modifier
) {
    val visiblePhases = remember(phaseLoads) { phaseLoads.filter { it.groups.isNotEmpty() } }

    // ✅ Вариант 3 — три независимых saveable-флага (переживут process death)
    var aExpanded by rememberSaveable(visiblePhases.map { it.phase }) { mutableStateOf(false) }
    var bExpanded by rememberSaveable(visiblePhases.map { it.phase }) { mutableStateOf(false) }
    var cExpanded by rememberSaveable(visiblePhases.map { it.phase }) { mutableStateOf(false) }

    fun isExpanded(phase: Phase) = when (phase) {
        Phase.A -> aExpanded
        Phase.B -> bExpanded
        Phase.C -> cExpanded
    }
    fun toggle(phase: Phase) = when (phase) {
        Phase.A -> aExpanded = !aExpanded
        Phase.B -> bExpanded = !bExpanded
        Phase.C -> cExpanded = !cExpanded
    }

    val perPhase = remember(visiblePhases) {
        mapOf(
            Phase.A to (visiblePhases.firstOrNull { it.phase == Phase.A }?.totalCurrent ?: 0.0),
            Phase.B to (visiblePhases.firstOrNull { it.phase == Phase.B }?.totalCurrent ?: 0.0),
            Phase.C to (visiblePhases.firstOrNull { it.phase == Phase.C }?.totalCurrent ?: 0.0)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PhaseLoadDonutChart(perPhase = perPhase, modifier = Modifier.fillMaxWidth())

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = visiblePhases,
                key = { it.phase } // стабильный ключ
            ) { phaseLoad ->
                PhaseGroupTableItem(
                    item = phaseLoad,
                    expanded = isExpanded(phaseLoad.phase),
                    onToggle = { toggle(phaseLoad.phase) }
                )
            }
        }
    }
}




