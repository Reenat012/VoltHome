package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType
import ru.mugalimov.volthome.ui.utilities.label

/* ----------------- Модель «какой параметр поясняем» ----------------- */

private enum class InfoTopic { POWER, POWER_FACTOR, DEMAND_RATIO, VOLTAGE }

/* ----------------------------- UI ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDevice(
    device: Device,
    roomCalculatedPowerW: Double = 0.0,
    modifier: Modifier = Modifier,
    onEditClick: (Long) -> Unit = {},
    onDeleteClick: (Long) -> Unit = {}
) {
    var info by remember { mutableStateOf<InfoTopic?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val t = VhColors.tokens
    val calculatedPowerW = device.power * device.demandRatio
    val calculatedCurrentA = device.current * device.demandRatio
    val contribution = if (roomCalculatedPowerW > 0.0) {
        (calculatedPowerW / roomCalculatedPowerW).coerceIn(0.0, 1.0)
    } else 0.0

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = t.surfaceAlt),
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDetails = true }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(type = device.deviceType, modifier = Modifier.size(42.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = t.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.deviceType.label(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Меню",
                            tint = t.textSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = t.textSecondary
                                )
                            },
                            text = { Text("Редактировать", color = t.textPrimary) },
                            onClick = {
                                menuExpanded = false
                                onEditClick(device.id)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = t.error
                                )
                            },
                            text = { Text("Удалить", color = t.textPrimary) },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick(device.id)
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceMetric(
                    label = "Паспортная",
                    value = "${(device.power / 1000.0).format(2)} кВт",
                    modifier = Modifier.weight(1f)
                )
                DeviceMetric(
                    label = "Расчётная",
                    value = "${(calculatedPowerW / 1000.0).format(2)} кВт",
                    modifier = Modifier.weight(1f)
                )
                DeviceMetric(
                    label = "Ток расч.",
                    value = "${calculatedCurrentA.format(2)} А",
                    modifier = Modifier.weight(1f)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (device.requiresDedicatedCircuit) Pill("Выделенная линия")
                Pill(if (device.requiresSocketConnection) "Розеточное подключение" else "Прямое подключение")
                if (device.hasMotor) Pill("Двигательная нагрузка")
                if (device.voltage.type == VoltageType.AC_3PHASE) Pill("3-фазное устройство")
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Вклад в нагрузку комнаты", style = MaterialTheme.typography.labelSmall, color = t.textMuted)
                    Text("${(contribution * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = t.textSecondary)
                }
                LinearProgressIndicator(
                    progress = { contribution.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = t.primary,
                    trackColor = t.surface
                )
            }
        }
    }

    if (showDetails) {
        DeviceEngineeringSheet(
            device = device,
            calculatedPowerW = calculatedPowerW,
            calculatedCurrentA = calculatedCurrentA,
            contribution = contribution,
            onDismiss = { showDetails = false },
            onEdit = {
                showDetails = false
                onEditClick(device.id)
            }
        )
    }

    // лист пояснений остаётся без изменений по структуре/логике
    if (info != null) {
        ModalBottomSheet(
            onDismissRequest = { info = null },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            val (title, description) = remember(info) { info!!.titleAndText() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = t.textPrimary
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textSecondary
                )
            }
        }
    }
}

@Composable
private fun DeviceMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val t = VhColors.tokens
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = t.surface) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = t.textMuted, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, color = t.textPrimary, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceEngineeringSheet(
    device: Device,
    calculatedPowerW: Double,
    calculatedCurrentA: Double,
    contribution: Double,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val t = VhColors.tokens
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(device.deviceType, Modifier.size(48.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = t.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(device.deviceType.label(context), style = MaterialTheme.typography.bodyMedium, color = t.textSecondary)
                }
            }

            Surface(shape = RoundedCornerShape(18.dp), color = t.primarySurface) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Расчётный вклад", style = MaterialTheme.typography.labelMedium, color = t.textSecondary)
                        Text("${(calculatedPowerW / 1000.0).format(2)} кВт", style = MaterialTheme.typography.headlineSmall, color = t.textPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Расчётный ток", style = MaterialTheme.typography.labelMedium, color = t.textSecondary)
                        Text("${calculatedCurrentA.format(2)} А", style = MaterialTheme.typography.headlineSmall, color = t.primary)
                    }
                }
            }

            DeviceDetailRow("Установленная мощность", "${device.power} Вт")
            DeviceDetailRow("Коэффициент спроса", device.demandRatio.format(2))
            DeviceDetailRow("Коэффициент мощности", device.powerFactor.format(2))
            DeviceDetailRow("Питание", device.voltage.toReadableLabel())
            DeviceDetailRow("Вклад в комнату", "${(contribution * 100).toInt()}% расчётной нагрузки")
            DeviceDetailRow("Подключение", if (device.requiresSocketConnection) "Через розетку" else "Прямое")
            DeviceDetailRow("Линия", if (device.requiresDedicatedCircuit) "Выделенная" else "Допускается групповая")

            Text(
                "Расчётная мощность получена из паспортной мощности с учётом коэффициента спроса. " +
                    "Ток дополнительно учитывает напряжение и коэффициент мощности.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
            androidx.compose.material3.OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Text("Изменить параметры", Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceDetailRow(label: String, value: String) {
    val t = VhColors.tokens
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = t.textSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = t.textPrimary,
            modifier = Modifier.padding(start = 16.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ---------------- helpers ---------------- */

