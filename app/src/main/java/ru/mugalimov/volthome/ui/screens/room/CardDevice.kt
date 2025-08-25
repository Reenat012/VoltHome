package ru.mugalimov.volthome.ui.screens.room

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.Voltage
import ru.mugalimov.volthome.domain.model.VoltageType

/* ----------------- Модель «какой параметр поясняем» ----------------- */

private enum class InfoTopic { POWER, POWER_FACTOR, DEMAND_RATIO, VOLTAGE }

/* ----------------------------- UI ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDevice(
    device: Device,
    modifier: Modifier = Modifier
) {
    // состояние листа-пояснения
    var info by remember { mutableStateOf<InfoTopic?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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

        // Мягкая, но заметная тонировка по типу устройства (непрозрачный итоговый цвет)
        val base = MaterialTheme.colorScheme.surface
        val tone = when (device.deviceType) {
            DeviceType.LIGHTING        -> MaterialTheme.colorScheme.tertiaryContainer
            DeviceType.SOCKET          -> MaterialTheme.colorScheme.secondaryContainer
            DeviceType.HEAVY_DUTY      -> MaterialTheme.colorScheme.errorContainer
            DeviceType.AIR_CONDITIONER -> MaterialTheme.colorScheme.primaryContainer
            DeviceType.ELECTRIC_STOVE,
            DeviceType.OVEN            -> MaterialTheme.colorScheme.secondaryContainer
            DeviceType.WASHING_MACHINE,
            DeviceType.DISHWASHER      -> MaterialTheme.colorScheme.tertiaryContainer
            DeviceType.WATER_HEATER    -> MaterialTheme.colorScheme.primaryContainer
            DeviceType.OTHER, null     -> MaterialTheme.colorScheme.surfaceVariant
        }
        val cardBg = tone.copy(alpha = 0.5f).compositeOver(base)

        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
            modifier = Modifier.weight(1f)
        ) {
            Column(Modifier.padding(12.dp)) {
                // Заголовок + тип
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = deviceTypeLabel(device.deviceType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(10.dp))

                // ОСНОВНЫЕ параметры: мощность и питание
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

                // ДОП параметры: cos φ и коэффициент спроса
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
        }
    }

    // Нижний лист с пояснением
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
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/* ---------------- helpers ---------------- */

@Composable
private fun DeviceAvatar(type: DeviceType?, modifier: Modifier = Modifier) {
    val icon = when (type) {
        DeviceType.LIGHTING        -> Icons.Rounded.Lightbulb
        DeviceType.SOCKET,
        DeviceType.HEAVY_DUTY      -> Icons.Rounded.Power
        DeviceType.AIR_CONDITIONER -> Icons.Rounded.AcUnit
        else                       -> Icons.Rounded.Settings
    }
    val bg = when (type) {
        DeviceType.LIGHTING        -> MaterialTheme.colorScheme.tertiaryContainer
        DeviceType.SOCKET          -> MaterialTheme.colorScheme.secondaryContainer
        DeviceType.HEAVY_DUTY      -> MaterialTheme.colorScheme.errorContainer
        DeviceType.AIR_CONDITIONER -> MaterialTheme.colorScheme.primaryContainer
        DeviceType.ELECTRIC_STOVE,
        DeviceType.OVEN            -> MaterialTheme.colorScheme.secondaryContainer
        DeviceType.WASHING_MACHINE,
        DeviceType.DISHWASHER      -> MaterialTheme.colorScheme.tertiaryContainer
        DeviceType.WATER_HEATER    -> MaterialTheme.colorScheme.primaryContainer
        DeviceType.OTHER, null     -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg =
        if (bg == MaterialTheme.colorScheme.errorContainer) MaterialTheme.colorScheme.onErrorContainer
        else when (bg) {
            MaterialTheme.colorScheme.primaryContainer   -> MaterialTheme.colorScheme.onPrimaryContainer
            MaterialTheme.colorScheme.secondaryContainer -> MaterialTheme.colorScheme.onSecondaryContainer
            MaterialTheme.colorScheme.tertiaryContainer  -> MaterialTheme.colorScheme.onTertiaryContainer
            else                                         -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        contentColor = fg
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null)
        }
    }
}

/** Чип с опциональным onClick */
@Composable
private fun Pill(text: String, onClick: (() -> Unit)? = null) {
    val clickable = onClick != null
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = if (clickable) Modifier.clickable { onClick?.invoke() } else Modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun Double.format(d: Int) = "%.${d}f".format(this).replace(',', '.')

private fun Voltage.toReadableLabel(): String {
    val phase = when (this.type) {
        VoltageType.AC_1PHASE -> "1‑фаза"
        VoltageType.AC_3PHASE -> "3‑фазы"
        VoltageType.DC        -> "пост. ток"
    }
    return "${this.value} В, $phase"
}

private fun deviceTypeLabel(type: DeviceType?): String = when (type) {
    DeviceType.LIGHTING        -> "Освещение"
    DeviceType.SOCKET          -> "Розетка"
    DeviceType.HEAVY_DUTY      -> "Мощное устройство"
    DeviceType.AIR_CONDITIONER -> "Кондиционер"
    DeviceType.ELECTRIC_STOVE  -> "Электроплита"
    DeviceType.OVEN            -> "Духовой шкаф"
    DeviceType.WASHING_MACHINE -> "Стиральная машина"
    DeviceType.DISHWASHER      -> "Посудомоечная машина"
    DeviceType.WATER_HEATER    -> "Водонагреватель"
    DeviceType.OTHER, null     -> "Другое"
}

/* --------- Тексты для листа-пояснения --------- */

// было: @Composable
private fun InfoTopic.titleAndText(): Pair<String, String> = when (this) {
    InfoTopic.POWER -> "Мощность (Вт)" to
            "Номинальная потребляемая мощность устройства. Нужна для расчёта нагрузки и выбора автомата/кабеля."
    InfoTopic.POWER_FACTOR -> "cos φ — коэффициент мощности" to
            "Показывает соотношение активной и полной мощности. Чем ближе к 1.00, тем эффективнее устройство и ниже реактивные потери."
    InfoTopic.DEMAND_RATIO -> "Коэффициент спроса" to
            "Доля времени, когда устройство реально нагружает сеть в максимуме. Используется для расчёта суммарной нагрузки (учёт неполной одновременности)."
    InfoTopic.VOLTAGE -> "Питание" to
            "Рабочее напряжение и тип питания: «1‑фаза» — однофазная сеть 220–230 В, «3‑фазы» — трёхфазная 380–400 В, «пост. ток» — питание DC."
}