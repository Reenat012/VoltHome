package ru.mugalimov.volthome.ui.screens.explication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.domain.model.CalculatedValue
import ru.mugalimov.volthome.domain.model.DeviceCalcBreakdown
import ru.mugalimov.volthome.domain.model.DeviceSpecUi
import ru.mugalimov.volthome.ui.screens.explication.sheets.CalcDetailsState
import ru.mugalimov.volthome.ui.utilities.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSpecSheet(
    device: DeviceSpecUi,
    breakdown: DeviceCalcBreakdown?,
    showProfessionalSections: Boolean,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    val context = LocalContext.current
    val safeBreakdown = breakdown?.takeIf { it.deviceId == device.id }

    val detailsState: CalcDetailsState = when {
        safeBreakdown == null -> CalcDetailsState.NONE
        safeBreakdown.calculatedPower.steps.isEmpty() -> CalcDetailsState.NONE
        showProfessionalSections -> CalcDetailsState.AVAILABLE
        else -> CalcDetailsState.LOCKED
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = device.name,
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(4.dp))

            // --- Технические параметры ---
            SpecRow(Icons.Outlined.Power, "Мощность", "${device.power} Вт")
            SpecRow(Icons.Outlined.Bolt, "Напряжение", device.voltage?.let { "$it В" } ?: "—")
            SpecRow(Icons.Outlined.Calculate, "Коэффициент спроса", device.demandRatio?.toString() ?: "—")
            SpecRow(Icons.Outlined.Emergency, "Коэффициент мощности", device.powerFactor?.toString() ?: "—")

            // Пояснения (только когда есть смысл: детали доступны, т.е. мы в PRO)
            if (detailsState == CalcDetailsState.AVAILABLE) {
                Text(
                    text = "Расчётная мощность определяется как Pрасч = Pуст × kспроса.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (device.powerFactor != null) {
                    Text(
                        text = "Коэффициент мощности учитывается при расчёте токов нагрузки.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- Применение в расчёте ---
            safeBreakdown?.let { b ->
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Применение в расчёте",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(6.dp))

                // Итог всегда
                CalculatedValueBlock(b.calculatedPower)

                Spacer(Modifier.height(6.dp))

                when (detailsState) {
                    CalcDetailsState.LOCKED -> {
                        Text(
                            text = "Детали расчёта доступны в PRO.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalcDetailsState.NONE -> {
                        // Важно: это сообщение будет показываться и в Free, и в Pro, если steps реально пустые.
                        // Если ты хочешь в Free при NONE молчать — это уже правило из коммита 2, правится логикой payload/статуса.
                        Text(
                            text = "Детали расчёта для этого устройства не формируются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalcDetailsState.AVAILABLE -> {
                        // Здесь ничего: детали показываются через InfoSheet
                    }

                    CalcDetailsState.HIDDEN -> {
                        // Для DeviceSpecSheet не актуально: "применение в расчёте" просто не показываем.
                    }
                }
            }

            // --- Тип / логика подключения ---
            SpecRow(Icons.Outlined.Devices, "Тип устройства", device.deviceType.label(context))
            SpecRow(Icons.Outlined.Cable, "Выделенная линия", if (device.requiresDedicatedCircuit) "Да" else "Нет")
            SpecRow(
                Icons.Outlined.ElectricalServices,
                "Требует точку подключения/розетку",
                device.requiresSocketConnection?.let { if (it) "Да" else "Нет" } ?: "—"
            )
            SpecRow(Icons.Outlined.Build, "Двигатель", if (device.hasMotor) "Есть" else "Нет")

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SpecRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun CalculatedValueBlock(v: CalculatedValue) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val valueText =
            if (v.unit == "Вт" && v.value >= 1000)
                "%.0f Вт (%.1f кВт)".format(v.value, v.value / 1000.0)
            else
                "%.0f %s".format(v.value, v.unit)

        Text(
            text = "Итог по устройству: $valueText",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}