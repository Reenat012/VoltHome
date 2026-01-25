package ru.mugalimov.volthome.ui.screens.loads.single

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadUiState
import ru.mugalimov.volthome.ui.components.InfoTooltip
import kotlin.math.roundToInt

/**
 * Коммит 9.5:
 * - Выносим размеры/ритм/отступы в константы (единый источник правды)
 * - Фиксим “слеплено из разных частей”: одинаковая сетка у блоков
 * - [3] = заметка ("Примечание"), а не равная карточка
 */
@Composable
fun PhaseLoadSingleReportContent(
    uiState: PhaseLoadUiState,
    modifier: Modifier = Modifier
) {
    val phaseA = remember(uiState.data) { uiState.data.firstOrNull { it.phase == Phase.A } }
    val totalA = phaseA?.totalCurrent ?: 0.0

    val incomer = uiState.incomer?.mcbRating
    val warnPct = uiState.thresholds.warnPct
    val alertPct = uiState.thresholds.alertPct

    val loadPct: Double? = remember(totalA, incomer) {
        if (incomer != null && incomer > 0) (totalA / incomer) * 100.0 else null
    }

    val incomerLevel = remember(loadPct, warnPct, alertPct) {
        when {
            loadPct == null -> ReportLevel.NORMAL
            loadPct >= alertPct -> ReportLevel.ALERT
            loadPct >= warnPct -> ReportLevel.WARN
            else -> ReportLevel.NORMAL
        }
    }

    // ===== [2] агрегаты =====
    val maxSharePct: Double? = remember(phaseA, totalA) {
        if (phaseA == null) return@remember null
        if (totalA <= 0.0) return@remember null
        if (phaseA.groups.isEmpty()) return@remember null

        val byRoom = phaseA.groups
            .groupBy { it.roomName }
            .mapValues { (_, groups) -> groups.sumOf { it.totalCurrent } }

        val maxRoomAmps = byRoom.values.maxOrNull() ?: return@remember null
        if (maxRoomAmps <= 0.0) return@remember null

        (maxRoomAmps / totalA) * 100.0
    }

    val loadProfile = remember(maxSharePct) {
        when {
            maxSharePct == null -> LoadProfile.BACKGROUND
            maxSharePct >= 70.0 -> LoadProfile.IMPULSE
            maxSharePct >= 50.0 -> LoadProfile.CYCLIC
            else -> LoadProfile.BACKGROUND
        }
    }

    val concentrationLevel = remember(maxSharePct) {
        when {
            maxSharePct == null -> ReportLevel.NORMAL
            maxSharePct >= 70.0 -> ReportLevel.ALERT
            maxSharePct >= 50.0 -> ReportLevel.WARN
            else -> ReportLevel.NORMAL
        }
    }

    // ===== [3] интерпретация (2–3 строки, нейтрально) =====
    val meaningLines: List<String> = remember(
        loadPct, incomer, incomerLevel, maxSharePct, loadProfile, concentrationLevel
    ) {
        val lines = mutableListOf<String>()

        if (incomer == null || incomer <= 0 || loadPct == null) {
            lines += "Номинал вводного автомата не задан."
            lines += "Оценка нагрузки по порогам недоступна."
            if (maxSharePct != null) lines += "Концентрация нагрузки: ${fmt0(maxSharePct)}% в одной зоне."
            return@remember lines.take(3)
        }

        lines += when (incomerLevel) {
            ReportLevel.NORMAL -> "Загрузка вводного в допустимой зоне: ${fmt0(loadPct)}%."
            ReportLevel.WARN -> "Загрузка вводного близка к порогу: ${fmt0(loadPct)}%."
            ReportLevel.ALERT -> "Загрузка вводного превышает порог: ${fmt0(loadPct)}%."
        }

        if (maxSharePct == null) {
            lines += "Концентрация по зонам не определена."
        } else {
            lines += when (concentrationLevel) {
                ReportLevel.NORMAL -> "Концентрация нагрузки умеренная: ${fmt0(maxSharePct)}%."
                ReportLevel.WARN -> "Концентрация нагрузки повышенная: ${fmt0(maxSharePct)}%."
                ReportLevel.ALERT -> "Концентрация нагрузки высокая: ${fmt0(maxSharePct)}%."
            }
        }

        lines += "Профиль нагрузки: ${loadProfile.label}."
        lines.take(3)
    }

    // ===== Вердикт-текст и уровень (единый источник для [1] и footer) =====
    val verdictText: String = remember(incomer, incomerLevel) {
        if (incomer == null) {
            "Вердикт недоступен: вводной автомат не задан."
        } else {
            when (incomerLevel) {
                ReportLevel.NORMAL -> "Состояние: допустимо."
                ReportLevel.WARN -> "Состояние: на границе допустимого."
                ReportLevel.ALERT -> "Состояние: недопустимо."
            }
        }
    }
    val verdictLevelOrNull: ReportLevel? = if (incomer == null) null else incomerLevel

    LazyColumn(
        modifier = modifier.padding(
            horizontal = UiDimens.ScreenPaddingH,
            vertical = UiDimens.ScreenPaddingV
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // [1] главный блок
        if (incomer != null) {
            item {
                IncomerReportCard(
                    incomerA = incomer,
                    totalA = totalA,
                    loadPct = loadPct,
                    level = incomerLevel,
                    warnPct = warnPct,
                    alertPct = alertPct,
                    verdictText = verdictText
                )
            }
            item { Spacer(Modifier.height(UiDimens.GapAfterMainBlock)) }
        }

        // [2] вторичный блок
        if (phaseA != null) {
            item {
                ConcentrationReportCard(
                    maxSharePct = maxSharePct,
                    level = concentrationLevel,
                    warnPct = UiDimens.ConcentrationWarnPct,
                    alertPct = UiDimens.ConcentrationAlertPct,
                    profile = loadProfile,
                    tooltipContent = {
                        Text(
                            text = "Импульсный — кратковременные пики нагрузки.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Цикличный — периодически повторяющиеся включения.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Фоновый — равномерная длительная нагрузка.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
            item { Spacer(Modifier.height(UiDimens.GapBetweenSecondaryAndNote)) }
        }

        // [3] примечание
        item {
            MeaningReportCard(lines = meaningLines)
        }

        // перед footer — больше воздуха
        item { Spacer(Modifier.height(UiDimens.GapBeforeFooter)) }

        // [4] footer
        item {
            FooterVerdictStrip(
                text = verdictText,
                level = verdictLevelOrNull
            )
        }

        item { Spacer(Modifier.height(UiDimens.GapAfterFooter)) }
    }
}

// =======================
// “Дорогие” карточки (UI)
// =======================

@Composable
private fun IncomerReportCard(
    incomerA: Int,
    totalA: Double,
    loadPct: Double?,
    level: ReportLevel,
    warnPct: Int,
    alertPct: Int,
    verdictText: String
) {
    val pctText = if (loadPct == null) "—" else "${fmt0(loadPct)}%"
    val currentText = "${fmt1(totalA)} A"

    val reserveA = (incomerA - totalA).coerceAtLeast(0.0)
    val reserveText = "Запас: ${fmt1(reserveA)} A"

    val verdictColor = level.valueColor()
    val dividerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = UiDimens.CardElevation)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = UiDimens.CardPaddingH,
                vertical = UiDimens.CardPaddingV
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Вводной автомат",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$currentText · $pctText",
                    style = MaterialTheme.typography.titleMedium, // ✅ не меньше заголовка
                    fontWeight = FontWeight.Bold,                 // ✅ усилено
                    color = verdictColor
                )
            }

            Spacer(Modifier.height(UiDimens.IncomerGapHeaderToScale))

            // Scale (доминирует)
            ThresholdProgressBar(
                valuePct = (loadPct ?: 0.0).coerceIn(0.0, 200.0),
                warnPct = warnPct,
                alertPct = alertPct,
                level = level,
                heightDp = UiDimens.IncomerProgressHeight
            )

            Spacer(Modifier.height(UiDimens.IncomerGapScaleToLabels))

            // подписи под шкалой (всегда bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$incomerA A",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = reserveText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(UiDimens.IncomerGapLabelsToNote))

            // вторичное примечание (всегда bodySmall)
            Text(
                text = "Без учёта пусковых токов",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(UiDimens.IncomerGapNoteToDivider))

            // Divider
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(UiDimens.DividerHeight)
            ) {
                val y = size.height / 2f
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = size.height
                )
            }

            Spacer(Modifier.height(UiDimens.IncomerGapDividerToVerdict))

            // Verdict
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiDimens.GapInline)
            ) {
                Text(
                    text = "●",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = verdictColor
                )
                Text(
                    text = verdictText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = verdictColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ConcentrationReportCard(
    maxSharePct: Double?,
    level: ReportLevel,
    warnPct: Int,
    alertPct: Int,
    profile: LoadProfile,
    tooltipContent: @Composable () -> Unit
) {
    val shareText = if (maxSharePct == null) "—" else "${fmt0(maxSharePct)}%"
    val expanded: MutableState<Boolean> = remember { mutableStateOf(false) }

    // ✅ если цвет концентрации спорит с вводным — приглушаем ТОЛЬКО отображение
    val valueColor = level.valueColor().copy(alpha = UiDimens.SecondaryValueAlpha)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = UiDimens.CardElevation)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = UiDimens.CardPaddingH,
                vertical = UiDimens.CardPaddingV
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Концентрация нагрузки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                InfoTooltip(
                    expanded = expanded,
                    contentDescription = "Пояснение",
                    tooltip = tooltipContent
                )
            }

            Spacer(Modifier.height(UiDimens.ConcentrationGapHeaderToRow))

            // Value row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Максимальная доля",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = shareText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor
                )
            }

            Spacer(Modifier.height(UiDimens.ConcentrationGapRowToScale))

            ThresholdProgressBar(
                valuePct = (maxSharePct ?: 0.0).coerceIn(0.0, 100.0),
                warnPct = warnPct,
                alertPct = alertPct,
                level = level,
                heightDp = UiDimens.ConcentrationProgressHeight
            )

            Spacer(Modifier.height(UiDimens.ConcentrationGapScaleToProfile))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiDimens.GapInline)
            ) {
                Text(
                    text = "Профиль:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UiDimens.SecondaryLabelAlpha)
                )
                LoadProfileChipSecondary(text = profile.label)
            }
        }
    }
}

