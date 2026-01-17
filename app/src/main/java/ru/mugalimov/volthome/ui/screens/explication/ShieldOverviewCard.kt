package ru.mugalimov.volthome.ui.screens.explication

// ---------- imports ----------
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SafetyDivider
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.core.theme.toUiPhase
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.IncomerKind
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.report.InlineNormatives
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.inferVoltageType
import ru.mugalimov.volthome.domain.use_case.phaseCurrents
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcDetailsState
import ru.mugalimov.volthome.ui.format.ExplicationNumberFormat as F
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetPayload
import ru.mugalimov.volthome.ui.screens.explication.sheets.InfoSheetType
import ru.mugalimov.volthome.ui.viewmodel.explication.InfoSheetPayloadFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShieldOverviewCard(
    incomer: IncomerSpec,
    groups: List<CircuitGroup>,
    hasGroupRcds: Boolean,
    installedPowerW: CalculatedValue,
    calculatedPowerW: CalculatedValue,
    showProfessionalEvidence: Boolean,
    onProfessionalLockedClick: () -> Unit,
    onOpenInfoSheet: (InfoSheetPayload) -> Unit,
    onInstalledPowerClick: (CalculatedValue) -> Unit,
    onCalculatedPowerClick: (CalculatedValue) -> Unit,
    onIncomerFieldClick: (InfoSheetPayloadFactory.IncomerField) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ---------- данные ----------
    val vType: VoltageType = inferVoltageType(groups)
    val is3 = vType == VoltageType.AC_3PHASE
    val perPhase = phaseCurrents(groups)
    val aI = perPhase.getOrZero(Phase.A)
    val bI = perPhase.getOrZero(Phase.B)
    val cI = perPhase.getOrZero(Phase.C)
    val maxPhase =
        if (is3) listOf("A" to aI, "B" to bI, "C" to cI).maxBy { it.second }.first else null

    val hasWetZones = remember(groups) { groups.any { it.rcdRequired } }

    // ---------- roles ----------
    val cs = MaterialTheme.colorScheme
    val bgCard = cs.surface
    val textSecondary = cs.onSurfaceVariant
    val outline = cs.outlineVariant.copy(alpha = 0.60f)
    val divider = cs.outlineVariant.copy(alpha = 0.45f)

    // ---------- UI ----------
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, outline)
    ) {
        Column(Modifier.padding(16.dp)) {

            // Шапка «Щит в целом»
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Щит в целом",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { onOpenInfoSheet(buildHeaderPayload()) }
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = "Что это?")
                }
            }
            Text(
                text = "Сеть, ввод и сводка по мощности — всё, что нужно для общей оценки и подбора аппарата.",
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary
            )

            Spacer(Modifier.height(12.dp))

            // Итоги (без темы перекоса)
            ClickableSummaryRow(
                label = "Группы",
                value = groups.size.toString()
            ) {
                onOpenInfoSheet(
                    InfoSheetPayload.totalsGroups(
                        groupsCount = groups.size
                    )
                )
            }

            ClickableSummaryRow(
                label = "Установленная мощность",
                value = "${F.kwFromW(installedPowerW.value.toInt(), decimals = 1)} кВт"
            ) { onInstalledPowerClick(installedPowerW) }

            ClickableSummaryRow(
                label = "Расчётная нагрузка",
                value = "${F.kwFromW(calculatedPowerW.value.toInt(), decimals = 1)} кВт"
            ) { onCalculatedPowerClick(calculatedPowerW) }

            Spacer(Modifier.height(12.dp))

            // Бейджи сети
            InfoBadges(
                is3 = is3,
                hasGroupRcds = hasGroupRcds,
                hasWetZones = hasWetZones,
                onNetworkClick = { onOpenInfoSheet(buildNetworkPayload(is3)) },
                onGroupRcdsClick = { onOpenInfoSheet(buildGroupRcdsPayload()) },
                onWetZonesClick = { onOpenInfoSheet(buildWetZonesPayload()) }
            )

            if (is3) {
                Spacer(Modifier.height(12.dp))
                PhaseLine(aI = aI, bI = bI, cI = cI, maxPhase = maxPhase)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = divider)
            Spacer(Modifier.height(12.dp))

            // 2×2 грид параметров вводного оборудования
            IncomerGrid(
                incomer = incomer,
                is3 = is3,
                hasGroupRcds = hasGroupRcds,
                onHeaderInfoClick = { onOpenInfoSheet(buildIncomerPayload()) },
                onTileClick = onIncomerFieldClick
            )
        }
    }
}

