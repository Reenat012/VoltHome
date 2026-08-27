package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.cable.CableCalculationStatus
import ru.mugalimov.volthome.domain.model.cable.CableLineCalculation
import ru.mugalimov.volthome.domain.model.cable.ProjectCableDefaults

@Composable
fun CableCalculationOverviewCard(
    groups: List<CircuitGroup>,
    calculations: Map<Long, CableLineCalculation>,
    defaults: ProjectCableDefaults,
    isPro: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groupIds = groups.mapTo(hashSetOf(), CircuitGroup::groupId)
    val actual = calculations.values.filter { it.groupId in groupIds && it.input.lengthM != null }
    val configured = actual.size
    val passed = actual.count { it.status == CableCalculationStatus.PASSED }
    val attention = actual.count {
        it.status == CableCalculationStatus.WARNING || it.status == CableCalculationStatus.FAILED
    }
    val total = groups.size
    val progress = if (total == 0) 0f else configured.toFloat() / total.toFloat()
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, if (isPro) cs.primary.copy(alpha = 0.55f) else cs.outlineVariant),
        tonalElevation = 3.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.primaryContainer,
                    contentColor = cs.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cable,
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp).size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Расчёт кабельных линий",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Проверка сечения, допустимого тока и падения напряжения",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                if (!isPro) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = cs.secondaryContainer,
                        contentColor = cs.onSecondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PRO", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$configured из $total линий рассчитано",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (configured > 0) {
                    Text(
                        text = if (attention == 0) "$passed без замечаний" else "$attention требуют внимания",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (attention == 0) cs.primary else cs.error
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (attention == 0) cs.primary else cs.error,
                trackColor = cs.surfaceContainerHighest
            )

            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cs.surfaceContainer
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = if (isPro) "Условия проекта" else "Базовый профиль бесплатной версии",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${defaults.material.title} · ${defaults.insulation.title} · ${defaults.installationMethod.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        text = "${defaults.ambientTemperatureC} °C · цепей рядом: ${defaults.groupedCircuits} · ΔU до ${formatPercent(defaults.maxVoltageDropPercent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isPro) {
                    "Сначала задайте общие условия, затем внесите длины трасс. Для каждой линии можно оставить автоматический выбор или проверить своё сечение."
                } else {
                    "Параметры показаны для ознакомления. Без фактической длины результат остаётся предварительным; редактирование условий и полный расчёт доступны в PRO."
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(if (isPro) "Настроить и рассчитать" else "Открыть расчёт в PRO")
            }
        }
    }
}

private fun formatPercent(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().replace('.', ',')
