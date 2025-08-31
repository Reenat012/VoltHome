package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.use_case.balanceUi
import ru.mugalimov.volthome.domain.use_case.calcPhaseBalance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalsCard(
    totalGroups: Int,
    totalPowerW: Int,
    perPhaseCurrents: Triple<Double, Double, Double>
) {
    val (a, b, c) = perPhaseCurrents
    val bal = remember(a, b, c) { calcPhaseBalance(a, b, c) }
    val maxPhase = bal.maxPhase
    val sum = (a + b + c).coerceAtLeast(0.0001) // защита от деления на 0

    val (statusText, statusColor) = balanceUi(bal.pct)

    // BottomSheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var infoTopic by remember { mutableStateOf<TotalInfoTopic?>(null) }

    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Итог по щиту", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = { /* noop */ },
                    label = { Text(statusText) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = statusColor.copy(alpha = 0.15f))
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { infoTopic = TotalInfoTopic.HEADER; scope.launch { sheetState.show() } }) {
                    Icon(Icons.Outlined.Info, contentDescription = "Что это?")
                }
            }
            Text(
                "Сводка нагрузки и токов по фазам: помогает увидеть перекос и «узкое место».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            // Кликабельные SummaryRow
            ClickableSummaryRow("Группы", "$totalGroups") {
                infoTopic = TotalInfoTopic.GROUPS; scope.launch { sheetState.show() }
            }
            ClickableSummaryRow("Мощность", "%.1f кВт".format(totalPowerW / 1000.0)) {
                infoTopic = TotalInfoTopic.POWER; scope.launch { sheetState.show() }
            }

            Spacer(Modifier.height(12.dp))
            Text("Токи по фазам", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            // Мини‑график распределения (нормируем ширины)
            MiniPhaseBars(a = a, b = b, c = c, sum = sum, maxPhase = maxPhase)

            Spacer(Modifier.height(8.dp))
            PhaseLegendRow(Phase.A, a, highlight = maxPhase == Phase.A)
            Spacer(Modifier.height(4.dp))
            PhaseLegendRow(Phase.B, b, highlight = maxPhase == Phase.B)
            Spacer(Modifier.height(4.dp))
            PhaseLegendRow(Phase.C, c, highlight = maxPhase == Phase.C)

            Spacer(Modifier.height(6.dp))
            Text(
                if (bal.pct >= 10.0)
                    "Перекос фаз ${"%.0f".format(bal.pct)}% — рекомендуется перераспределить группы."
                else
                    "Баланс фаз в норме (перекос ${"%.0f".format(bal.pct)}%).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // ----- BottomSheet -----
    if (infoTopic != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { infoTopic = null }
            },
            sheetState = sheetState
        ) {
            val (title, text) = totalsSheetContent(infoTopic!!, a, b, c, bal.pct / 100.0 /* сохраним старую сигнатуру, если нужно */)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

enum class TotalInfoTopic { HEADER, GROUPS, POWER }

// кликалка для строчки
@Composable
private fun ClickableSummaryRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

// мини‑график: три горизонтальные полоски по долям, с цветами фаз
@Composable
private fun MiniPhaseBars(a: Double, b: Double, c: Double, sum: Double, maxPhase: Phase) {
    Column(Modifier.fillMaxWidth()) {
        PhaseBar(Phase.A, a / sum, highlight = maxPhase == Phase.A)
        Spacer(Modifier.height(4.dp))
        PhaseBar(Phase.B, b / sum, highlight = maxPhase == Phase.B)
        Spacer(Modifier.height(4.dp))
        PhaseBar(Phase.C, c / sum, highlight = maxPhase == Phase.C)
    }
}

@Composable
private fun PhaseBar(phase: Phase, ratio: Double, highlight: Boolean) {
    val color = phaseColor(phase)
    val track = color.copy(alpha = 0.18f)
    val bar = if (highlight) color else color.copy(alpha = 0.8f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(track, MaterialTheme.shapes.extraLarge)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0.0, 1.0).toFloat())
                .fillMaxHeight()
                .background(bar, MaterialTheme.shapes.extraLarge)
        )
    }
}

// цвета фаз — единые
private fun phaseColor(p: Phase): Color = when (p) {
    Phase.A -> Color(0xFFF6D96B) // жёлтый
    Phase.B -> Color(0xFF7ED492) // зелёный
    Phase.C -> Color(0xFFFF8A80) // красный
}

// утилита для суммарной мощности (если нужна локально)
fun List<CircuitGroup>.totalPowerW(): Int = this.sumOf { it.devices.sumOf { d -> d.power ?: 0 } }

fun totalsSheetContent(
    topic: TotalInfoTopic,
    a: Double, b: Double, c: Double,
    unbalance: Double
): Pair<String, String> = when (topic) {
    TotalInfoTopic.HEADER -> "Как читать итог" to
            "Смотри на баланс фаз и суммарную мощность. Максимальная фаза — потенциальное «узкое место». " +
            "Если перекос выше 15–25%, стоит перераспределить группы между фазами."
    TotalInfoTopic.GROUPS -> "Группы" to
            "Количество групп помогает оценить заполненность щита и селективность. При росте числа групп проверь место под модули и отдельные УЗО."
    TotalInfoTopic.POWER -> "Мощность" to
            "Сумма мощностей всех устройств. Используется для оценки общей нагрузки и подбора вводного автомата/кабеля. " +
            "Помни про коэффициенты спроса: фактическая одновременная нагрузка обычно ниже номинальной."
}