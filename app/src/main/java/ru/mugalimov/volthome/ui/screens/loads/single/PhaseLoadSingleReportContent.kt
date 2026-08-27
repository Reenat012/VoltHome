package ru.mugalimov.volthome.ui.screens.loads.single

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.phase_load.PhaseGroupItem
import ru.mugalimov.volthome.domain.model.phase_load.PhaseLoadUiState
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingScreen
import ru.mugalimov.volthome.ui.onboarding.model.OnboardingTargetTag
import ru.mugalimov.volthome.ui.onboarding.modifier.onboardingAnchor
import ru.mugalimov.volthome.ui.components.IncomerAssessmentStatusCard
import ru.mugalimov.volthome.ui.format.UiTextFormat
import kotlin.math.roundToInt

/**
 * Однофазный экран отвечает на три вопроса: сколько потребляем, какой остался
 * запас и какие группы формируют нагрузку. Эвристики профиля/концентрации здесь
 * намеренно не используются: по статическому составу проекта их определить нельзя.
 */
@Composable
fun PhaseLoadSingleReportContent(
    uiState: PhaseLoadUiState,
    modifier: Modifier = Modifier
) {
    val phase = remember(uiState.data) {
        uiState.data.firstOrNull { it.phase == Phase.A }
    }
    val current = phase?.totalCurrent ?: 0.0
    val incomer = uiState.incomer?.mcbRating?.takeIf { it > 0 }
    val loadPct = incomer?.let { current / it * 100.0 }
    val status = loadStatus(loadPct, uiState.thresholds.warnPct, uiState.thresholds.alertPct)
    val groups = remember(phase?.groups) {
        phase?.groups.orEmpty().sortedByDescending { it.totalPower }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SinglePhaseOverviewCard(
                current = current,
                incomer = incomer,
                loadPct = loadPct,
                status = status,
                warnPct = uiState.thresholds.warnPct,
                alertPct = uiState.thresholds.alertPct
            )
        }

        uiState.incomerAssessment?.let { assessment ->
            item {
                IncomerAssessmentStatusCard(assessment = assessment)
            }
        }

        item {
            LoadCompositionHeader(
                groupsCount = groups.size,
                devicesCount = groups.sumOf { it.devices.size },
                totalPower = phase?.totalPower ?: 0.0
            )
        }

        if (groups.isEmpty()) {
            item { EmptyCompositionCard() }
        } else {
            items(groups, key = { it.groupId }) { group ->
                SinglePhaseGroupRow(group)
            }
        }
    }
}

@Composable
private fun SinglePhaseOverviewCard(
    current: Double,
    incomer: Int?,
    loadPct: Double?,
    status: LoadStatus,
    warnPct: Int,
    alertPct: Int
) {
    val statusColor = status.color()
    val progress = ((loadPct ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    var showLoadExplanation by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onboardingAnchor(
                targetTag = OnboardingTargetTag.LOADS_SINGLE_OVERVIEW,
                screenId = OnboardingScreen.LOADS
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Нагрузка на ввод",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (incomer == null) "Номинал автомата не задан" else "Вводной автомат · $incomer А",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(status.label, statusColor)
                    IconButton(onClick = { showLoadExplanation = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Как оценивается загрузка ввода")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (incomer == null) UiTextFormat.amperes(current) else "${UiTextFormat.amperes(current)} из $incomer${UiTextFormat.NBSP}А",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = loadPct?.let { "${fmt0(it)}%" } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val reserve = incomer?.let { (it - current).coerceAtLeast(0.0) }
                Text(
                    text = reserve?.let { "Запас ${UiTextFormat.amperes(it)}" } ?: "Укажите номинал для оценки",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (incomer != null) {
                    Text(
                        text = "Пороги $warnPct / $alertPct%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showLoadExplanation) {
        AlertDialog(
            onDismissRequest = { showLoadExplanation = false },
            title = { Text("Загрузка вводного автомата") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (incomer == null || loadPct == null) {
                            "Для оценки нужен рассчитанный номинал вводного автомата."
                        } else {
                            "Расчётный ток ${UiTextFormat.amperes(current)} составляет ${fmt0(loadPct)}% от номинала $incomer${UiTextFormat.NBSP}А."
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Почему нужен запас")
                    Text("• не все режимы работы и кратковременные пики полностью описываются статическим расчётом;\n• нагрев и условия установки влияют на реальную работу аппарата;\n• запас уменьшает риск нежелательного отключения и оставляет место для небольшого изменения нагрузки.")
                    Text(
                        "Пороги $warnPct / $alertPct% — диагностические уровни ВольтХом. " +
                            "Это предварительная оценка, а не универсальный нормативный предел.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadExplanation = false }) { Text("Понятно") }
            }
        )
    }
}

@Composable
private fun LoadCompositionHeader(groupsCount: Int, devicesCount: Int, totalPower: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Состав нагрузки",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "$groupsCount ${plural(groupsCount, "группа", "группы", "групп")} · " +
                "$devicesCount ${plural(devicesCount, "устройство", "устройства", "устройств")} · ${UiTextFormat.power(totalPower)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SinglePhaseGroupRow(group: PhaseGroupItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = group.groupNumber.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = group.roomName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = group.devices.joinToString { it.name }.ifBlank { "Без устройств" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(UiTextFormat.power(group.totalPower), fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Расчётный ток ${UiTextFormat.amperes(group.totalCurrent)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyCompositionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Text(
            text = "Добавьте устройства и группы — здесь появится структура нагрузки.",
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(
        modifier = Modifier.background(color.copy(alpha = 0.14f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private enum class LoadStatus(val label: String) {
    UNKNOWN("Нет данных"), NORMAL("Допустимо"), WARNING("Внимание"), ALERT("Перегрузка")
}

private fun loadStatus(value: Double?, warn: Int, alert: Int): LoadStatus = when {
    value == null -> LoadStatus.UNKNOWN
    value >= alert -> LoadStatus.ALERT
    value >= warn -> LoadStatus.WARNING
    else -> LoadStatus.NORMAL
}

@Composable
private fun LoadStatus.color(): Color = when (this) {
    LoadStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    LoadStatus.NORMAL -> MaterialTheme.colorScheme.primary
    LoadStatus.WARNING -> MaterialTheme.colorScheme.tertiary
    LoadStatus.ALERT -> MaterialTheme.colorScheme.error
}

private fun fmt0(value: Double) = value.roundToInt().toString()

private fun plural(value: Int, one: String, few: String, many: String): String {
    val mod100 = value % 100
    val mod10 = value % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