@Composable
private fun MeaningReportCard(lines: List<String>) {
    val titleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val noteBg = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = noteBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // ✅ заметка, без подъёма
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = UiDimens.NotePaddingH,
                vertical = UiDimens.NotePaddingV
            ),
            verticalAlignment = Alignment.Top
        ) {
            // note-accent слева
            Box(
                modifier = Modifier
                    .width(UiDimens.NoteAccentWidth)
                    .fillMaxHeight()
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = accentColor,
                        cornerRadius = CornerRadius(x = UiDimens.NoteAccentRadiusPx, y = UiDimens.NoteAccentRadiusPx)
                    )
                }
            }

            Spacer(Modifier.width(UiDimens.NoteGapAfterAccent))

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = "Примечание",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor
                )

                Spacer(Modifier.height(UiDimens.NoteGapTitleToLines))

                Column(verticalArrangement = Arrangement.spacedBy(UiDimens.NoteLineSpacing)) {
                    lines.take(3).forEach { line ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(UiDimens.NoteBulletGap)
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = markerColor
                            )
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * FooterVerdictStrip: финальная строка, НЕ "карточка №4"
 */
@Composable
private fun FooterVerdictStrip(
    text: String,
    level: ReportLevel?
) {
    val color = level?.valueColor() ?: MaterialTheme.colorScheme.onSurfaceVariant
    val bg = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = UiDimens.FooterPaddingH,
                vertical = UiDimens.FooterPaddingV
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "●",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Spacer(Modifier.width(UiDimens.GapInline))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ThresholdProgressBar(
    valuePct: Double,
    warnPct: Int,
    alertPct: Int,
    level: ReportLevel,
    heightDp: Dp
) {
    val progress = (valuePct / 100.0).toFloat().coerceIn(0f, 1f)
    val warnX = (warnPct / 100f).coerceIn(0f, 1f)
    val alertX = (alertPct / 100f).coerceIn(0f, 1f)

    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = level.valueColor()
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
    ) {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(h / 2f, h / 2f)

        drawRoundRect(
            color = track,
            cornerRadius = radius,
            size = Size(w, h)
        )

        drawRoundRect(
            color = fill,
            cornerRadius = radius,
            size = Size(w * progress, h)
        )

        fun tick(xFrac: Float) {
            val x = w * xFrac
            drawLine(
                color = tickColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 2f
            )
        }

        tick(warnX)
        tick(alertX)
    }
}

@Composable
private fun LoadProfileChipSecondary(text: String) {
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = UiDimens.ChipBgAlpha)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = UiDimens.ChipPaddingH,
                vertical = UiDimens.ChipPaddingV
            ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UiDimens.ChipTextAlpha)
        )
    }
}

