package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

@Composable
fun PhaseLoadContent(
    phaseLoads: List<PhaseLoadItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Диаграмма
        PhaseLoadDonutChart(phaseLoads)

        Spacer(modifier = Modifier.height(24.dp))

        // Таблица по фазам
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(phaseLoads) { phaseLoad ->
                PhaseGroupTableItem(phaseLoad)
            }
        }
    }
}
