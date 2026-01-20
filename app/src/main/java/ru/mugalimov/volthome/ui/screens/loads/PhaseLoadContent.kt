package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.phase_load.LoadThresholds
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadMode
import kotlin.math.roundToInt
import androidx.compose.ui.layout.boundsInRoot

/**
 * Контент экрана «Нагрузки».
 * - 1 фаза: донат (индикатор вводного) + карточка «Куда уходит ток» + «Что можно улучшить»
 *   + таблица только по фазе A.
 * - 3 фазы: донат A/B/C + таблица трёх фаз.
 *
 * Коммит 4:
 * - добавляем секцию "3-фазные нагрузки" (Phase.THREE_PHASE) отдельным блоком
 * - исключаем 3φ из списков A/B/C (и, главное, из DnD/drop-zones)
 */
@Composable
fun PhaseLoadContent(
    decisions: List<DistributionDecision> = emptyList(),
    phaseLoads: List<PhaseLoadItem>,
    mode: PhaseMode = PhaseMode.THREE,
    phaseLoadMode: PhaseLoadMode = PhaseLoadMode.AUTO,
    incomerRating: Int? = null,
    thresholds: LoadThresholds = LoadThresholds(),
    modifier: Modifier = Modifier,
    canDrag: Boolean,
    onPaywall: () -> Unit,
    onEnterManualMode: () -> Unit,
    onGroupDropped: (groupId: Long, target: Phase) -> Unit,
    onDecisionDetailsClick: (groupNumber: Int) -> Unit,
    onReset: () -> Unit,

    // Hints (commit 2)
    manualModeHintShown: Boolean,
    firstDragHintShown: Boolean,
    markManualModeHintShown: () -> Unit,
    markFirstDragHintShown: () -> Unit,
    onDropMissed: () -> Unit, // ✅ Hint 3
) {
    // Drop-zones в координатах ROOT (boundsInRoot) — ТОЛЬКО для A/B/C
    val dropZones = remember { mutableStateMapOf<Phase, Rect>() }

    var overlayContainerSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOverlaySize by remember { mutableStateOf(IntSize.Zero) }

    val density = LocalDensity.current
    val overlayGapPx = with(density) { 12.dp.toPx() } // зазор между пальцем и карточкой

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

    // ✅ быстрый доступ: решение по groupNumber
    val decisionsByGroupNumber = remember(decisions) {
        decisions.groupBy { it.groupNumber } // Map<Int, List<DistributionDecision>>
    }

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

    // Hint 1: как только вошли в MANUAL и подсказка ещё не показывалась — фиксируем (один раз)
    LaunchedEffect(phaseLoadMode, manualModeHintShown) {
        if (phaseLoadMode == PhaseLoadMode.MANUAL && !manualModeHintShown) {
            markManualModeHintShown()
        }
    }

    // ===== Разделение данных: A/B/C отдельно, 3φ отдельно =====

    // 3φ item (если есть)
    val threePhaseItem = remember(phaseLoads) {
        phaseLoads.firstOrNull { it.phase == Phase.THREE_PHASE }
    }

    // A/B/C items (3φ сюда не попадает)
    val phaseItems = remember(phaseLoads, mode) {
        val abc = phaseLoads.filter {
            it.phase == Phase.A || it.phase == Phase.B || it.phase == Phase.C
        }
        if (mode == PhaseMode.SINGLE) abc.filter { it.phase == Phase.A } else abc
    }

    // Токи по фазам для доната/индикатора (только A/B/C)
    val perPhase = remember(phaseItems) {
        mapOf(
            Phase.A to (phaseItems.find { it.phase == Phase.A }?.totalCurrent ?: 0.0),
            Phase.B to (phaseItems.find { it.phase == Phase.B }?.totalCurrent ?: 0.0),
            Phase.C to (phaseItems.find { it.phase == Phase.C }?.totalCurrent ?: 0.0)
        )
    }

    // Локальное состояние разворота секций по фазам (только A/B/C)
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

    // Коммит 3: панель фаз видна только при drag
    val showDropTargetsPanel = (dragging != null)

    LaunchedEffect(showDropTargetsPanel) {
        // Во время drag используем ТОЛЬКО панельные зоны.
        // Вне drag зоны нам не нужны (и не должны влиять на подсветку).
        dropZones.clear()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                overlayContainerTopLeft = coords.positionInRoot()
                overlayContainerSize = coords.size
            }
    ) {
        // ===== Панель целей фаз (Variant A) — ТОЛЬКО ВИЗУАЛ =====
        // visible = (dragging != null)
        AnimatedVisibility(
            visible = showDropTargetsPanel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(900f)
                .fillMaxWidth()
        ) {
            val p = dragging
            if (p != null) {
                PhaseDropTargetsPanel(
                    fromPhase = p.fromPhase,
                    highlightedPhase = hoveredPhase,
                    onRegisterDropZone = { phase, rect ->
                        // Панель — главный источник dropZones во время drag
                        if (phase == Phase.A || phase == Phase.B || phase == Phase.C) {
                            dropZones[phase] = rect
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // чтобы панель (оверлей) не перекрывала верхний контент во время drag
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(top = if (showDropTargetsPanel) 56.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Режим работы экрана (коммит 1): Auto / Manual
            item {
                val modeLabel = when (phaseLoadMode) {
                    PhaseLoadMode.AUTO -> "Авто"
                    PhaseLoadMode.MANUAL -> "Ручной"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Режим: $modeLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // ✅ Точка входа в MANUAL (явное действие)
                    if (phaseLoadMode == PhaseLoadMode.AUTO) {
                        Text(
                            text = "Перейти в ручной режим",
                            modifier = Modifier
                                .clickable {
                                    if (canDrag) onEnterManualMode() else onPaywall()
                                }
                                .padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Hint 1 — первый вход в ручной режим (MANUAL)
            item {
                val showHint1 = (phaseLoadMode == PhaseLoadMode.MANUAL) && !manualModeHintShown
                if (showHint1) {
                    Text(
                        text = "Зажмите значок ⠿ у группы и перетащите её на фазу сверху.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
                val resetEnabled = (phaseLoadMode == PhaseLoadMode.MANUAL) && canDrag

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Сбросить изменения и вернуться в режим Авто",
                        modifier = Modifier
                            .clickable(
                                enabled = resetEnabled || !canDrag,
                            ) {
                                when {
                                    !canDrag -> onPaywall()      // Free: объясняющая модалка (коммит 3)
                                    resetEnabled -> onReset()     // PRO + MANUAL: сброс + AUTO (коммит 5)
                                    else -> Unit                  // PRO + AUTO: ничего (уже baseline)
                                }
                            }
                            .padding(vertical = 6.dp),
                        color = when {
                            !canDrag -> MaterialTheme.colorScheme.onSurfaceVariant
                            resetEnabled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                }
            }

            // Hint 2 — первый активный drag (пока dragging != null и флаг ещё не выставлен)
            item {
                val showHint2 = (dragging != null) && !firstDragHintShown
                if (showHint2) {
                    Text(
                        text = "Отпустите палец на нужной фазе.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== 1-ф режим: "Куда уходит ток" + советы (берём фазу A из phaseItems) =====
            if (mode == PhaseMode.SINGLE) {
                val aItem = phaseItems.firstOrNull { it.phase == Phase.A }

                stickyHeader {
                    SectionHeader(
                        title = "Куда уходит ток",
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.PieChart,
                                contentDescription = null
                            )
                        }
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
                                val restSum =
                                    (byRoom.drop(5).sumOf { it.second }).coerceAtLeast(0.0)

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

            val canStartDnD = (phaseLoadMode == PhaseLoadMode.MANUAL) && canDrag

            items(phaseItems, key = { it.phase }) { item ->
                PhaseGroupTableItem(
                    item = item,
                    decisionsByGroupNumber = decisionsByGroupNumber,
                    expanded = isExpanded(item.phase),
                    onToggle = { togglePhase(item.phase) },
                    onDecisionDetailsClick = onDecisionDetailsClick,

                    isDropTargetHighlighted = (dragging != null && hoveredPhase == item.phase),

                    onRegisterDropZone = { phase, rect ->
                        // ВАЖНО: во время drag drop-зоны задаёт панель, а не большие карточки фаз
                        if (showDropTargetsPanel) return@PhaseGroupTableItem

                        if (phase == Phase.A || phase == Phase.B || phase == Phase.C) {
                            dropZones[phase] = rect
                        }
                    },

                    // ✅ ЕДИНАЯ точка gating-а: здесь решаем paywall/ignore/start
                    onDragStartAttempt = { payload, startRoot ->
                        // чтобы overlay не прыгал с (0,0), даже если старт разрешён
                        dragPosRoot = startRoot
                        dragPosLocal = startRoot - overlayContainerTopLeft

                        when {
                            !canDrag -> onPaywall()                      // Free: capability=false → модалка (коммит 5)
                            phaseLoadMode != PhaseLoadMode.MANUAL -> Unit // PRO+AUTO: игнор
                            else -> dragging = payload                   // PRO+MANUAL: стартуем
                        }
                    },

                    onDragMove = { rootPos ->
                        // двигаем только если DnD реально запущен (иначе это шум)
                        if (dragging == null) return@PhaseGroupTableItem
                        dragPosRoot = rootPos
                        dragPosLocal = rootPos - overlayContainerTopLeft
                    },

                    onDragCancel = { dragging = null },

                    onDragEndAttempt = { payload ->
                        if (!canStartDnD || dragging == null) {
                            dragging = null
                            return@PhaseGroupTableItem
                        }

                        val target = dropZones.entries.firstOrNull { (_, rect) ->
                            rect.contains(dragPosRoot)
                        }?.key

                        when {
                            target == null -> {
                                // ✅ Hint 3: промах мимо панельных целей
                                onDropMissed()
                            }
                            target == payload.fromPhase -> {
                                // disabled-drop (своя фаза) — ничего, и без snackbar (по спекам "мимо фаз")
                            }
                            else -> {
                                // ✅ успешный drop — как и раньше
                                if (!firstDragHintShown) markFirstDragHintShown()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onGroupDropped(payload.groupId, target)
                            }
                        }

                        dragging = null
                    }
                )
            }

            // ✅ 3φ — ПОСЛЕДНИМ блоком
            if (threePhaseItem != null && threePhaseItem.groups.isNotEmpty()) {
                item {
                    ThreePhaseLoadsSection(
                        item = threePhaseItem,
                        decisionsByGroupNumber = decisionsByGroupNumber,
                        modifier = Modifier.fillMaxWidth(),
                        onDecisionDetailsClick = onDecisionDetailsClick
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        // Drag overlay (появляется СЛЕВА от пальца/хэндла)
        val p = dragging
        if (p != null) {
            val containerW = overlayContainerSize.width
            val containerH = overlayContainerSize.height

            val overlayW = dragOverlaySize.width
            val overlayH = dragOverlaySize.height

            val desiredX = (dragPosLocal.x - overlayW - overlayGapPx).roundToInt()
            val desiredY = (dragPosLocal.y - overlayH * 0.25f).roundToInt() // чуть выше пальца

            val clampedX = desiredX.coerceIn(0, (containerW - overlayW).coerceAtLeast(0))
            val clampedY = desiredY.coerceIn(0, (containerH - overlayH).coerceAtLeast(0))

            Card(
                modifier = Modifier
                    .zIndex(1000f)
                    .onGloballyPositioned { coords -> dragOverlaySize = coords.size }
                    .offset { IntOffset(clampedX, clampedY) }
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
private fun PhaseDropTargetsPanel(
    fromPhase: Phase,
    highlightedPhase: Phase?,
    onRegisterDropZone: (phase: Phase, rect: Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhaseDropTargetChip(
            phase = Phase.A,
            disabled = (fromPhase == Phase.A),
            highlighted = (highlightedPhase == Phase.A),
            modifier = Modifier.weight(1f),
            onRegisterDropZone = onRegisterDropZone
        )
        PhaseDropTargetChip(
            phase = Phase.B,
            disabled = (fromPhase == Phase.B),
            highlighted = (highlightedPhase == Phase.B),
            modifier = Modifier.weight(1f),
            onRegisterDropZone = onRegisterDropZone
        )
        PhaseDropTargetChip(
            phase = Phase.C,
            disabled = (fromPhase == Phase.C),
            highlighted = (highlightedPhase == Phase.C),
            modifier = Modifier.weight(1f),
            onRegisterDropZone = onRegisterDropZone
        )
    }
}

@Composable
private fun PhaseDropTargetChip(
    phase: Phase,
    disabled: Boolean,
    highlighted: Boolean,
    onRegisterDropZone: (phase: Phase, rect: Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        disabled -> MaterialTheme.colorScheme.surfaceVariant
        highlighted -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = when {
        disabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val border = when {
        disabled -> null
        highlighted -> CardDefaults.outlinedCardBorder()
        else -> null
    }

    Card(
        modifier = modifier
            .height(44.dp)
            .onGloballyPositioned { coords ->
                // dropZones всегда в ROOT координатах
                onRegisterDropZone(phase, coords.boundsInRoot())
            },
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = border,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (highlighted) 4.dp else 1.dp
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = when (phase) {
                    Phase.A -> "Фаза A"
                    Phase.B -> "Фаза B"
                    Phase.C -> "Фаза C"
                    else -> "Фаза"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
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
                add(
                    "${top1.first} даёт ${fmt0(top1PctTotal)}% общей нагрузки (${fmt1(top1.second)} A, ${fmt0(top1PctIncomer)}% вводного). Разносите мощные приборы по времени/группам."
                )
            }

            val top2 = roomShares.getOrNull(1)
            if (top2 != null) {
                val pairSum = top1.second + top2.second
                val pairPctTotal = (pairSum / totalRoomsA) * 100.0
                val pairPctIncomer = if (incomer > 0) (pairSum / incomer) * 100.0 else 0.0
                if (pairPctTotal >= 60.0) {
                    add(
                        "Две зоны лидируют: ${top1.first} + ${top2.first} = ${fmt0(pairPctTotal)}% нагрузки (${fmt0(pairPctIncomer)}% вводного). Сведите одновременную работу к минимуму."
                    )
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