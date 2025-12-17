package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.phase_load.LoadThresholds
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem

/**
 * Контент экрана «Нагрузки».
 * - 1 фаза: донат (индикатор вводного) + карточка «Куда уходит ток» + «Что можно улучшить»
 *   + таблица только по фазе A.
 * - 3 фазы: донат A/B/C + таблица трёх фаз (как было).
 */
@Composable
fun PhaseLoadContent(
    phaseLoads: List<PhaseLoadItem>,
    mode: PhaseMode = PhaseMode.THREE,
    incomerRating: Int? = null,
    thresholds: LoadThresholds = LoadThresholds(),
    modifier: Modifier = Modifier,
    canDrag: Boolean,
    onPaywall: () -> Unit,
    onGroupDropped: (groupId: Long, target: Phase) -> Unit,
    onReset: () -> Unit
) {
    // Drop-zones в координатах ROOT (boundsInRoot)
    val dropZones = remember { mutableStateMapOf<Phase, Rect>() }

    // Drag state
    var dragging by remember { mutableStateOf<PhaseLoadContentKt_DragPayload?>(null) }

    // ✅ ВАЖНО: разделяем координаты
    // - dragPosRoot: координаты ROOT — для попадания в drop-zones
    // - dragPosLocal: координаты контейнера overlay — для рисования overlay
    var dragPosRoot by remember { mutableStateOf(Offset.Zero) }
    var dragPosLocal by remember { mutableStateOf(Offset.Zero) }

    // Top-left контейнера, в котором рисуем overlay (в координатах ROOT)
    var overlayContainerTopLeft by remember { mutableStateOf(Offset.Zero) }

    val haptic = LocalHapticFeedback.current

    // ✅ Подсветка drop-зоны должна считаться в тех же координатах, что и Rect (ROOT)
    val hoveredPhase by remember {
        derivedStateOf {
            val p = dragging ?: return@derivedStateOf null
            dropZones.entries
                .firstOrNull { (_, rect) -> rect.contains(dragPosRoot) }
                ?.key
                ?.takeIf { it != p.fromPhase } // подсвечиваем только “чужую” фазу
        }
    }

    // В 1-ф режиме показываем только фазу A; в 3-ф — все как есть
    val shown = remember(phaseLoads, mode) {
        if (mode == PhaseMode.SINGLE) phaseLoads.filter { it.phase == Phase.A } else phaseLoads
    }

    // Токи по фазам для доната/индикатора
    val perPhase = remember(shown) {
        mapOf(
            Phase.A to (shown.find { it.phase == Phase.A }?.totalCurrent ?: 0.0),
            Phase.B to (shown.find { it.phase == Phase.B }?.totalCurrent ?: 0.0),
            Phase.C to (shown.find { it.phase == Phase.C }?.totalCurrent ?: 0.0)
        )
    }

    // Локальное состояние разворота секций по фазам
    val expandedMap = remember(mode) {
        mutableStateMapOf<Phase, Boolean>().apply {
            this[Phase.A] = (mode == PhaseMode.SINGLE)
            this[Phase.B] = false
            this[Phase.C] = false
        }
    }
    fun isExpanded(phase: Phase) = expandedMap[phase] == true
    fun togglePhase(phase: Phase) {
        expandedMap[phase] = !(expandedMap[phase] ?: false)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                // ✅ координаты контейнера overlay в ROOT
                overlayContainerTopLeft = coords.positionInRoot()
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Донат / Индикатор вводного + маркетинговый замок для FREE
            item {
                Box(Modifier.fillMaxWidth()) {
                    PhaseLoadDonutChart(
                        perPhase = perPhase,
                        mode = mode,
                        incomerRating = incomerRating,
                        warnPct = thresholds.warnPct,
                        alertPct = thresholds.alertPct
                    )

                    if (!canDrag) {
                        IconButton(
                            onClick = onPaywall,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "PRO"
                            )
                        }
                    }
                }
            }

            // Reset (PRO) / Paywall (FREE)
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Сбросить ручные изменения фаз",
                        modifier = Modifier
                            .clickable { if (canDrag) onReset() else onPaywall() }
                            .padding(vertical = 6.dp),
                        color = if (canDrag) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (mode == PhaseMode.SINGLE) {
                val aItem = shown.firstOrNull { it.phase == Phase.A }

                stickyHeader {
                    SectionHeader(
                        title = "Куда уходит ток",
                        icon = { Icon(imageVector = Icons.Outlined.PieChart, contentDescription = null) }
                    )
                }

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

            // Таблица фаз/групп — со сворачиванием по клику + DnD drop-zones
            items(shown, key = { it.phase }) { item ->
                PhaseGroupTableItem(
                    item = item,
                    expanded = isExpanded(item.phase),
                    onToggle = { togglePhase(item.phase) },

                    canDrag = canDrag,
                    onPaywall = onPaywall,

                    isDropTargetHighlighted = (dragging != null && hoveredPhase == item.phase),

                    onRegisterDropZone = { phase, rect -> dropZones[phase] = rect },

                    onDragStart = { payload ->
                        if (!canDrag) onPaywall() else dragging = payload
                    },

                    // ✅ rootPos приходит в ROOT координатах (см. PhaseGroupTableItem: localToRoot)
                    onDragMove = { rootPos ->
                        dragPosRoot = rootPos
                        dragPosLocal = rootPos - overlayContainerTopLeft
                    },

                    onDragCancel = {
                        dragging = null
                    },

                    onDragEnd = { payload ->
                        if (!canDrag) {
                            dragging = null
                            return@PhaseGroupTableItem
                        }

                        // ✅ проверка drop-а в ROOT координатах
                        val target = dropZones.entries.firstOrNull { (_, rect) ->
                            rect.contains(dragPosRoot)
                        }?.key

                        val success = (target != null && target != payload.fromPhase)

                        if (success) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onGroupDropped(payload.groupId, target!!)
                        }

                        dragging = null
                    }
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        // Drag overlay
        val p = dragging
        if (p != null) {
            Card(
                modifier = Modifier
                    .zIndex(1000f)
                    // ✅ overlay рисуем в ЛОКАЛЬНЫХ координатах контейнера
                    .offset { IntOffset(dragPosLocal.x.roundToInt(), dragPosLocal.y.roundToInt()) }
                    .widthIn(max = 280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = p.title,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
                pct >= warnPct -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
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

    val tips = buildList {
        val reserveA = (incomer - totalA).coerceAtLeast(0.0)
        val nominalRow = listOf(6, 10, 16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160)
        val nextUpNominal = nominalRow.firstOrNull { it > incomer }

        if (incomer > 0) {
            when {
                loadPct >= alertPct -> {
                    add("Высокая загрузка вводного (${fmt0(loadPct)}%). Запас всего ${fmt1(reserveA)} A.")
                    if (nextUpNominal != null) {
                        add("Если высокие пики регулярны — рассмотрите вводной на $nextUpNominal A (проверьте сечение и Icu).")
                    } else {
                        add("Следующего номинала нет — контролируйте одновременную работу мощных приборов.")
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

        if (roomShares.isNotEmpty()) {
            val totalRoomsA = roomShares.sumOf { it.second }.coerceAtLeast(0.0001)
            val top1 = roomShares[0]
            val top1PctTotal = (top1.second / totalRoomsA) * 100.0
            val top1PctIncomer = if (incomer > 0) (top1.second / incomer) * 100.0 else 0.0

            if (top1PctTotal >= 35.0) {
                add("${top1.first} даёт ${fmt0(top1PctTotal)}% общей нагрузки (${fmt1(top1.second)} A, ${fmt0(top1PctIncomer)}% вводного). Разносите мощные приборы по времени/группам.")
            }

            val top2 = roomShares.getOrNull(1)
            if (top2 != null) {
                val pairSum = top1.second + top2.second
                val pairPctTotal = (pairSum / totalRoomsA) * 100.0
                val pairPctIncomer = if (incomer > 0) (pairSum / incomer) * 100.0 else 0.0
                if (pairPctTotal >= 60.0) {
                    add("Две зоны лидируют: ${top1.first} + ${top2.first} = ${fmt0(pairPctTotal)}% нагрузки (${fmt0(pairPctIncomer)}% вводного). Сведите одновременную работу к минимуму.")
                }
            }
        }

        if (reserveA in 0.0..10.0 && incomer > 0) {
            add("Запас менее 10 A — пиковые включения (чайник+духовка/бойлер) могут вызывать срабатывания.")
        }

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
                Text(
                    "Что можно улучшить",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            tips.forEach {
                Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun fmt1(v: Double) = String.format("%.1f", v)
private fun fmt0(v: Double) = String.format("%.0f", v)