// ---------- вспомогательные блоки ----------

@Composable
private fun ClickableSummaryRow(label: String, value: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val textSecondary = cs.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InfoBadges(
    is3: Boolean,
    hasGroupRcds: Boolean,
    hasWetZones: Boolean,
    onNetworkClick: () -> Unit,
    onGroupRcdsClick: () -> Unit,
    onWetZonesClick: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Badge(
            icon = Icons.Outlined.Bolt,
            text = if (is3) "Сеть: 3-фазная" else "Сеть: 1-фазная",
            onClick = onNetworkClick
        )
        Badge(
            icon = Icons.Outlined.SafetyDivider,
            text = "Групповые УЗО: " + if (hasGroupRcds) "да" else "нет",
            onClick = onGroupRcdsClick
        )
        Badge(
            icon = Icons.Outlined.WaterDrop,
            text = "Влажные зоны: " + if (hasWetZones) "есть" else "нет",
            onClick = onWetZonesClick
        )
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = cs.surfaceContainerHigh
    val outline = cs.outlineVariant.copy(alpha = 0.60f)
    val iconTint = cs.onSurfaceVariant
    val textPrimary = cs.onSurface

    Surface(
        shape = MaterialTheme.shapes.large,
        color = bg,
        border = BorderStroke(1.dp, outline)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = textPrimary
            )
        }
    }
}

