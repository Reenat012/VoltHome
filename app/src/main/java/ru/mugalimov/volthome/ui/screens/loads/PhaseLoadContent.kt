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
    // показываем все фазы, чтобы ключи не прыгали
    val allPhases = remember(phaseLoads) {
        val byPhase = phaseLoads.associateBy { it.phase }
        listOf(Phase.A, Phase.B, Phase.C).map { p -> byPhase[p] ?: PhaseLoadItem(p, 0.0, 0.0, emptyList()) }
    }

    // стабильные saveable-флаги на фиксированных ключах
    var aExpanded by rememberSaveable("phase_expanded_A") { mutableStateOf(false) }
    var bExpanded by rememberSaveable("phase_expanded_B") { mutableStateOf(false) }
    var cExpanded by rememberSaveable("phase_expanded_C") { mutableStateOf(false) }

    fun isExpanded(p: Phase) = when (p) { Phase.A -> aExpanded; Phase.B -> bExpanded; Phase.C -> cExpanded }
    fun toggle(p: Phase)     = when (p) { Phase.A -> aExpanded = !aExpanded; Phase.B -> bExpanded = !bExpanded; Phase.C -> cExpanded = !cExpanded }

    // derivedStateOf вместо remember(...) с «липкими» ссылками
    val perPhase by remember(allPhases) {
        derivedStateOf {
            allPhases.associate { it.phase to it.totalCurrent }
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PhaseLoadDonutChart(perPhase = perPhase, modifier = Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            items(allPhases, key = { it.phase }) { item ->
                PhaseGroupTableItem(
                    item = item,
                    expanded = isExpanded(item.phase),
                    onToggle = { toggle(item.phase) }
                )
            }
        }
    }
}




