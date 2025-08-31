package ru.mugalimov.volthome.ui.screens.algoritm_about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*  Алгоритм: простыми словами + лёгкие визуальные акценты.
    — Иконки окрашены в primary
    — Карточки: небольшая тень (2dp) + тонкая рамка surfaceVariant
    — Термины объясняем: «защита от утечки тока», «редко всё включено сразу» и т.д.
*/

@Composable
fun AlgorithmExplanationContent() {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeaderCard()

        InfoCard(
            title = "Анализ проекта",
            icon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            bullets = listOf(
                "Собираем данные по приборам: мощность, напряжение, тип.",
                "Учитываем «особые зоны»: ванная, кухня, улица.",
                "Берём поправки на реальную нагрузку: приборы редко работают все одновременно.",
                "Выделяем устройства, которым нужна отдельная линия."
            ),
            chips = listOf("Безопасность", "Нормы ПУЭ", "Локальная обработка")
        )

        InfoCard(
            title = "Отдельные линии для мощных приборов",
            icon = {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            paragraphs = listOf(
                "Для мощных устройств — например, духовой шкаф, электроплита, кондиционер — создаётся отдельная линия.",
                "Система автоматически подбирает подходящий автоматический выключатель и сечение кабеля по нормам."
            ),
            bullets = listOf(
                "Снижаем риск перегрузки и просадок напряжения.",
                "Учитываем пусковые токи (кратковременные всплески при старте)."
            )
        )

        InfoCard(
            title = "Группы освещения",
            icon = {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            bullets = listOf(
                "Свет делим на удобные группы, чтобы при срабатывании защиты не гасло всё сразу.",
                "Нагрузка каждой группы ограничена, при превышении создаём дополнительную."
            ),
            chips = listOf("Комфорт", "Безопасность")
        )

        InfoCard(
            title = "Розеточные группы",
            icon = {
                Icon(
                    Icons.Filled.Power,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            bullets = listOf(
                "Объединяем розетки в группы так, чтобы суммарная мощность была в пределах нормы.",
                "Если приборов много — делим на несколько групп автоматически."
            ),
            chips = listOf("Автоделение", "Контроль нагрузки")
        )

        PhaseBalanceCard()

        InfoCard(
            title = "Защита и безопасность",
            icon = {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            bullets = listOf(
                "Для влажных зон (ванная, кухня, улица) добавляем защиту от утечки тока (УЗО/дифавтомат) — это защищает человека при повреждении изоляции.",
                "Для каждой линии подбираем автоматический выключатель и кабель по нормам.",
                "Согласуем защиту с вводным автоматом, чтобы при аварии отключалась только нужная линия."
            ),
            chips = listOf("Защита от утечки", "Подбор автомата", "Селективность")
        )

        InfoCard(
            title = "Что вы получаете в итоге",
            icon = {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            bullets = listOf(
                "Список линий (групп) и какие приборы куда подключены.",
                "Для каждой линии — подобранный автомат, кабель, отметка о защите от утечки (если нужна).",
                "Распределение по фазам A/B/C и итоговая нагрузка по каждой фазе."
            )
        )
    }
}

/* ────────────────────────── UI-блоки ────────────────────────── */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderCard() {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Умное распределение по группам и фазам",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                "VoltHome группирует приборы, подбирает защиту и равномерно распределяет нагрузку по фазам A/B/C. Учёт «реальной» нагрузки и того, что не всё включено одновременно.",
                style = MaterialTheme.typography.bodyMedium
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallChip("Равномерные фазы")
                SmallChip("Нормы ПУЭ")
                SmallChip("Безопасность")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(
    title: String,
    icon: @Composable (() -> Unit)? = null,
    bullets: List<String> = emptyList(),
    paragraphs: List<String> = emptyList(),
    chips: List<String> = emptyList()
) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) icon()
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (bullets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bullets.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chips.forEach { SmallChip(it) }
                }
            }
        }
    }
}

/* ──────────────────────── Фазы / Баланс ─────────────────────── */

private val PhaseA = Color(0xFFE74C3C)
private val PhaseB = Color(0xFFF1C40F)
private val PhaseC = Color(0xFF2ECC71)

@Composable
private fun PhaseBalanceCard() {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Equalizer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Балансировка по фазам",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Text(
                "После формирования групп система распределяет их по трём фазам так, чтобы нагрузка была максимально ровной. Это помогает избежать перекоса и шумов в сети.",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhaseDot("Фаза A", PhaseA)
                PhaseDot("Фаза B", PhaseB)
                PhaseDot("Фаза C", PhaseC)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhaseBar("Фаза A", 34, PhaseA)
                PhaseBar("Фаза B", 33, PhaseB)
                PhaseBar("Фаза C", 33, PhaseC)
            }

            AssistChip(
                onClick = {},
                label = { Text("Равномерная нагрузка = стабильнее и безопаснее") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

/* ─────────────────────── Вспомогательные UI ─────────────────────── */

@Composable
private fun PhaseDot(text: String, color: Color) {
    AssistChip(
        onClick = {},
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, shape = MaterialTheme.shapes.small)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun PhaseBar(label: String, percent: Int, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .height(14.dp)
                    .background(color)
            )
        }
    }
}

@Composable
private fun SmallChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}