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
    // Показываем ВСЕ фазы (A,B,C), чтобы ключи были стабильны
    val allPhases = remember(phaseLoads) {
        val byPhase = phaseLoads.associateBy { it.phase }
        listOf(Phase.A, Phase.B, Phase.C).map { p ->
            byPhase[p] ?: PhaseLoadItem(p, 0.0, 0.0, emptyList())
        }
    }

    // Стабильные saveable-флаги
    var aExpanded by rememberSaveable("phase_expanded_A") { mutableStateOf(false) }
    var bExpanded by rememberSaveable("phase_expanded_B") { mutableStateOf(false) }
    var cExpanded by rememberSaveable("phase_expanded_C") { mutableStateOf(false) }

    fun isExpanded(p: Phase) = when (p) {
        Phase.A -> aExpanded
        Phase.B -> bExpanded
        Phase.C -> cExpanded
    }

    fun toggle(p: Phase) {
        when (p) {
            Phase.A -> aExpanded = !aExpanded
            Phase.B -> bExpanded = !bExpanded
            Phase.C -> cExpanded = !cExpanded
        }
    }

    // Ток по фазам для доната
    val perPhase by remember(allPhases) {
        derivedStateOf { allPhases.associate { it.phase to it.totalCurrent } }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "donut") {
            // Контейнер на всю ширину, сам круг — фиксированного размера и по центру
            PhaseLoadDonutChart(
                perPhase = perPhase,
                modifier = Modifier.fillMaxWidth(),
                chartSizeDp = 220.dp // при желании: 200.dp / 240.dp
            )
        }

        item { Spacer(Modifier.height(4.dp)) }

        // Таблица по фазам
        items(allPhases, key = { it.phase }) { item ->
            PhaseGroupTableItem(
                item = item,
                expanded = isExpanded(item.phase),
                onToggle = { toggle(item.phase) }
            )
        }
    }
}