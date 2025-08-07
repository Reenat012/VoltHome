package ru.mugalimov.volthome.ui.screens.explication.Incomer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.IncomerKind
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.inferVoltageType
import ru.mugalimov.volthome.domain.use_case.phaseCurrents

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomerCard(
    incomer: IncomerSpec,
    groups: List<CircuitGroup>,
    hasGroupRcds: Boolean,
    modifier: Modifier = Modifier
) {
    val vType: VoltageType = inferVoltageType(groups)
    val perPhase = phaseCurrents(groups)
    val is3 = vType == VoltageType.AC_3PHASE

    val aI = perPhase.getOrZero(Phase.A)
    val bI = perPhase.getOrZero(Phase.B)
    val cI = perPhase.getOrZero(Phase.C)
    val maxPhase = if (is3) listOf(
        Phase.A to aI, Phase.B to bI, Phase.C to cI
    ).maxByOrNull { it.second }?.first else null

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Вводной аппарат", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            // Чипы — в FlowRow, чтобы не ломали ширину
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = {}, label = { Text(if (is3) "Сеть: 3-фазная" else "Сеть: 1-фазная") })
                AssistChip(onClick = {}, label = { Text("УЗО на группах: " + if (hasGroupRcds) "да" else "нет") })
                val hasWet = groups.any { it.rcdRequired }
                AssistChip(onClick = {}, label = { Text("Мокрые зоны: " + if (hasWet) "есть" else "нет") })
            }

            if (is3) {
                Spacer(Modifier.height(12.dp))
                val line = "Фазы: A ${fmt(aI)} A · B ${fmt(bI)} A · C ${fmt(cI)} A" +
                        (maxPhase?.let { "  → берём ${it.name}" } ?: "")
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            KeyValue("Схема", incomer.kind.human(hasGroupRcds))
            KeyValue("Полюса", if (is3) "3P+N (4 пол.)" else "1P+N (2 пол.)")
            KeyValue("Автомат", "${incomer.mcbRating} A, кривая ${incomer.mcbCurve}, отключающая способность ${incomer.icn/1000} кА")
            if (incomer.kind != IncomerKind.MCB_ONLY) {
                val sel = if (incomer.rcdSelectivity.name == "S") " (селективное)" else ""
                KeyValue("УЗО (ввод)", "тип ${incomer.rcdType}, ${incomer.rcdSensitivityMa} мА$sel")
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ВАЖНО: фиксируем ширину ЛЕЙБЛА, НЕ даём ему weight
        Text(
            text = label,
            modifier = Modifier.widthIn(min = 110.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(12.dp))
        // ВЕС отдаём ЗНАЧЕНИЮ — оно гибкое и уходит в перенос/эллипсис
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun IncomerKind.human(hasGroupRcds: Boolean) = when (this) {
    IncomerKind.MCB_PLUS_RCD -> "Автомат + УЗО" + if (hasGroupRcds) " (селективное, противопожарное)" else ""
    IncomerKind.RCBO -> "Дифавтомат"
    IncomerKind.MCB_ONLY -> "Только автомат (без УЗО)"
}

private fun fmt(v: Double) = String.format("%.2f", v)