// =======================
// Константы UI (Commit 9.5)
// =======================

private object UiDimens {
    // Screen
    val ScreenPaddingH = 16.dp
    val ScreenPaddingV = 12.dp

    // Lazy rhythm
    val GapAfterMainBlock = 12.dp
    val GapBetweenSecondaryAndNote = 8.dp
    val GapBeforeFooter = 16.dp
    val GapAfterFooter = 4.dp

    // Card base
    val CardPaddingH = 14.dp
    val CardPaddingV = 12.dp
    val CardElevation = 1.dp

    // Inline gaps
    val GapInline = 10.dp

    // Divider
    val DividerHeight = 1.dp

    // Incomer layout rhythm
    val IncomerProgressHeight = 12.dp
    val IncomerGapHeaderToScale = 10.dp
    val IncomerGapScaleToLabels = 8.dp
    val IncomerGapLabelsToNote = 6.dp
    val IncomerGapNoteToDivider = 10.dp
    val IncomerGapDividerToVerdict = 10.dp

    // Concentration layout rhythm (чуть компактнее)
    val ConcentrationProgressHeight = 4.dp
    val ConcentrationGapHeaderToRow = 10.dp
    val ConcentrationGapRowToScale = 8.dp
    val ConcentrationGapScaleToProfile = 10.dp