@Composable
private fun PhaseLine(aI: Double, bI: Double, cI: Double, maxPhase: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PhaseDot(Phase.A)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${F.a(aI, decimals = 1)} A",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.width(12.dp))
        PhaseDot(Phase.B)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${F.a(bI, decimals = 1)} A",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.width(12.dp))
        PhaseDot(Phase.C)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${F.a(cI, decimals = 1)} A",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.weight(1f))
        if (maxPhase != null) {
            Text(
                text = "Макс. фаза: $maxPhase",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhaseDot(phase: Phase) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(VhColors.phase(phase.toUiPhase()))
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomerGrid(
    incomer: IncomerSpec,
    is3: Boolean,
    hasGroupRcds: Boolean,
    onHeaderInfoClick: () -> Unit,
    onTileClick: (InfoSheetPayloadFactory.IncomerField) -> Unit
) {
    val scheme = when (incomer.kind) {
        IncomerKind.MCB_PLUS_RCD -> "Автомат + УЗО"
        IncomerKind.RCBO -> "Дифавтомат"
        IncomerKind.MCB_ONLY -> "Только автомат"
    }
    val schemeBadges = when {
        incomer.kind == IncomerKind.MCB_PLUS_RCD && hasGroupRcds -> listOf(
            "селективное",
            "противопожарное"
        )
        else -> emptyList()
    }

    // Шапка (кликабельная + иконка i)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onHeaderInfoClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Вводной аппарат",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Что такое вводной аппарат?",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(8.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GridCell(
            label = "Схема",
            value = scheme,
            trailingBadges = schemeBadges
        ) { onTileClick(InfoSheetPayloadFactory.IncomerField.SCHEME) }

        GridCell(
            label = "Полюса",
            value = if (is3) "3P+N (4 пол.)" else "1P+N (2 пол.)"
        ) { onTileClick(InfoSheetPayloadFactory.IncomerField.POLES) }

        GridCell(
            label = "Автомат",
            value = "${incomer.mcbRating} A • кривая ${incomer.mcbCurve} • Icn ${incomer.icn / 1000} кА"
        ) { onTileClick(InfoSheetPayloadFactory.IncomerField.MCB) }

        GridCell(
            label = "УЗО (ввод)",
            value = if (incomer.kind != IncomerKind.MCB_ONLY)
                "тип ${incomer.rcdType} • ${incomer.rcdSensitivityMa} мА" +
                        if (incomer.rcdSelectivity.name == "S") " • селективное" else ""
            else "—"
        ) { onTileClick(InfoSheetPayloadFactory.IncomerField.RCD) }
    }
}

@Composable
private fun GridCell(
    label: String,
    value: String,
    trailingBadges: List<String> = emptyList(),
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val bg = cs.surfaceContainerHigh
    val bgChip = cs.surface
    val outline = cs.outlineVariant.copy(alpha = 0.60f)
    val textSecondary = cs.onSurfaceVariant
    val textPrimary = cs.onSurface

    Surface(
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        color = bg,
        border = BorderStroke(1.dp, outline)
    ) {
        Column(
            Modifier
                .widthIn(min = 0.dp, max = 360.dp)
                .clickable(onClick = onClick)
                .padding(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textSecondary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textPrimary
            )

            if (trailingBadges.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    trailingBadges.forEach { b ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = bgChip,
                            border = BorderStroke(1.dp, outline)
                        ) {
                            Text(
                                text = b,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- payload builders (без legacy имен) ----------

private fun buildHeaderPayload(): InfoSheetPayload =
    InfoSheetPayload(
        title = "Что в этом блоке",
        sheetType = InfoSheetType.REFERENCE,
        interpretation = "Сводка по сети и вводному аппарату плюс краткие итоги по мощностям. " +
                "Нажимайте на элементы — откроются пояснения простым языком и подсказки по выбору.",
        calcDetailsState = CalcDetailsState.HIDDEN
    )

private fun buildNetworkPayload(is3: Boolean): InfoSheetPayload =
    InfoSheetPayload(
        title = "Тип сети",
        sheetType = InfoSheetType.REFERENCE,
        interpretation = withNorm(
            text = if (is3)
                "3-фазная сеть 400/230 В: нагрузки распределяются по фазам A/B/C; ток на каждой фазе ниже и проще балансировать."
            else
                "1-фазная сеть 230 В: все группы на одной фазе; важно контролировать суммарную нагрузку и запас вводного автомата.",
            key = InlineNormatives.FactKey.NETWORK_TYPE
        ),
        calcDetailsState = CalcDetailsState.HIDDEN
    )

private fun buildGroupRcdsPayload(): InfoSheetPayload =
    InfoSheetPayload(
        title = "Групповые УЗО",
        sheetType = InfoSheetType.REFERENCE,
        interpretation = withNorm(
            text = "УЗО ставят на отдельные линии (розетки, влажные помещения и т. п.). При утечке отключается только эта линия, " +
                    "а остальная часть щита остаётся под напряжением — это удобнее и безопаснее.",
            key = InlineNormatives.FactKey.GROUP_RCDS
        ),
        calcDetailsState = CalcDetailsState.HIDDEN
    )

private fun buildWetZonesPayload(): InfoSheetPayload =
    InfoSheetPayload(
        title = "Влажные зоны",
        sheetType = InfoSheetType.REFERENCE,
        interpretation = withNorm(
            text = "Ванные, санузлы и зоны у мойки. Для таких линий обычно применяют УЗО чувствительностью 30 мА. " +
                    "Следуйте проекту/ПУЭ и проверяйте степень защиты оборудования.",
            key = InlineNormatives.FactKey.WET_ZONES_30MA
        ),
        calcDetailsState = CalcDetailsState.HIDDEN
    )

private fun buildIncomerPayload(): InfoSheetPayload =
    InfoSheetPayload(
        title = "Вводной аппарат",
        sheetType = InfoSheetType.REFERENCE,
        interpretation =
            "Главный коммутационный аппарат щита: позволяет быстро обесточить объект и защищает ввод от перегрузки и КЗ. " +
                    "Как правило включает:\n" +
                    "• Автоматический выключатель (номинал In, кривая отключения B/C/D, отключающая способность — кА).\n" +
                    "• Полюсность: 1P+N для 1-ф сети, 3P+N для 3-ф.\n" +
                    "• При необходимости — УЗО/RCBO на вводе (тип AC/A/F/B, чувствительность мА).",
        calcDetailsState = CalcDetailsState.HIDDEN
    )

// ---------- utils ----------

private fun withNorm(text: String, key: InlineNormatives.FactKey): String {
    val norm = InlineNormatives.forFact(key)?.trim().orEmpty()
    return if (norm.isBlank()) text else text.trimEnd() + "\n\n" + norm
}

@Composable
fun ProfessionalSectionPlaceholder(
    title: String,
    subtitle: String,
    onUnlockClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onUnlockClick) {
            Text("Открыть PRO")
        }
    }
}