package ru.mugalimov.volthome.ui.screens.loads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessment
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadMode
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor
import ru.mugalimov.volthome.ui.components.IncomerAssessmentStatusCard
import kotlin.math.roundToInt

/**
 * Контент экрана «Нагрузки» ДЛЯ 3-ф режима.
 * SINGLE-режим рендерится отдельным экраном PhaseLoadSingleReportContent.
 *
 * ВАЖНО:
 * - здесь нет SINGLE веток, нет "отчёта", нет фолбэков "только A";
 * - поясняющий banner ручного режима теперь живёт ВНУТРИ общей ленты;
 * - banner показывается над блоками с фазами.
 */
@Composable
fun PhaseLoadContent(
    decisions: List<DistributionDecision> = emptyList(),
    phaseLoads: List<PhaseLoadItem>,
    phaseLoadMode: PhaseLoadMode = PhaseLoadMode.AUTO,
    incomerAssessment: IncomerAssessment? = null,
    modifier: Modifier = Modifier,
    showManualBanner: Boolean = false,
    canDrag: Boolean,
    onPaywall: () -> Unit,
    onGroupDropped: (groupId: Long, target: Phase) -> Unit,
    onDecisionDetailsClick: (groupNumber: Int) -> Unit,
    onDropMissed: () -> Unit,
) {
    // Drop-зоны в координатах ROOT — только для фаз A/B/C.
    val dropZones = remember { mutableStateMapOf<Phase, Rect>() }

    var overlayContainerSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOverlaySize by remember { mutableStateOf(IntSize.Zero) }

    val density = LocalDensity.current
    val overlayGapPx = with(density) { 12.dp.toPx() }

    // Состояние drag-and-drop.
    var dragging by remember { mutableStateOf<PhaseLoadContentKt_DragPayload?>(null) }

    // Координаты указателя в ROOT.
    var dragPosRoot by remember { mutableStateOf(Offset.Zero) }

    // Координаты указателя в локальной системе overlay-контейнера.
    var dragPosLocal by remember { mutableStateOf(Offset.Zero) }

    // Top-left контейнера overlay в координатах ROOT.
    var overlayContainerTopLeft by remember { mutableStateOf(Offset.Zero) }

    val haptic = LocalHapticFeedback.current

    // Быстрый доступ к distribution decisions по groupNumber.
    val decisionsByGroupNumber = remember(decisions) {
        decisions.groupBy { it.groupNumber }
    }

    // Подсветка текущей drop-зоны считается в ROOT координатах.
    val hoveredPhase by remember {
        derivedStateOf {
            val payload = dragging ?: return@derivedStateOf null
            dropZones.entries
                .firstOrNull { (_, rect) -> rect.contains(dragPosRoot) }
                ?.key
                ?.takeIf { it != payload.fromPhase }
        }
    }

    // Выделяем отдельный 3-фазный блок, если он есть.
    val threePhaseItem = remember(phaseLoads) {
        phaseLoads.firstOrNull { it.phase == Phase.THREE_PHASE }
    }

    // A/B/C блоки.
    val phaseItems = remember(phaseLoads) {
        phaseLoads.filter { it.phase == Phase.A || it.phase == Phase.B || it.phase == Phase.C }
    }

    // Токи по фазам для donut chart — только A/B/C.
    val perPhase = remember(phaseItems) {
        mapOf(
            Phase.A to (phaseItems.find { it.phase == Phase.A }?.totalCurrent ?: 0.0),
            Phase.B to (phaseItems.find { it.phase == Phase.B }?.totalCurrent ?: 0.0),
            Phase.C to (phaseItems.find { it.phase == Phase.C }?.totalCurrent ?: 0.0)
        )
    }

    // Локальное состояние раскрытия карточек фаз.
    val expandedMap = remember {
        mutableStateMapOf<Phase, Boolean>().apply {
            this[Phase.A] = false
            this[Phase.B] = false
            this[Phase.C] = false
        }
    }

    // Состояние раскрытия поясняющего блока ручного режима.
    var manualBannerExpanded by rememberSaveable { mutableStateOf(false) }

    fun isExpanded(phase: Phase): Boolean = expandedMap[phase] == true

    fun togglePhase(phase: Phase) {
        expandedMap[phase] = !(expandedMap[phase] ?: false)
    }

    // Панель фаз показываем только во время активного drag.
    val showDropTargetsPanel = dragging != null

    LaunchedEffect(showDropTargetsPanel) {
        // Во время drag drop-зоны задаёт только верхняя панель целей.
        // Вне drag старые зоны нам не нужны.
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
        // Панель целей фаз — только как визуальный верхний overlay во время drag.
        AnimatedVisibility(
            visible = showDropTargetsPanel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(900f)
                .fillMaxWidth()
        ) {
            val payload = dragging
            if (payload != null) {
                PhaseDropTargetsPanel(
                    fromPhase = payload.fromPhase,
                    highlightedPhase = hoveredPhase,
                    onRegisterDropZone = { phase, rect ->
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
                // Во время drag добавляем верхний отступ, чтобы overlay-панель
                // не перекрывала верхнюю часть контента.
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(top = if (showDropTargetsPanel) 56.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Главная сравнительная сводка фаз.
            item {
                ThreePhaseBalanceOverview(
                    perPhase = perPhase,
                    modifier = Modifier
                        .testTag(OnboardingTargetTag.LOADS_DONUT_CHART.rawTag)
                        .onboardingAnchor(
                            targetTag = OnboardingTargetTag.LOADS_DONUT_CHART,
                            screenId = OnboardingScreen.LOADS
                        )
                )
            }

            incomerAssessment?.let { assessment ->
                item {
                    IncomerAssessmentStatusCard(assessment = assessment)
                }
            }

            if (!canDrag) {
                item {
                    ManualDistributionProCard(onClick = onPaywall)
                }
            }

            // Поясняющий banner ручного режима — теперь В ЛЕНТЕ,
            // над фазными блоками.
            if (showManualBanner) {
                item {
                    LoadsManualModeLegalBanner(
                        expanded = manualBannerExpanded,
                        onToggle = {
                            manualBannerExpanded = !manualBannerExpanded
                        },
                        modifier = Modifier.fillMaxWidth()
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
                    dragEnabled = canStartDnD,
                    onToggle = { togglePhase(item.phase) },
                    onDecisionDetailsClick = onDecisionDetailsClick,
                    isDropTargetHighlighted = (dragging != null && hoveredPhase == item.phase),

                    onRegisterDropZone = { phase, rect ->
                        // Во время drag drop-зоны задаёт только панель целей сверху.
                        if (showDropTargetsPanel) return@PhaseGroupTableItem

                        if (phase == Phase.A || phase == Phase.B || phase == Phase.C) {
                            dropZones[phase] = rect
                        }
                    },

                    // Единая точка gating-а для DnD.
                    onDragStartAttempt = { payload, startRoot ->
                        // Сразу сохраняем стартовые координаты,
                        // чтобы overlay не моргал с нулевой позиции.
                        dragPosRoot = startRoot
                        dragPosLocal = startRoot - overlayContainerTopLeft

                        when {
                            !canDrag -> onPaywall()
                            phaseLoadMode != PhaseLoadMode.MANUAL -> Unit
                            else -> dragging = payload
                        }
                    },

                    onDragMove = { rootPos ->
                        if (dragging == null) return@PhaseGroupTableItem
                        dragPosRoot = rootPos
                        dragPosLocal = rootPos - overlayContainerTopLeft
                    },

                    onDragCancel = {
                        dragging = null
                    },

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
                                // Промах мимо фазной цели.
                                onDropMissed()
                            }

                            target == payload.fromPhase -> {
                                // Бросили в свою же фазу — ничего не делаем.
                            }

                            else -> {
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

            // 3-фазный блок — последним.
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

        // Карточка dragged item.
        val payload = dragging
        if (payload != null) {
            val containerW = overlayContainerSize.width
            val containerH = overlayContainerSize.height

            val overlayW = dragOverlaySize.width
            val overlayH = dragOverlaySize.height

            val desiredX = (dragPosLocal.x - overlayW - overlayGapPx).roundToInt()
            val desiredY = (dragPosLocal.y - overlayH * 0.25f).roundToInt()

            val clampedX = desiredX.coerceIn(0, (containerW - overlayW).coerceAtLeast(0))
            val clampedY = desiredY.coerceIn(0, (containerH - overlayH).coerceAtLeast(0))

            Card(
                modifier = Modifier
                    .zIndex(1000f)
                    .onGloballyPositioned { coords ->
                        dragOverlaySize = coords.size
                    }
                    .offset { IntOffset(clampedX, clampedY) }
                    .widthIn(max = 280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = payload.title,
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
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
fun LoadsManualModeLegalBanner(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.large
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                    text = "Ручное распределение включено",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.SemiBold
                    )
                }

                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = if (expanded) {
                        "Свернуть"
                    } else {
                        "Развернуть"
                    },
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Перетаскивайте группы за маркер на нужную фазу. Автоматическое распределение временно отключено.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Чтобы вернуть автоматический расчёт, сбросьте ручные изменения.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualDistributionProCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ручное распределение",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Перенос групп между фазами · PRO",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Открыть",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