    // Concentration thresholds (эвристика — как раньше)
    const val ConcentrationWarnPct = 50
    const val ConcentrationAlertPct = 70

    // Secondary rendering tweaks
    const val SecondaryValueAlpha = 0.85f
    const val SecondaryLabelAlpha = 0.85f

    // Note card
    val NotePaddingH = 12.dp
    val NotePaddingV = 10.dp
    val NoteAccentWidth = 3.dp
    const val NoteAccentRadiusPx = 6f
    val NoteGapAfterAccent = 10.dp
    val NoteGapTitleToLines = 6.dp
    val NoteLineSpacing = 8.dp
    val NoteBulletGap = 8.dp

    // Footer
    val FooterPaddingH = 14.dp
    val FooterPaddingV = 8.dp

    // Chip
    val ChipPaddingH = 8.dp
    val ChipPaddingV = 4.dp
    const val ChipBgAlpha = 0.65f
    const val ChipTextAlpha = 0.90f
}

// =======================
// Модели/уровни/утилиты
// =======================

private enum class LoadProfile(val label: String) {
    IMPULSE("Импульсный"),
    CYCLIC("Цикличный"),
    BACKGROUND("Фоновый")
}

private enum class ReportLevel { NORMAL, WARN, ALERT }

@Composable
private fun ReportLevel.valueColor(): Color {
    return when (this) {
        ReportLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        ReportLevel.WARN -> MaterialTheme.colorScheme.tertiary
        ReportLevel.ALERT -> MaterialTheme.colorScheme.error
    }
}

private fun fmt1(v: Double) = String.format("%.1f", v)
private fun fmt0(v: Double) = v.roundToInt().toString()