@Composable
private fun DeviceAvatar(type: DeviceType?, modifier: Modifier = Modifier) {
    val icon = when (type) {
        DeviceType.LIGHTING -> Icons.Rounded.Lightbulb
        DeviceType.SOCKET,
        DeviceType.HEAVY_DUTY -> Icons.Rounded.Power
        DeviceType.AIR_CONDITIONER -> Icons.Rounded.AcUnit
        else -> Icons.Rounded.Settings
    }

    val t = VhColors.tokens

    // Нейтральный avatar: без цветных “пятен” по типу устройства
    val bg: Color = t.surface
    val fg: Color = t.textSecondary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, t.divider)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = fg)
        }
    }
}

/** Чип с опциональным onClick */
@Composable
private fun Pill(text: String, onClick: (() -> Unit)? = null) {
    val t = VhColors.tokens
    val clickable = onClick != null

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = t.surface,
        border = BorderStroke(1.dp, t.divider),
        modifier = if (clickable) Modifier.clickable { onClick?.invoke() } else Modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = t.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun Double.format(d: Int) = "%.${d}f".format(this).replace('.', ',')

private fun Voltage.toReadableLabel(): String {
    val phase = when (this.type) {
        VoltageType.AC_1PHASE -> "1-фаза"
        VoltageType.AC_3PHASE -> "3-фазы"
        VoltageType.DC -> "пост. ток"
    }
    return "${this.value} В, $phase"
}

/* --------- Тексты для листа-пояснения --------- */

private fun InfoTopic.titleAndText(): Pair<String, String> = when (this) {
    InfoTopic.POWER -> "Мощность (Вт)" to
            "Номинальная потребляемая мощность устройства. Нужна для расчёта нагрузки и выбора автомата/кабеля."

    InfoTopic.POWER_FACTOR -> "cos φ — коэффициент мощности" to
            "Показывает соотношение активной и полной мощности. Чем ближе к 1.00, тем эффективнее устройство и ниже реактивные потери."

    InfoTopic.DEMAND_RATIO -> "Коэффициент спроса" to
            "Доля времени, когда устройство реально нагружает сеть в максимуме. Используется для расчёта суммарной нагрузки (учёт неполной одновременности)."

    InfoTopic.VOLTAGE -> "Питание" to
            "Рабочее напряжение и тип питания: «1-фаза» — однофазная сеть 220–230 В, «3-фазы» — трёхфазная 380–400 В, «пост. ток» — питание DC."
}
