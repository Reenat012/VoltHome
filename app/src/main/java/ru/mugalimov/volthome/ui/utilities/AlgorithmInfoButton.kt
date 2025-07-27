
package ru.mugalimov.volthome.ui.utilities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
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
                Text(
                    "Детальное описание работы алгоритма распределения устройств по группам",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Соответствует ПУЭ 7 и ГОСТ Р 50571",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Шаги алгоритма с формулами
        AlgorithmStepCard(
            icon = Icons.Filled.Calculate,
            title = "1. Расчёт тока устройств",
            content = "Для каждого устройства рассчитывается номинальный ток по формуле:"
        ) {
            FormulaBox("I = P / (U × cosφ)  [для однофазных]")
            FormulaBox("I = P / (√3 × U × cosφ)  [для трёхфазных]")
            BulletPoint("P - мощность устройства (Вт)")
            BulletPoint("U - напряжение (В)")
            BulletPoint("cosφ - коэффициент мощности (0.8-1.0)")

            ExampleBox(
                "Пример: Стиральная машина (2300 Вт, 230В, cosφ=0.9)",
                "I = 2300 / (230 × 0.9) = 11.1 А"
            )

            InfoText("Для устройств с двигателями дополнительно рассчитывается пусковой ток: I_пуск = I × 5")
        }

        AlgorithmStepCard(
            icon = Icons.Filled.Star,
            title = "2. Выделенные линии (HEAVY_DUTY)",
            content = "Устройства попадают в эту категорию если:"
        ) {
            FormulaBox("P > 2300 Вт (1-фазные) или P > 7000 Вт (3-фазные)")
            FormulaBox("Или установлен флаг 'Требует выделенной линии'")

            BulletPoint("Профиль защиты выбирается по току:")
            BulletPoint("• I ≤ 16A: Автомат 16A, кабель 2.5 мм², тип 'C'")
            BulletPoint("• 16A < I ≤ 25A: Автомат 25A, кабель 4 мм², тип 'D'")
            BulletPoint("• I > 25A: Автомат 32A, кабель 6 мм², тип 'D'")

            ExampleBox(
                "Водонагреватель (3500 Вт, 230В, cosφ=0.9)",
                "I = 3500 / (230 × 0.9) = 16.9 А → Автомат 25А, кабель 4 мм²"
            )

            InfoText("Проверка по ПУЭ 3.1.10: I_ном ≤ I_автомата и I_пуск ≤ I_срабатывания")
        }

        AlgorithmStepCard(
            icon = Icons.Filled.Lightbulb,
            title = "3. Группы освещения",
            content = "Осветительные приборы объединяются с ограничениями:"
        ) {
            FormulaBox("ΣI_группы ≤ 8A (80% от 10А автомата)")
            BulletPoint("Автомат: 10А, тип 'B' (ПУЭ 3.1.8)")
            BulletPoint("Кабель: 1.5 мм² (ПУЭ 7.1.34)")
            BulletPoint("Максимум 20 устройств на группу")

            ExampleBox(
                "3 люстры по 100 Вт + 5 светильников по 50 Вт",
                "ΣP = 550 Вт → ΣI = 550 / 230 = 2.4A < 8A"
            )
        }

        AlgorithmStepCard(
            icon = Icons.Filled.Power,
            title = "4. Розеточные группы",
            content = "Формирование групп с двойной проверкой:"
        ) {
            FormulaBox("ΣI_ном ≤ 12.8A (80% от 16А автомата)")
            FormulaBox("ΣI_пуск ≤ 160A (10 × 16А для типа 'C')")
            BulletPoint("Автомат: 16А, тип 'C'")
            BulletPoint("Кабель: 2.5 мм²")
            BulletPoint("Максимум 8 розеток на группу (ПУЭ 7.1.79)")

            ExampleBox(
                "Холодильник (1.02A), микроволновка (5.11A), кофемашина (4.09A)",
                "ΣI_ном = 10.22A < 12.8A\n" +
                        "ΣI_пуск = 1.02×5 + 5.11 + 4.09 = 14.2A < 160A"
            )
        }

        AlgorithmStepCard(
            icon = Icons.Filled.Security,
            title = "5. Защита и соответствие ПУЭ",
            content = "Автоматическое применение мер безопасности:"
        ) {
            BulletPoint("УЗО 30мА для ванных, кухонь и улицы (ПУЭ 7.1.71)")
            BulletPoint("Тип автомата 'D' для групп с двигателями")
            BulletPoint("Сечение кабеля по току (ПУЭ 1.3.10)")
            BulletPoint("Разделение PEN-проводника (ПУЭ 1.7.135)")

            InfoText("Все группы проходят 4 проверки безопасности перед сохранением")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Диаграмма процесса
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Диаграмма работы алгоритма:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                AlgorithmDiagram()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Детальный пример
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Пример группировки для кухни:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                DetailedGroupExample(
                    title = "HEAVY_DUTY-линия",
                    devices = listOf(
                        DeviceExample("Водонагреватель", 3500, 230, 0.9, true),
                        DeviceExample("Посудомоечная машина", 2000, 230, 0.9, true)
                    ),
                    specs = listOf("Автомат: 25А тип 'D'", "Кабель: 4 мм²", "УЗО: 30мА")
                )

                DetailedGroupExample(
                    title = "Розеточная группа 1",
                    devices = listOf(
                        DeviceExample("Холодильник", 200, 230, 0.85, true),
                        DeviceExample("Микроволновка", 1000, 230, 0.85, false)
                    ),
                    specs = listOf("Автомат: 16А тип 'C'", "Кабель: 2.5 мм²", "УЗО: 30мА")
                )

                DetailedGroupExample(
                    title = "Освещение",
                    devices = listOf(
                        DeviceExample("Основной свет", 100, 230, 1.0, false),
                        DeviceExample("Подсветка", 50, 230, 1.0, false)
                    ),
                    specs = listOf("Автомат: 10А тип 'B'", "Кабель: 1.5 мм²", "УЗО: нет")
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// Новые компоненты для визуализации
@Composable
private fun FormulaBox(formula: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Text(
            text = formula,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun ExampleBox(title: String, calculation: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = title, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = calculation,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AlgorithmDiagram() {
    val steps = listOf(
        "1. Анализ всех устройств",
        "2. Расчёт токов (I = P/(U×cosφ))",
        "3. Определение выделенных линий",
        "4. Группировка освещения",
        "5. Формирование розеточных групп",
        "6. Проверка безопасности (ПУЭ)",
        "7. Сохранение результатов"
    )

    Column {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = step)
            }

            if (index < steps.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .height(24.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

data class DeviceExample(
    val name: String,
    val power: Int, // Вт
    val voltage: Int, // В
    val powerFactor: Double,
    val hasMotor: Boolean
)

@Composable
private fun DetailedGroupExample(
    title: String,
    devices: List<DeviceExample>,
    specs: List<String>
) {
    // Рассчитываем суммарные параметры
    val totalCurrent = devices.sumOf {
        it.power / (it.voltage * it.powerFactor)
    }
    val totalPeakCurrent = devices.sumOf { device ->
        val current = device.power / (device.voltage * device.powerFactor)
        if (device.hasMotor) current * 5 else current
    }

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

            // Устройства с расчетами
            devices.forEach { device ->
                val current = device.power / (device.voltage * device.powerFactor)
                val peakCurrent = if (device.hasMotor) current * 5 else current

                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("• ${device.name} (${device.power} Вт)")
                    Text(
                        text = "I = ${"%.2f".format(current)}A, I_пуск = ${"%.2f".format(peakCurrent)}A",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Суммарные значения
            Text("Суммарный ток: ${"%.2f".format(totalCurrent)}A")
            Text("Суммарный пусковой ток: ${"%.2f".format(totalPeakCurrent)}A")

            Spacer(modifier = Modifier.height(8.dp))

            // Спецификации
            specs.forEach { spec ->
                Text(
                    text = spec,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Остальные компоненты (BulletPoint, InfoText, Chip) остаются без изменений

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

//@Composable
//private fun GroupExample(
//    title: String,
//    devices: List<String>,
//    specs: List<String>
//) {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = 8.dp),
//        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
//    ) {
//        Column(modifier = Modifier.padding(12.dp)) {
//            Text(
//                title,
//                style = MaterialTheme.typography.titleSmall,
//                fontWeight = FontWeight.Bold
//            )
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            devices.forEach { device ->
//                Row(verticalAlignment = Alignment.CenterVertically) {
//                    // Упрощенная версия без иконки
//                    Text("• ", fontWeight = FontWeight.Bold)
//                    Text(device)
//                }
//            }
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            specs.forEach { spec ->
//                Text(
//                    text = spec,
//                    style = MaterialTheme.typography.bodySmall,
//                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
//                )
//            }
//        }
//    }
//}

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