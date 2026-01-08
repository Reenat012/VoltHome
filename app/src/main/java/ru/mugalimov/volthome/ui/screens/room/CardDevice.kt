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
    modifier: Modifier = Modifier,
    onEditClick: (Long) -> Unit = {},
    onDeleteClick: (Long) -> Unit = {}
) {
    var info by remember { mutableStateOf<InfoTopic?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val t = VhColors.tokens

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceAvatar(
            type = device.deviceType,
            modifier = Modifier
                .size(36.dp)
                .padding(end = 12.dp)
        )

        // Нейтральная индустриальная карточка без “раскраски по типам”
        val cardBg = t.surfaceAlt

        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {

                // основной контент карточки
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 40.dp) // запас справа под меню
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = t.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = device.deviceType.label(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(10.dp))

                    // параметры
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Pill(
                            text = "Мощность: ${device.power} Вт",
                            onClick = { info = InfoTopic.POWER }
                        )
                        Pill(
                            text = device.voltage.toReadableLabel(),
                            onClick = { info = InfoTopic.VOLTAGE }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Pill(
                            text = "cos φ = ${device.powerFactor.format(2)}",
                            onClick = { info = InfoTopic.POWER_FACTOR }
                        )
                        Pill(
                            text = "Коэф. спроса = ${device.demandRatio.format(2)}",
                            onClick = { info = InfoTopic.DEMAND_RATIO }
                        )
                    }
                }

                // меню действий (Редактировать / Удалить) в правом верхнем углу
                var menuExpanded by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
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
        }
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

private fun Double.format(d: Int) = "%.${d}f".format(this).replace(',', '.')

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