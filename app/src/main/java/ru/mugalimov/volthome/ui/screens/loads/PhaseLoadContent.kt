package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.boundsInRoot
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
import androidx.compose.ui.platform.testTag
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor

/**
 * Контент экрана «Нагрузки» ДЛЯ 3-ф режима.
 * SINGLE-режим рендерится отдельным экраном PhaseLoadSingleReportContent.
 *
 * ВАЖНО:
 * - здесь нет SINGLE веток, нет "отчёта", нет фолбэков "только A".
 */
@Composable
fun PhaseLoadContent(
    decisions: List<DistributionDecision> = emptyList(),
    phaseLoads: List<PhaseLoadItem>,
    phaseLoadMode: PhaseLoadMode = PhaseLoadMode.AUTO,
    incomerRating: Int? = null,
    thresholds: LoadThresholds = LoadThresholds(),
    modifier: Modifier = Modifier,
    canDrag: Boolean,
    onPaywall: () -> Unit,
    onGroupDropped: (groupId: Long, target: Phase) -> Unit,
    onDecisionDetailsClick: (groupNumber: Int) -> Unit,
    onReset: () -> Unit,

    // Hints
    manualModeHintShown: Boolean,
    firstDragHintShown: Boolean,
    markManualModeHintShown: () -> Unit,
    markFirstDragHintShown: () -> Unit,
    onDropMissed: () -> Unit,
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
    val phaseItems = remember(phaseLoads) {
        phaseLoads.filter { it.phase == Phase.A || it.phase == Phase.B || it.phase == Phase.C }
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
    val expandedMap = remember {
        mutableStateMapOf<Phase, Boolean>().apply {
            this[Phase.A] = false
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
//            item {
//                val modeLabel = when (phaseLoadMode) {
//                    PhaseLoadMode.AUTO -> "Авто"
//                    PhaseLoadMode.MANUAL -> "Ручной"
//                }
//
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.SpaceBetween
//                ) {
//                    Text(
//                        text = "Режим: $modeLabel",
//                        style = MaterialTheme.typography.labelLarge,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
//                }
//            }

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
                        mode = PhaseMode.THREE,
                        incomerRating = incomerRating,
                        warnPct = thresholds.warnPct,
                        alertPct = thresholds.alertPct,
                        modifier = Modifier
                            .testTag(OnboardingTargetTag.LOADS_DONUT_CHART.rawTag)
                            .onboardingAnchor(
                                targetTag = OnboardingTargetTag.LOADS_DONUT_CHART,
                                screenId = OnboardingScreen.LOADS
                            )
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

//                Row(
//                    Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.End
//                ) {
//                    Text(
//                        text = "Сбросить изменения и вернуться в режим Авто",
//                        modifier = Modifier
//                            .clickable(
//                                enabled = resetEnabled || !canDrag,
//                            ) {
//                                when {
//                                    !canDrag -> onPaywall()      // Free: объясняющая модалка (коммит 3)
//                                    resetEnabled -> onReset()     // PRO + MANUAL: сброс + AUTO (коммит 5)
//                                    else -> Unit                  // PRO + AUTO: ничего (уже baseline)
//                                }
//                            }
//                            .padding(vertical = 6.dp),
//                        color = when {
//                            !canDrag -> MaterialTheme.colorScheme.onSurfaceVariant
//                            resetEnabled -> MaterialTheme.colorScheme.primary
//                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
//                        }
//                    )
//                }
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

            val canStartDnD = (phaseLoadMode == PhaseLoadMode.MANUAL) && canDrag

            items(phaseItems, key = { it.phase }) { item ->
                val isFirstGroupTarget = item.phase == phaseItems.firstOrNull()?.phase

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
                    },

                    modifier = if (isFirstGroupTarget) {
                        Modifier
                            .testTag(OnboardingTargetTag.LOADS_FIRST_GROUP.rawTag)
                            .onboardingAnchor(
                                targetTag = OnboardingTargetTag.LOADS_FIRST_GROUP,
                                screenId = OnboardingScreen.LOADS
                            )
                    } else {
                        Modifier
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

private fun fmt1(v: Double) = String.format("%.1f", v)
private fun fmt0(v: Double) = String.format("%.0f", v)