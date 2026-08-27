package ru.mugalimov.volthome.ui.screens.algoritm_about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import ru.mugalimov.volthome.core.theme.UiPhase
import ru.mugalimov.volthome.core.theme.VhColors

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

        InfoCard(
            title = "Как считается ток",
            icon = {
                Icon(
                    Icons.Filled.Equalizer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            paragraphs = listOf(
                "Для однофазной нагрузки ток определяется по мощности, напряжению и коэффициенту мощности. " +
                        "Для трёхфазной используется линейное напряжение и множитель √3."
            ),
            bullets = listOf(
                "Установленный ток: I = P / (U × cos φ) для 1 фазы.",
                "Расчётный вклад: мощность дополнительно умножается на коэффициент спроса.",
                "Автомат группы подбирается по установленному току: max(округление вверх, минимум для типа линии)."
            ),
            chips = listOf("P", "U", "cos φ", "Коэффициент спроса")
        )

        InfoCard(
            title = "Автомат и кабель",
            icon = {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            bullets = listOf(
                "Автомат выбирается первым из поддерживаемого ряда 10–63 А.",
                "Характеристика B используется для обычной линии 10 А, C — базовая и для умеренной моторной нагрузки, D — для моторной нагрузки от 25 А.",
                "После автомата кабель выбирается из продуктовой матрицы: 1,5; 2,5; 4; 6; 10; 16 мм²."
            ),
            chips = listOf("Автомат → кабель", "Единое правило AUTO/MANUAL")
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
                "Для ванной, кухни и улицы алгоритм назначает групповое УЗО 30 мА.",
                "В обычной комнате УЗО 30 мА получает линия, в которой добавлена «Розетка бытовая».",
                "Любая самостоятельная группа с прибором, отмеченным «Подключение через розетку», получает УЗО 30 мА на эту группу.",
                "Например, отдельная группа микроволновой печи не зависит от другой группы «Розетка бытовая»: обе линии защищаются самостоятельно.",
                "Для каждой линии подбираем автоматический выключатель и кабель по нормам.",
                "Согласуем защиту с вводным автоматом, чтобы при аварии отключалась только нужная линия."
            ),
            chips = listOf("Защита от утечки", "Подбор автомата", "Селективность")
        )

        InfoCard(
            title = "Вводной аппарат",
            icon = {
                Icon(
                    Icons.Filled.Power,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            bullets = listOf(
                "За основу берётся максимальный расчётный ток фазы.",
                "Расчётный ток делится на 0,8 — так закладывается 20% резерва.",
                "Выбирается первый подходящий номинал из ряда 6–160 А.",
                "Для трёхфазной сети используется 3P+N, для однофазной — 1P+N."
            ),
            chips = listOf("20% резерва", "Максимальная фаза")
        )

        InfoCard(
            title = "Границы автоматического расчёта",
            icon = {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            bullets = listOf(
                "Для линии с заданной длиной учитываются материал, изоляция, способ прокладки, температура, группировка и падение напряжения.",
                "Без длины линии сечение остаётся предварительным и подбирается по автомату.",
                "Не рассчитываются ток короткого замыкания, петля повреждения и время автоматического отключения.",
                "Приложение не определяет фактические границы влажных зон и не проверяет договорную мощность.",
                "Результат является расчётной рекомендацией и требует проверки специалистом перед монтажом."
            ),
            chips = listOf("Проверить перед монтажом")
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
    val t = VhColors.tokens
    Surface(
        color = t.primarySurface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = t.primary.copy(alpha = 0.42f),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "МЕТОДИКА · АЛГОРИТМ 3",
                style = MaterialTheme.typography.labelMedium,
                color = t.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "От исходных данных до структуры щита",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = t.textPrimary
            )
            Text(
                "Показываем не только результат, но и последовательность инженерных решений, исходные допущения и границы автоматического расчёта.",
                style = MaterialTheme.typography.bodyMedium,
                color = t.textSecondary
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallChip("Равномерные фазы")
                SmallChip("Объяснимый расчёт")
                SmallChip("Проверка специалистом")
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
    val t = VhColors.tokens
    Surface(
        color = t.surfaceAlt,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, t.divider, RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Surface(shape = RoundedCornerShape(10.dp), color = t.primarySurface) {
                        Box(Modifier.padding(8.dp)) { icon() }
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = t.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = t.textSecondary) }
            if (bullets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bullets.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = t.textSecondary) }
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
                "Однофазные группы сортируются по расчётному току от большей к меньшей. Каждая группа " +
                        "ставится на фазу с наименьшим результирующим перекосом; затем алгоритм до трёх раз " +
                        "проверяет перенос 15 самых тяжёлых групп. При равенстве используется стабильный порядок A → B → C.",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhaseDot("Фаза A", VhColors.phase(UiPhase.A))
                PhaseDot("Фаза B", VhColors.phase(UiPhase.B))
                PhaseDot("Фаза C", VhColors.phase(UiPhase.C))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhaseBar("Фаза A", 34, VhColors.phase(UiPhase.A))
                PhaseBar("Фаза B", 33, VhColors.phase(UiPhase.B))
                PhaseBar("Фаза C", 33, VhColors.phase(UiPhase.C))
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
