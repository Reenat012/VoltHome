
package ru.mugalimov.volthome.ui.utilities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ru.mugalimov.volthome.ui.navigation.Screens

@Composable
fun AlgorithmInfoButton(navController: NavHostController) {
    IconButton(onClick = { navController.navigate(Screens.AlgorithmExplanationScreen.route) }) {
        Icon(
            imageVector = Icons.Filled.Lightbulb,
            contentDescription = "Как работает алгоритм",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun AlgorithmExplanationContent() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Заголовок
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
//                Icon(
//                    imageVector = Icons.Filled.Lightbulb,
//                    contentDescription = "Идея",
//                    modifier = Modifier.size(40.dp)
//                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Умный алгоритм распредедения устройств по электрическим группам",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Шаги алгоритма
        AlgorithmStepCard(
            icon = Icons.Filled.Search,
            title = "1. Анализ проекта",
            content = "Алгоритм начинает с тщательного анализа всех помещений и устройств:"
        ) {
            BulletPoint("Сбор данных по каждому устройству (мощность, тип, наличие двигателя)")
            BulletPoint("Определение 'особых зон' (ванная, кухня, улица)")
            BulletPoint("Учет характеристик напряжения и коэффициентов спроса")
            BulletPoint("Выявление устройств, требующих специальных условий")
        }

        AlgorithmStepCard(
            icon = Icons.Filled.Star,
            title = "2. HEAVY_DUTY-линии для мощной техники",
            content = "Создаются выделенные линии для энергоемкого оборудования:"
        ) {
            BulletPoint("Кондиционеры и климатические системы")
            BulletPoint("Водонагреватели и электроплиты")
            BulletPoint("Любая техника мощнее 2000 Вт")
            BulletPoint("Устройства с пометкой 'Требует выделенной линии'")
            InfoText("Для них подбирается усиленная защита: автоматы 25А-32А, кабель 4-6 мм², тип 'D'")
        }

        AlgorithmStepCard(
            icon = Icons.Filled.Lightbulb,
            title = "3. Группы освещения",
            content = "Все осветительные приборы объединяются в отдельные группы:"
        ) {
            BulletPoint("Люстры, светильники и LED-ленты")
            BulletPoint("Экономичное и безопасное решение")
            BulletPoint("Автоматы 10А с кабелем 1.5 мм²")
            BulletPoint("Оптимальное распределение по помещениям")
        }

        AlgorithmStepCard(
            icon = Icons.Filled.Power,
            title = "4. Розеточные группы",
            content = "Бытовая техника объединяется с соблюдением правил:"
        ) {
            BulletPoint("5-8 устройств на одну группу")
            BulletPoint("Нагрузка не превышает 80% от номинала автомата")
            BulletPoint("Автоматы типа 'C' (16А)")
            BulletPoint("Универсальный кабель 2.5 мм²")
            InfoText("Автоматическое разделение групп при обнаружении перегрузки")
        }

        AlgorithmStepCard(
            icon = Icons.Filled.Security,
            title = "5. Защита и безопасность",
            content = "Система обеспечивает максимальную защиту:"
        ) {
            BulletPoint("Автоматическое добавление УЗО 30мА для ванных, кухонь и улицы")
            BulletPoint("Проверка соответствия проводки нагрузкам")
            BulletPoint("Защита от перегрузок и коротких замыканий")
            BulletPoint("Учет пусковых токов для техники с двигателями")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Пример группировки
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Пример группировки для кухни:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                GroupExample(
                    title = "HEAVY_DUTY-линия",
                    devices = listOf("Кондиционер (2000 Вт)", "Проточный водонагреватель (3500 Вт)"),
                    specs = listOf("Автомат: 25А, тип 'D'", "Кабель: 4 мм²", "Защита: УЗО 30мА")
                )

                Spacer(modifier = Modifier.height(8.dp))

                GroupExample(
                    title = "Розеточная группа",
                    devices = listOf("Холодильник (200 Вт)", "Микроволновка (1000 Вт)", "Кофемашина (800 Вт)"),
                    specs = listOf("Автомат: 16А, тип 'C'", "Кабель: 2.5 мм²", "Защита: УЗО 30мА")
                )

                Spacer(modifier = Modifier.height(8.dp))

                GroupExample(
                    title = "Освещение",
                    devices = listOf("Основная люстра (100 Вт)", "Подсветка рабочей зоны (50 Вт)"),
                    specs = listOf("Автомат: 10А, тип 'B'", "Кабель: 1.5 мм²", "Защита: Без УЗО")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Гарантии
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Почему это надежно?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Chip("Соответствие ПУЭ")
                    Chip("Двойная проверка")
                    Chip("Запас мощности 20%")
                    Chip("Защита от рисков")
                    Chip("Учет пусковых токов")
                    Chip("Оптимальный кабель")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Все расчеты соответствуют российским стандартам (ПУЭ) и обеспечивают безопасную работу вашей техники на десятилетия",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AlgorithmStepCard(
    icon: ImageVector,
    title: String,
    content: String,
    contentBlock: @Composable () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(content)
            Spacer(modifier = Modifier.height(12.dp))
            contentBlock()
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("• ", fontWeight = FontWeight.Bold)
        Text(text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun GroupExample(
    title: String,
    devices: List<String>,
    specs: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            devices.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Упрощенная версия без иконки
                    Text("• ", fontWeight = FontWeight.Bold)
                    Text(device)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            specs.forEach { spec ->
                Text(
                    text = spec,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}