package ru.mugalimov.volthome.ui.screens.explication.Incomer

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

// ---------- public API ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomerCard(
    incomer: IncomerSpec,
    groups: List<CircuitGroup>,
    hasGroupRcds: Boolean,
    modifier: Modifier = Modifier
) {
    val vType: VoltageType = inferVoltageType(groups)
    val is3 = vType == VoltageType.AC_3PHASE
    val perPhase = phaseCurrents(groups)

    val aI = perPhase.getOrZero(Phase.A)
    val bI = perPhase.getOrZero(Phase.B)
    val cI = perPhase.getOrZero(Phase.C)
    val maxPhase = if (is3) listOf("A" to aI, "B" to bI, "C" to cI).maxBy { it.second }.first else null

    // что показываем в шите
    var infoTopic by rememberSaveable { mutableStateOf<InfoTopic?>(null) }
    var fieldTopic by rememberSaveable { mutableStateOf<FieldTopic?>(null) }

    // состояние шита
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val hasWetZones = remember(groups) { groups.any { it.rcdRequired } }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(16.dp)) {

            // --- ШАПКА С ПОЯСНЕНИЕМ ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Вводной аппарат",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { infoTopic = InfoTopic.HEADER; scope.launch { sheetState.show() } }) {
                    Icon(Icons.Outlined.Info, contentDescription = "Что это?")
                }
            }
            Text(
                "Краткие характеристики устройства, которое защищает весь щит: помогает быстро проверить соответствие нормам и планировать замену.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            // --- БЕЙДЖИ (кликабельные) ---
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

            // --- 2×2 СЕТКА ПАРАМЕТРОВ (каждая плитка кликабельна) ---
            IncomerGrid(
                incomer = incomer,
                is3 = is3,
                hasGroupRcds = hasGroupRcds,
                onTileClick = { topic -> fieldTopic = topic; scope.launch { sheetState.show() } }
            )
        }
    }

    // ---------- единый BottomSheet для всех подсказок ----------
    if (infoTopic != null || fieldTopic != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    infoTopic = null; fieldTopic = null
                }
            },
            sheetState = sheetState
        ) {
            val title: String
            val text: String

            if (infoTopic != null) {
                val (t, msg) = infoSheetContent(infoTopic!!, is3)
                title = t; text = msg
            } else {
                val (t, msg) = fieldSheetContent(fieldTopic!!, incomer, is3)
                title = t; text = msg
            }

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

// ---------- пояснения (иконка в заголовке и бейджи) ----------
private enum class InfoTopic { HEADER, NETWORK, GROUP_RCDS, WET_ZONES }

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
        Badge(icon = Icons.Outlined.Bolt, text = if (is3) "Сеть: 3‑фазная" else "Сеть: 1‑фазная", onClick = onNetworkClick)
        Badge(icon = Icons.Outlined.SafetyDivider, text = "Групповые УЗО: " + if (hasGroupRcds) "да" else "нет", onClick = onGroupRcdsClick)
        Badge(icon = Icons.Outlined.WaterDrop, text = "Влажные зоны: " + if (hasWetZones) "есть" else "нет", onClick = onWetZonesClick)
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
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ---------- содержимое шита для бейджей ----------
private fun infoSheetContent(topic: InfoTopic, is3: Boolean): Pair<String, String> = when (topic) {
    InfoTopic.HEADER -> "Зачем этот блок" to
            "Здесь собраны ключевые параметры вводного устройства: схема, полюса, автомат и УЗО. " +
            "Это позволяет быстро оценить соответствие нормам, понять уровень защиты и без ошибок подобрать замену."
    InfoTopic.NETWORK -> "Тип сети" to if (is3)
        "3‑фазная сеть (380/400 В): нагрузка распределяется по трём фазам A/B/C, что снижает ток по каждой фазе."
    else
        "1‑фазная сеть (220/230 В): все группы на одной фазе; следи за суммарной мощностью и током вводного автомата."
    InfoTopic.GROUP_RCDS -> "Групповые УЗО" to
            "УЗО на отдельных группах (розетки, влажные помещения и т. д.). При утечке отключается только конкретная группа, а не весь ввод. " +
            "Это повышает селективность и удобство эксплуатации."
    InfoTopic.WET_ZONES -> "Влажные зоны" to
            "Ванные, санузлы, кухни у мойки и т.п. Требуется УЗО, обычно 30 мА. Для некоторых зон — ещё ниже."
}

// ---------- grid с кликом по плиткам ----------
private enum class FieldTopic { SCHEME, POLES, MCB, RCD }

@Composable
private fun IncomerGrid(
    incomer: IncomerSpec,
    is3: Boolean,
    hasGroupRcds: Boolean,
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

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GridCell(label = "Схема", value = scheme, trailingBadges = schemeBadges) { onTileClick(FieldTopic.SCHEME) }
        GridCell(label = "Полюса", value = if (is3) "3P+N (4 пол.)" else "1P+N (2 пол.)") { onTileClick(FieldTopic.POLES) }
        GridCell(label = "Автомат", value = "${incomer.mcbRating} A • кривая ${incomer.mcbCurve} • Icn ${incomer.icn / 1000} кА") { onTileClick(FieldTopic.MCB) }
        GridCell(
            label = "УЗО (ввод)",
            value = if (incomer.kind != IncomerKind.MCB_ONLY)
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
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (trailingBadges.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    trailingBadges.forEach { b ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            Text(
                                b,
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

// ---------- содержимое шита для плиток грида ----------
private fun fieldSheetContent(topic: FieldTopic, incomer: IncomerSpec, is3: Boolean): Pair<String, String> =
    when (topic) {
        FieldTopic.SCHEME -> "Схема" to when (incomer.kind) {
            IncomerKind.MCB_PLUS_RCD -> "Вводной автомат + отдельное УЗО. Часто используют селективное/противопожарное УЗО, чтобы при утечке отключалась группа, а не весь ввод."
            IncomerKind.RCBO -> "Дифавтомат (автомат + УЗО в одном корпусе). Удобно, когда нужно сэкономить место."
            IncomerKind.MCB_ONLY -> "Только автомат без УЗО. Тогда защиту от утечек обеспечивают групповые УЗО/дифавтоматы."
        }
        FieldTopic.POLES -> "Полюса" to if (is3)
            "3P+N (4 полюса): отключаются три фазы и нейтраль — полное обесточивание."
        else
            "1P+N (2 полюса): отключаются фаза и нейтраль."
        FieldTopic.MCB -> "Автомат" to
                "Номинал — рабочий ток без отключения; кривая — реакция на пусковые токи; Icn — предельная отключающая способность (важно для КЗ). " +
                "Подбирается по расчётному току, сечению кабеля и требуемой кривой B/C/D."
        FieldTopic.RCD -> "УЗО (ввод)" to
                "Тип (A/AC и др.) — какие утечки распознаёт; чувствительность (мА) — порог отключения; «селективное» — с задержкой для селективности и чтобы не вырубать весь ввод."
    }

// ---------- helpers ----------
private fun fmt1(v: Double) = String.format("%.1f", v)

// единые цвета фаз (твоя палитра)
private fun phaseDotColor(p: Phase): Color = when (p) {
    Phase.A -> Color(0xFFF6D96B) // жёлтый
    Phase.B -> Color(0xFF7ED492) // зелёный
    Phase.C -> Color(0xFFFF8A80) // красный
}

@Composable
private fun PhaseLine(aI: Double, bI: Double, cI: Double, maxPhase: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PhaseDot(Phase.A)
        Spacer(Modifier.width(6.dp))
        Text("${fmt1(aI)} A", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.width(12.dp))
        PhaseDot(Phase.B)
        Spacer(Modifier.width(6.dp))
        Text("${fmt1(bI)} A", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.width(12.dp))
        PhaseDot(Phase.C)
        Spacer(Modifier.width(6.dp))
        Text("${fmt1(cI)} A", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.weight(1f))
        if (maxPhase != null) {
            Text(
                "Макс. фаза: $maxPhase",
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
            .background(phaseDotColor(phase))
    )
}