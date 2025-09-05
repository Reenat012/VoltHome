package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.phase_load.LoadThresholds
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

/**
 * Контент экрана «Нагрузки».
 * - 1 фаза: донат (индикатор вводного) + карточка «Куда уходит ток» + карточка «Что можно улучшить»
 *   + таблица только по фазе A.
 * - 3 фазы: донат A/B/C + таблица трёх фаз (как было).
 */
@Composable
fun PhaseLoadContent(
    phaseLoads: List<PhaseLoadItem>,
    mode: PhaseMode = PhaseMode.THREE,
    incomerRating: Int? = null,
    thresholds: LoadThresholds = LoadThresholds(),
    modifier: Modifier = Modifier
) {
    val shown = remember(phaseLoads, mode) {
        if (mode == PhaseMode.SINGLE) phaseLoads.filter { it.phase == Phase.A } else phaseLoads
    }

    val perPhase = remember(shown) {
        mapOf(
            Phase.A to (shown.find { it.phase == Phase.A }?.totalCurrent ?: 0.0),
            Phase.B to (shown.find { it.phase == Phase.B }?.totalCurrent ?: 0.0),
            Phase.C to (shown.find { it.phase == Phase.C }?.totalCurrent ?: 0.0)
        )
    }

    // локальное состояние разворота секций по фазам
    val expandedMap = remember(mode) { mutableStateMapOf<Phase, Boolean>().apply {
        // по умолчанию: в 1-фазе A раскрыта, в 3-фазе все свернуты
        this[Phase.A] = (mode == PhaseMode.SINGLE)
        this[Phase.B] = false
        this[Phase.C] = false
    } }
    fun isExpanded(phase: Phase) = expandedMap[phase] == true
    fun togglePhase(phase: Phase) { expandedMap[phase] = !(expandedMap[phase] ?: false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Донат / Индикатор вводного
        item {
            PhaseLoadDonutChart(
                perPhase = perPhase,
                mode = mode,
                incomerRating = incomerRating,
                warnPct = thresholds.warnPct,
                alertPct = thresholds.alertPct
            )
        }

        if (mode == PhaseMode.SINGLE) {
            val aItem = shown.firstOrNull { it.phase == Phase.A }

            // Sticky-заголовок аналитики
            stickyHeader {
                SectionHeader(title = "Куда уходит ток", icon = {
                    Icon(imageVector = Icons.Outlined.PieChart, contentDescription = null)
                })
            }

            // Карточка: «Куда уходит ток» (по помещениям)
            if (aItem != null && aItem.groups.isNotEmpty()) {
                item {
                    val totalA = aItem.totalCurrent.coerceAtLeast(0.0)
                    val byRoom = aItem.groups
                        .groupBy { it.roomName }
                        .mapValues { entry -> entry.value.sumOf { it.totalCurrent } }
                        .toList()
                        .sortedByDescending { it.second }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val top = byRoom.take(5)
                            val restSum = (byRoom.drop(5).sumOf { it.second }).coerceAtLeast(0.0)

                            top.forEach { (room, amps) ->
                                val pct = if (totalA > 0) amps / totalA * 100.0 else 0.0
                                RoomShareRow(
                                    title = room,
                                    amps = amps,
                                    pct = pct,
                                    warnPct = thresholds.warnPct,
                                    alertPct = thresholds.alertPct
                                )
                            }
                            if (restSum > 0.0) {
                                val restPct = if (totalA > 0) restSum / totalA * 100.0 else 0.0
                                RoomShareRow(
                                    title = "Остальное",
                                    amps = restSum,
                                    pct = restPct,
                                    warnPct = thresholds.warnPct,
                                    alertPct = thresholds.alertPct
                                )
                            }
                        }
                    }
                }
            }

            // Карточка: «Что можно улучшить»
            item {
                val totalA = aItem?.totalCurrent ?: 0.0
                val roomShares = if (aItem == null) emptyList() else {
                    aItem.groups.groupBy { it.roomName }
                        .mapValues { entry -> entry.value.sumOf { it.totalCurrent } }
                        .toList()
                        .sortedByDescending { it.second }
                }

                AdviceCardSinglePhase(
                    totalA = totalA,
                    incomer = incomerRating ?: 0,
                    roomShares = roomShares,
                    warnPct = thresholds.warnPct,
                    alertPct = thresholds.alertPct
                )
            }
        }

        // Таблица фаз/групп — теперь снова со сворачиванием по клику
        items(shown, key = { it.phase }) { item ->
            PhaseGroupTableItem(
                item = item,
                expanded = isExpanded(item.phase),
                onToggle = { togglePhase(item.phase) }
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) icon()
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RoomShareRow(
    title: String,
    amps: Double,
    pct: Double,
    warnPct: Int,
    alertPct: Int
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${fmt1(amps)} A • ${fmt0(pct)}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (pct / 100.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            color = when {
                pct >= alertPct -> MaterialTheme.colorScheme.error
                pct >= warnPct  -> MaterialTheme.colorScheme.tertiary
                else            -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
private fun AdviceCardSinglePhase(
    totalA: Double,
    incomer: Int,
    roomShares: List<Pair<String, Double>>,
    warnPct: Int,
    alertPct: Int
) {
    val loadPct = if (incomer > 0) (totalA / incomer) * 100.0 else 0.0
    val topRoom = roomShares.firstOrNull()
    val topRoomPct = if (roomShares.isNotEmpty()) {
        val sum = roomShares.sumOf { it.second }
        if (sum > 0) (roomShares.first().second / sum) * 100.0 else 0.0
    } else 0.0

    val tips = buildList {
        val loadPct = if (incomer > 0) (totalA / incomer) * 100.0 else 0.0
        val reserveA = (incomer - totalA).coerceAtLeast(0.0)

        // ряд номиналов проекта — только для справочной подсказки
        val nominalRow = listOf(6, 10, 16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160)
        val nextUpNominal = nominalRow.firstOrNull { it > incomer }

        // 1) Общая загрузка вводного + запас
        if (incomer > 0) {
            when {
                loadPct >= alertPct -> {
                    add("Высокая загрузка вводного (${fmt0(loadPct)}%). Запас всего ${fmt1(reserveA)} A.")
                    if (nextUpNominal != null) {
                        add("Если высокие пики регулярны — рассмотрите вводной на ${nextUpNominal} A (убедитесь в допустимом сечении и Icu).")
                    } else {
                        add("Следующего номинала в ряду нет — контролируйте одновременную работу мощных приборов.")
                    }
                }
                loadPct >= warnPct -> {
                    add("Умеренная загрузка вводного (${fmt0(loadPct)}%). Запас ${fmt1(reserveA)} A — следите за пиковыми сценариями.")
                }
                else -> {
                    add("Запас по току достаточный: ${fmt1(reserveA)} A (${fmt0(100 - loadPct)}%).")
                }
            }
        }

        // 2) Доминирующие помещения (по roomShares)
        if (roomShares.isNotEmpty()) {
            val totalRoomsA = roomShares.sumOf { it.second }.coerceAtLeast(0.0001)
            val top1 = roomShares[0]
            val top1PctOfTotal = (top1.second / totalRoomsA) * 100.0
            val top1PctOfIncomer = if (incomer > 0) (top1.second / incomer) * 100.0 else 0.0

            if (top1PctOfTotal >= 35.0) {
                add("${top1.first} даёт ${fmt0(top1PctOfTotal)}% общей нагрузки (${fmt1(top1.second)} A, ${fmt0(top1PctOfIncomer)}% вводного). Желательно разнести мощные приборы этой зоны по разным группам/времени.")
            }

            val top2 = roomShares.getOrNull(1)
            if (top2 != null) {
                val pairSum = top1.second + top2.second
                val pairPctTotal = (pairSum / totalRoomsA) * 100.0
                val pairPctIncomer = if (incomer > 0) (pairSum / incomer) * 100.0 else 0.0
                if (pairPctTotal >= 60.0) {
                    add("Две зоны лидируют: ${top1.first} + ${top2.first} = ${fmt0(pairPctTotal)}% нагрузки (${fmt0(pairPctIncomer)}% вводного). Сведите их одновременную работу к минимуму.")
                }
            }
        }

        // 3) Критический малый запас по амперам
        if (reserveA in 0.0..10.0 && incomer > 0) {
            add("Запас менее 10 A — пиковые включения (чайник+духовка/бойлер) могут давать срабатывания.")
        }

        // 4) Когда всё хорошо и «узких мест» не найдено
        if (isEmpty()) {
            add("Ситуация стабильна: концентрации нагрузки по зонам нет, запас по току комфортный.")
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Outlined.TipsAndUpdates, contentDescription = null)
                Text("Что можно улучшить", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (tips.isEmpty()) {
                Text(
                    "Существенных рисков не выявлено.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                tips.forEach {
                    Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// форматтеры
private fun fmt1(v: Double) = String.format("%.1f", v)
private fun fmt0(v: Double) = String.format("%.0f", v)