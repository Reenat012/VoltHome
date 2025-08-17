package ru.mugalimov.volthome.ui.screens.explication

// ---------- imports ----------
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SafetyDivider
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.domain.model.incomer.IncomerKind
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.use_case.getOrZero
import ru.mugalimov.volthome.domain.use_case.inferVoltageType
import ru.mugalimov.volthome.domain.use_case.phaseCurrents

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShieldOverviewCard(
    incomer: IncomerSpec,
    groups: List<CircuitGroup>,
    hasGroupRcds: Boolean,
    modifier: Modifier = Modifier
) {
    // ---------- данные ----------
    val vType: VoltageType = inferVoltageType(groups)
    val is3 = vType == VoltageType.AC_3PHASE
    val perPhase = phaseCurrents(groups)
    val aI = perPhase.getOrZero(Phase.A)
    val bI = perPhase.getOrZero(Phase.B)
    val cI = perPhase.getOrZero(Phase.C)
    val maxPhase = if (is3) listOf("A" to aI, "B" to bI, "C" to cI).maxBy { it.second }.first else null

    val installedPowerW = groups.sumOf { it.devices.sumOf { d -> d.power ?: 0 } }
    // расчётная мощность (упрощённо: с учётом demandRatio устройств, если он есть; иначе берём 1.0)
    val calculatedPowerW = groups.sumOf { g ->
        g.devices.sumOf { d ->
            val p = (d.power ?: 0)
            val k = (d.demandRatio ?: 1.0)
            (p * k).toInt()
        }
    }

    val hasWetZones = remember(groups) { groups.any { it.rcdRequired } }

    // ---------- состояние bottom sheet ----------
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var infoTopic by rememberSaveable { mutableStateOf<InfoTopic?>(null) }
    var fieldTopic by rememberSaveable { mutableStateOf<FieldTopic?>(null) }
    var totalsTopic by rememberSaveable { mutableStateOf<TotalsTopic?>(null) }

    // ---------- UI ----------
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                    onClick = { infoTopic = InfoTopic.HEADER; scope.launch { sheetState.show() } }
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = "Что это?")
                }
            }
            Text(
                text = "Сеть, ввод и сводка по мощности — всё, что нужно для общей оценки и подбора аппарата.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // Итоги (без темы перекоса)
            ClickableSummaryRow(
                label = "Группы",
                value = "${groups.size}"
            ) { totalsTopic = TotalsTopic.GROUPS; scope.launch { sheetState.show() } }

            ClickableSummaryRow(
                label = "Установленная мощность",
                value = "%.1f кВт".format(installedPowerW / 1000.0)
            ) { totalsTopic = TotalsTopic.INSTALLED; scope.launch { sheetState.show() } }

            ClickableSummaryRow(
                label = "Расчётная нагрузка",
                value = "%.1f кВт".format(calculatedPowerW / 1000.0)
            ) { totalsTopic = TotalsTopic.CALCULATED; scope.launch { sheetState.show() } }

            Spacer(Modifier.height(12.dp))

            // Бейджи сети
            InfoBadges(
                is3 = is3,
                hasGroupRcds = hasGroupRcds,
                hasWetZones = hasWetZones,
                onNetworkClick = { infoTopic = InfoTopic.NETWORK; scope.launch { sheetState.show() } },
                onGroupRcdsClick = { infoTopic = InfoTopic.GROUP_RCDS; scope.launch { sheetState.show() } },
                onWetZonesClick = { infoTopic = InfoTopic.WET_ZONES; scope.launch { sheetState.show() } }
            )

            if (is3) {
                Spacer(Modifier.height(12.dp))
                PhaseLine(aI = aI, bI = bI, cI = cI, maxPhase = maxPhase)
            }

            Spacer(Modifier.height(12.dp))
            Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))

            // 2×2 грид параметров вводного оборудования
            IncomerGrid(
                incomer = incomer,
                is3 = is3,
                hasGroupRcds = hasGroupRcds,
                onHeaderInfoClick = { infoTopic = InfoTopic.INCOMER; scope.launch { sheetState.show() } },
                onTileClick = { fieldTopic = it; scope.launch { sheetState.show() } }
            )
        }
    }

    // ---------- BottomSheet: единый для всех подсказок ----------
    if (infoTopic != null || fieldTopic != null || totalsTopic != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    infoTopic = null; fieldTopic = null; totalsTopic = null
                }
            },
            sheetState = sheetState
        ) {
            val (title, text) = when {
                infoTopic != null   -> infoSheetContent(infoTopic!!, is3)
                fieldTopic != null  -> fieldSheetContent(fieldTopic!!, incomer, is3)
                else                -> totalsSheetContent(totalsTopic!!, installedPowerW, calculatedPowerW, groups.size)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ---------- вспомогательные блоки ----------

private enum class InfoTopic { HEADER, NETWORK, GROUP_RCDS, WET_ZONES, INCOMER }
private enum class FieldTopic { SCHEME, POLES, MCB, RCD }
private enum class TotalsTopic { GROUPS, INSTALLED, CALCULATED }

@Composable
private fun ClickableSummaryRow(label: String, value: String, onClick: () -> Unit) {
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PhaseLine(aI: Double, bI: Double, cI: Double, maxPhase: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PhaseDot(Phase.A); Spacer(Modifier.width(6.dp)); Text(text = "${fmt1(aI)} A", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        PhaseDot(Phase.B); Spacer(Modifier.width(6.dp)); Text(text = "${fmt1(bI)} A", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        PhaseDot(Phase.C); Spacer(Modifier.width(6.dp)); Text(text = "${fmt1(cI)} A", style = MaterialTheme.typography.bodyMedium)
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
            .background(
                when (phase) {
                    Phase.A -> Color(0xFFF6D96B) // жёлтый
                    Phase.B -> Color(0xFF7ED492) // зелёный
                    Phase.C -> Color(0xFFFF8A80) // красный
                }
            )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomerGrid(
    incomer: IncomerSpec,
    is3: Boolean,
    hasGroupRcds: Boolean,
    onHeaderInfoClick: () -> Unit,
    onTileClick: (FieldTopic) -> Unit
) {
    val scheme = when (incomer.kind) {
        IncomerKind.MCB_PLUS_RCD -> "Автомат + УЗО"
        IncomerKind.RCBO -> "Дифавтомат"
        IncomerKind.MCB_ONLY -> "Только автомат"
    }
    val schemeBadges = when {
        incomer.kind == IncomerKind.MCB_PLUS_RCD && hasGroupRcds -> listOf("селективное", "противопожарное")
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

    // Отступ между заголовком и карточками
    Spacer(Modifier.height(8.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GridCell("Схема", scheme, schemeBadges) { onTileClick(FieldTopic.SCHEME) }
        GridCell("Полюса", if (is3) "3P+N (4 пол.)" else "1P+N (2 пол.)") { onTileClick(FieldTopic.POLES) }
        GridCell(
            "Автомат",
            "${incomer.mcbRating} A • кривая ${incomer.mcbCurve} • Icn ${incomer.icn / 1000} кА"
        ) { onTileClick(FieldTopic.MCB) }
        GridCell(
            "УЗО (ввод)",
            if (incomer.kind != IncomerKind.MCB_ONLY)
                "тип ${incomer.rcdType} • ${incomer.rcdSensitivityMa} мА" +
                        if (incomer.rcdSelectivity.name == "S") " • селективное" else ""
            else "—"
        ) { onTileClick(FieldTopic.RCD) }
    }
}

@Composable
private fun GridCell(
    label: String,
    value: String,
    trailingBadges: List<String> = emptyList(),
    onClick: () -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
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
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = b,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- контент шита ----------

private fun infoSheetContent(topic: InfoTopic, is3: Boolean): Pair<String, String> = when (topic) {
    InfoTopic.HEADER -> "Что в этом блоке" to
            "Сводка по сети и вводному аппарату плюс краткие итоги по мощностям. " +
            "Нажимайте на элементы — откроются пояснения простым языком и подсказки по выбору."

    InfoTopic.NETWORK -> "Тип сети" to if (is3)
        "3-фазная сеть 400/230 В: нагрузки распределяются по фазам A/B/C; ток на каждой фазе ниже и проще балансировать."
    else
        "1-фазная сеть 230 В: все группы на одной фазе; важно контролировать суммарную нагрузку и запас вводного автомата."

    InfoTopic.GROUP_RCDS -> "Групповые УЗО" to
            "УЗО ставят на отдельные линии (розетки, влажные помещения и т. п.). При утечке отключается только эта линия, " +
            "а остальная часть щита остаётся под напряжением — это удобнее и безопаснее."

    InfoTopic.WET_ZONES -> "Влажные зоны" to
            "Ванные, санузлы и зоны у мойки. Для таких линий обычно применяют УЗО чувствительностью 30 мА. " +
            "Следуйте проекту/ПУЭ и проверяйте степень защиты оборудования."

    InfoTopic.INCOMER -> "Вводной аппарат" to
            "Главный коммутационный аппарат щита: позволяет быстро обесточить объект и защищает ввод от перегрузки и КЗ. " +
            "Как правило включает:\n" +
            "• Автоматический выключатель (номинал In, кривая отключения B/C/D, отключающая способность — кА).\n" +
            "• Полюсность: 1P+N для 1-ф сети, 3P+N для 3-ф.\n" +
            "• При необходимости — УЗО/RCBO на вводе (тип AC/A/F/B, чувствительность мА)."
}

private fun fieldSheetContent(topic: FieldTopic, incomer: IncomerSpec, is3: Boolean): Pair<String, String> =
    when (topic) {
        FieldTopic.SCHEME -> "Схема" to when (incomer.kind) {
            IncomerKind.MCB_PLUS_RCD ->
                "Вводной автомат + отдельное УЗО на вводе. На ввод обычно ставят селективное (тип S) или противопожарное УЗО " +
                        "с большим током утечки (100–300 мА). При мелкой утечке (например, 30 мА) на линии первым сработает " +
                        "групповое УЗО, а вводное останется включённым. Вводное отключает питание при крупной/неселективной утечке " +
                        "или при суммарных утечках, превышающих его порог."
            IncomerKind.RCBO ->
                "Дифавтомат (RCBO) — автомат + УЗО в одном корпусе. Защищает и от перегрузки/КЗ, и от утечек, экономит место."
            IncomerKind.MCB_ONLY ->
                "Только автомат без УЗО на вводе. Защиту от утечек обеспечивают групповые УЗО/RCBO на линиях."
        }

        FieldTopic.POLES -> "Полюса" to if (is3)
            "3P+N (4 полюса): одновременно отключаются три фазы и нейтраль — полное обесточивание. " +
                    "Распространённая конфигурация для вводных аппаратов в 3-ф сети."
        else
            "1P+N (2 полюса): одновременно отключаются фаза и нейтраль одной линии. " +
                    "Разрыв нейтрали выполняется совместно с фазой штатным двухполюсным аппаратом."

        FieldTopic.MCB -> "Автомат" to
                "Номинал (In) — ток, который автомат способен длительно проводить без отключения.\n" +
                "Кривая отключения (B/C/D) — диапазон мгновенного срабатывания: примерно B≈3–5·In, C≈5–10·In, D≈10–20·In. " +
                "Выбор зависит от пусковых токов нагрузки.\n" +
                "Отключающая способность: для бытовых MCB по IEC 60898-1 указывается Icn (кА); в квартирах часто 6 кА."

        FieldTopic.RCD -> "УЗО (ввод)" to
                "Тип чувствительности: AC (переменный), A (переменный + пульсирующий), F (доп. частоты/инверторы), B (постоянная составляющая). " +
                "Чувствительность (мА): 30 мА — защита человека; 100/300 мА — противопожарные задачи. " +
                "Селективное (S) — с выдержкой времени для селективности.\n" +
                "Важно: обычное УЗО не защищает от перегрузки и короткого замыкания — это делает автомат. RCBO совмещает обе функции."
    }

private fun totalsSheetContent(
    topic: TotalsTopic,
    installedPowerW: Int,
    calculatedPowerW: Int,
    groupsCount: Int
): Pair<String, String> = when (topic) {
    TotalsTopic.GROUPS -> "Группы" to
            "Количество групп помогает оценить заполненность щита и селективность. " +
            "При росте числа групп проверьте место под модули и наличие отдельных УЗО там, где это требуется."
    TotalsTopic.INSTALLED -> "Установленная мощность" to
            "Сумма паспортных мощностей всех устройств: %.1f кВт.".format(installedPowerW / 1000.0) +
            "\nИспользуется для подбора кабелей и оценки максимума."
    TotalsTopic.CALCULATED -> "Расчётная нагрузка" to
            "Мощность с учётом коэффициентов спроса: %.1f кВт.".format(calculatedPowerW / 1000.0) +
            "\nОна ближе к реальной одновременной нагрузке и влияет на выбор вводного автомата."
}

// ---------- utils ----------
private fun fmt1(v: Double) = String.format("%.1f", v)